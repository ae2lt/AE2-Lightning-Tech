package com.moakiee.ae2lt.grid;

import net.minecraft.ChatFormatting;

import javax.annotation.Nonnull;

public enum FrequencyAccessLevel {
    OWNER(ChatFormatting.DARK_PURPLE),
    ADMIN(ChatFormatting.BLUE),
    USER(ChatFormatting.GREEN),
    BLOCKED(ChatFormatting.RED);

    public static final FrequencyAccessLevel[] VALUES = values();

    private final ChatFormatting formatting;

    FrequencyAccessLevel(ChatFormatting formatting) {
        this.formatting = formatting;
    }

    @Nonnull
    public static FrequencyAccessLevel fromId(byte id) {
        if (id < 0 || id >= VALUES.length) return BLOCKED;
        return VALUES[id];
    }

    public byte getId() {
        return (byte) ordinal();
    }

    public ChatFormatting getFormatting() {
        return formatting;
    }

    public boolean canUse() {
        return this != BLOCKED;
    }

    /**
     * True for OWNER or ADMIN — i.e. anyone with authority to manage
     * the frequency (rename it, promote/demote other members, etc).
     * Renamed from the old {@code canEdit} which was ambiguous: "edit"
     * covered both member management and frequency config, but it
     * didn't cover everything an ADMIN could actually do.
     */
    public boolean isManager() {
        return this == OWNER || this == ADMIN;
    }

    /** True only for OWNER. Renamed from {@code canDelete} for clarity
     * since this role gates a lot more than just deletion — security
     * level changes, password changes, promoting / demoting owners,
     * transferring ownership, and destroying the frequency entirely
     * all require it. */
    public boolean isOwner() {
        return this == OWNER;
    }

    /**
     * Rank ordering used by {@link #canActOnLevel}: OWNER > ADMIN >
     * USER > BLOCKED. Separate from {@link #getId} (the NBT value)
     * because that ordering was historically <i>ascending</i> from
     * OWNER=0 to BLOCKED=3, which would flip the comparison.
     */
    public int rank() {
        return switch (this) {
            case OWNER -> 3;
            case ADMIN -> 2;
            case USER -> 1;
            case BLOCKED -> 0;
        };
    }

    /**
     * Shared UI + server rule: {@code actor} may perform an operation
     * that touches a target of {@code level} iff
     * <ol>
     *   <li>they have edit access (USER / BLOCKED are read-only);</li>
     *   <li>the target isn't ranked above them (no acting on superiors);</li>
     *   <li>the target isn't at their own rank — except when the actor
     *       is OWNER, since only OWNERs may demote/remove peer OWNERs.</li>
     * </ol>
     * Call with {@code level = max(currentLevel, newLevel)} to enforce
     * both "can I touch this target" and "am I allowed to create this
     * new rank" in one check — e.g. SET_ADMIN on a USER still requires
     * OWNER because creating a peer-ADMIN exceeds ADMIN's reach.
     * Self-targeting owners are still blocked separately by the
     * self-lock rule in {@link WirelessFrequency#changeMembership}.
     */
    public boolean canActOnLevel(@Nonnull FrequencyAccessLevel level) {
        if (!isManager()) return false;
        int ar = rank();
        int lr = level.rank();
        if (lr > ar) return false;
        if (lr == ar && this != OWNER) return false;
        return true;
    }

    /**
     * Returns whichever of {@code a} and {@code b} has the higher
     * {@link #rank}. Used with {@link #canActOnLevel} when an operation
     * has both a "current target level" and a "new level" and the
     * stricter of the two governs the permission check.
     */
    @Nonnull
    public static FrequencyAccessLevel higher(@Nonnull FrequencyAccessLevel a, @Nonnull FrequencyAccessLevel b) {
        return a.rank() >= b.rank() ? a : b;
    }
}
