package com.moakiee.ae2lt.grid;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;

/**
 * Represents a wireless frequency that pairs controllers and receivers.
 */
public class WirelessFrequency {

    public static final int MAX_NAME_LENGTH = 24;
    public static final int MAX_PASSWORD_LENGTH = 16;

    public static final String TAG_ID = "id";
    public static final String TAG_NAME = "name";
    public static final String TAG_COLOR = "color";
    public static final String TAG_OWNER = "owner";
    public static final String TAG_SECURITY = "security";
    public static final String TAG_PASSWORD = "password";
    public static final String TAG_MEMBERS = "members";

    /** NBT sync types */
    public static final byte NBT_BASIC = 0;
    public static final byte NBT_SAVE_ALL = 1;
    public static final byte NBT_MEMBERS_ONLY = 2;

    private int id;
    private String name;
    private int color;
    private UUID ownerUUID;
    private FrequencySecurityLevel security;
    private String password;
    private final Map<UUID, FrequencyMember> members = new HashMap<>();

    public WirelessFrequency() {
        this(-1, "", 0xFFFFFF, new UUID(0, 0), FrequencySecurityLevel.PUBLIC, "");
    }

    public WirelessFrequency(int id, String name, int color,
                             @Nonnull UUID ownerUUID,
                             @Nonnull FrequencySecurityLevel security,
                             @Nonnull String password) {
        this.id = id;
        this.name = name;
        this.color = color & 0xFFFFFF;
        this.ownerUUID = ownerUUID;
        this.security = security;
        this.password = hashPassword(password, id);
    }

    public WirelessFrequency(int id, String name, int color,
                             @Nonnull Player owner,
                             @Nonnull FrequencySecurityLevel security,
                             @Nonnull String password) {
        this(id, name, color, owner.getUUID(), security, password);
        members.put(ownerUUID, FrequencyMember.create(owner, FrequencyAccessLevel.OWNER));
    }

    /**
     * SHA-256 of {@code id || 0x00 || plaintext}, hex-encoded. The empty
     * string passes through unchanged so {@code isBlank()} checks in the
     * packet handlers keep working to distinguish "no password" from
     * "incoming plaintext to verify". Uses the frequency id as a salt so
     * two frequencies with the same password hash differently, which
     * prevents a leaked save from revealing that two rows share a code.
     */
    @Nonnull
    public static String hashPassword(@Nonnull String plaintext, int salt) {
        if (plaintext.isEmpty()) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Integer.toString(salt).getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            byte[] out = md.digest(plaintext.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(out);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable on this JVM", e);
        }
    }

    /**
     * Returns true only when {@code s} is exactly 64 lowercase-hex
     * characters — the shape produced by {@link #hashPassword}. Used to
     * detect whether a persisted password is already hashed or is a
     * legacy plaintext value that needs migrating on load.
     * MAX_PASSWORD_LENGTH is 16, so no plaintext can collide with this
     * shape.
     */
    private static boolean isHashShape(@Nonnull String s) {
        if (s.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) return false;
        }
        return true;
    }

    // ── Getters / Setters ──

    public int getId() {
        return id;
    }

    @Nonnull
    public String getName() {
        return name;
    }

    public boolean setName(@Nonnull String name) {
        if (!name.equals(this.name) && !name.isBlank() && name.length() <= MAX_NAME_LENGTH) {
            this.name = name;
            return true;
        }
        return false;
    }

    public int getColor() {
        return color;
    }

    public boolean setColor(int color) {
        color &= 0xFFFFFF;
        if (this.color != color) {
            this.color = color;
            return true;
        }
        return false;
    }

    @Nonnull
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    @Nonnull
    public FrequencySecurityLevel getSecurity() {
        return security;
    }

    public boolean setSecurity(@Nonnull FrequencySecurityLevel security) {
        if (this.security != security) {
            this.security = security;
            return true;
        }
        return false;
    }

    @Nonnull
    public String getPassword() {
        return password;
    }

    /**
     * Stores the SHA-256 hash of {@code plaintext} (salted by the
     * frequency's id), or the empty string when {@code plaintext} is
     * empty. Callers from the packet handlers are expected to pass
     * PLAINTEXT here — they must never forward an already-hashed value,
     * or the second hash pass would break verification.
     */
    public void setPassword(@Nonnull String password) {
        this.password = hashPassword(password, this.id);
    }

    // ── Members ──

    @Nonnull
    public FrequencyAccessLevel getPlayerAccess(@Nonnull Player player) {
        UUID uuid = player.getUUID();
        FrequencyMember member = members.get(uuid);
        if (member != null) {
            return member.getAccessLevel();
        }
        return security == FrequencySecurityLevel.PUBLIC ? FrequencyAccessLevel.USER : FrequencyAccessLevel.BLOCKED;
    }

    public boolean canPlayerAccess(@Nonnull Player player, @Nonnull String password) {
        FrequencyAccessLevel access = getPlayerAccess(player);
        if (access.canUse()) {
            return true;
        }
        // Encrypted frequencies allow temporary access with the correct password.
        if (security == FrequencySecurityLevel.ENCRYPTED && !password.isEmpty()) {
            return hashPassword(password, this.id).equals(this.password);
        }
        return false;
    }

    public boolean isMember(@Nonnull Player player) {
        return members.containsKey(player.getUUID());
    }

    /**
     * Persists a previously unknown player as a {@link FrequencyAccessLevel#USER}
     * member. Used by the password-unlock path in
     * {@link com.moakiee.ae2lt.network.SelectFrequencyPacket} so a
     * stranger who typed the correct password becomes a permanent USER
     * without needing an OWNER to hand-promote them afterwards.
     * Returns {@code true} when a new entry was added, {@code false} if
     * the player was already a member (in which case nothing is
     * mutated and the caller can skip the sync broadcast).
     */
    public boolean enrollAsUser(@Nonnull Player player) {
        UUID uuid = player.getUUID();
        if (members.containsKey(uuid)) return false;
        members.put(uuid, FrequencyMember.create(player, FrequencyAccessLevel.USER));
        return true;
    }

    public int changeMembership(@Nonnull Player actor, @Nonnull UUID targetUUID, byte type) {
        FrequencyAccessLevel actorAccess = getPlayerAccess(actor);
        FrequencyMember current = members.get(targetUUID);
        FrequencyAccessLevel targetLevel = current == null
                ? FrequencyAccessLevel.BLOCKED
                : current.getAccessLevel();
        boolean targetIsOwner = targetLevel == FrequencyAccessLevel.OWNER;
        boolean self = actor.getUUID().equals(targetUUID);

        switch (type) {
            case MEMBERSHIP_SET_USER -> {
                // Permission = canActOnLevel(max(current, USER)).
                // USER is the lowest rank so max degenerates to the
                // current target rank; this makes demoting an OWNER
                // require OWNER (same-rank exception), demoting an
                // ADMIN require OWNER, and promoting a stranger /
                // re-tagging a USER only require ADMIN.
                FrequencyAccessLevel effective = FrequencyAccessLevel.higher(
                        targetLevel, FrequencyAccessLevel.USER);
                if (!actorAccess.canActOnLevel(effective)) return RESPONSE_NO_PERMISSION;
                if (targetIsOwner && self) return RESPONSE_INVALID_USER;
                if (current == null) {
                    var server = actor.level().getServer();
                    if (server == null) return RESPONSE_INVALID_USER;
                    Player target = server.getPlayerList().getPlayer(targetUUID);
                    if (target == null) return RESPONSE_INVALID_USER;
                    members.put(targetUUID, FrequencyMember.create(target, FrequencyAccessLevel.USER));
                    return RESPONSE_SUCCESS;
                }
                return current.setAccessLevel(FrequencyAccessLevel.USER) ? RESPONSE_SUCCESS : RESPONSE_INVALID_USER;
            }
            case MEMBERSHIP_SET_ADMIN -> {
                // Permission = canActOnLevel(max(current, ADMIN)).
                // Creating an ADMIN always touches rank ADMIN, so an
                // ADMIN actor can't make peer admins even from a USER
                // target (same-rank rule).
                FrequencyAccessLevel effective = FrequencyAccessLevel.higher(
                        targetLevel, FrequencyAccessLevel.ADMIN);
                if (!actorAccess.canActOnLevel(effective)) return RESPONSE_NO_PERMISSION;
                if (targetIsOwner && self) return RESPONSE_INVALID_USER;
                if (current == null) {
                    var server = actor.level().getServer();
                    if (server == null) return RESPONSE_INVALID_USER;
                    Player target = server.getPlayerList().getPlayer(targetUUID);
                    if (target == null) return RESPONSE_INVALID_USER;
                    members.put(targetUUID, FrequencyMember.create(target, FrequencyAccessLevel.ADMIN));
                    return RESPONSE_SUCCESS;
                }
                return current.setAccessLevel(FrequencyAccessLevel.ADMIN) ? RESPONSE_SUCCESS : RESPONSE_INVALID_USER;
            }
            case MEMBERSHIP_CANCEL -> {
                if (current == null) return RESPONSE_INVALID_USER;
                if (self) {
                    // Self-leave path: any non-OWNER can remove
                    // themselves from a frequency they joined. OWNER
                    // self is still self-locked (preserves the "at
                    // least one OWNER remains" invariant).
                    if (targetIsOwner) return RESPONSE_INVALID_USER;
                } else {
                    if (!actorAccess.canActOnLevel(targetLevel)) return RESPONSE_NO_PERMISSION;
                }
                members.remove(targetUUID);
                return RESPONSE_SUCCESS;
            }
            case MEMBERSHIP_TRANSFER_OWNERSHIP -> {
                // Multi-owner: this operation now PROMOTES the target to
                // OWNER without demoting existing owners. Requires
                // OWNER-on-OWNER reach (max rank is always OWNER here).
                if (!actorAccess.canActOnLevel(FrequencyAccessLevel.OWNER)) return RESPONSE_NO_PERMISSION;
                if (targetIsOwner) return RESPONSE_INVALID_USER;
                if (current != null) {
                    current.setAccessLevel(FrequencyAccessLevel.OWNER);
                } else {
                    var server = actor.level().getServer();
                    if (server == null) return RESPONSE_INVALID_USER;
                    Player target = server.getPlayerList().getPlayer(targetUUID);
                    if (target == null) return RESPONSE_INVALID_USER;
                    members.put(targetUUID, FrequencyMember.create(target, FrequencyAccessLevel.OWNER));
                }
                return RESPONSE_SUCCESS;
            }
        }
        return RESPONSE_INVALID_USER;
    }

    // ── Membership operation constants ──

    public static final byte MEMBERSHIP_SET_USER = 0;
    public static final byte MEMBERSHIP_SET_ADMIN = 1;
    public static final byte MEMBERSHIP_CANCEL = 2;
    public static final byte MEMBERSHIP_TRANSFER_OWNERSHIP = 3;

    // ── Response codes ──

    public static final int RESPONSE_SUCCESS = 0;
    public static final int RESPONSE_NO_PERMISSION = 1;
    public static final int RESPONSE_INVALID_USER = 2;

    // ── NBT ──

    public void writeToTag(@Nonnull CompoundTag tag, byte type) {
        if (type == NBT_BASIC || type == NBT_SAVE_ALL) {
            tag.putInt(TAG_ID, id);
            tag.putString(TAG_NAME, name);
            tag.putInt(TAG_COLOR, color);
            tag.putUUID(TAG_OWNER, ownerUUID);
            tag.putByte(TAG_SECURITY, security.getId());
        }
        if (type == NBT_SAVE_ALL) {
            tag.putString(TAG_PASSWORD, password);
            if (!members.isEmpty()) {
                ListTag list = new ListTag();
                for (FrequencyMember m : members.values()) {
                    CompoundTag sub = new CompoundTag();
                    m.writeNBT(sub);
                    list.add(sub);
                }
                tag.put(TAG_MEMBERS, list);
            }
        }
        if (type == NBT_MEMBERS_ONLY) {
            tag.putInt(TAG_ID, id);
            ListTag list = new ListTag();
            for (FrequencyMember m : members.values()) {
                CompoundTag sub = new CompoundTag();
                m.writeNBT(sub);
                list.add(sub);
            }
            tag.put(TAG_MEMBERS, list);
        }
    }

    public void readFromTag(@Nonnull CompoundTag tag, byte type) {
        if (type == NBT_BASIC || type == NBT_SAVE_ALL) {
            id = tag.getInt(TAG_ID);
            name = tag.getString(TAG_NAME);
            color = tag.getInt(TAG_COLOR);
            ownerUUID = tag.getUUID(TAG_OWNER);
            security = FrequencySecurityLevel.fromId(tag.getByte(TAG_SECURITY));
        }
        if (type == NBT_SAVE_ALL) {
            // Auto-migrate legacy plaintext saves: pre-hash worlds stored
            // the plaintext password directly. A SHA-256 hex digest is
            // always 64 lowercase-hex chars, and MAX_PASSWORD_LENGTH is
            // 16, so anything that doesn't match {@link #isHashShape}
            // must be legacy plaintext — we re-hash it on load so old
            // worlds keep working without a manual password reset.
            String stored = tag.getString(TAG_PASSWORD);
            password = (stored.isEmpty() || isHashShape(stored))
                    ? stored
                    : hashPassword(stored, id);
            members.clear();
            ListTag list = tag.getList(TAG_MEMBERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                FrequencyMember m = new FrequencyMember(list.getCompound(i));
                members.put(m.getPlayerUUID(), m);
            }
        }
        if (type == NBT_MEMBERS_ONLY) {
            members.clear();
            ListTag list = tag.getList(TAG_MEMBERS, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                FrequencyMember m = new FrequencyMember(list.getCompound(i));
                members.put(m.getPlayerUUID(), m);
            }
        }
    }

    @Override
    public String toString() {
        return "WirelessFrequency{id=" + id + ", name='" + name + "', owner=" + ownerUUID + '}';
    }
}
