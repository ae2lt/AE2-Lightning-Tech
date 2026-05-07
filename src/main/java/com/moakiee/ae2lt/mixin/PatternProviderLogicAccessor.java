package com.moakiee.ae2lt.mixin;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.core.Direction;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import appeng.util.inv.AppEngInternalInventory;

/**
 * Mixin accessor exposing private members of {@code PatternProviderLogic}
 * needed by the wireless dispatch path in {@code OverloadedPatternProviderLogic}.
 */
@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicAccessor {

    @Invoker("onPushPatternSuccess")
    void invokeOnPushPatternSuccess(IPatternDetails pattern);

    @Invoker("doWork")
    boolean invokeDoWork();

    @Invoker("hasWorkToDo")
    boolean invokeHasWorkToDo();

    @Accessor("mainNode")
    IManagedGridNode getMainNode();

    @Accessor("patternInventory")
    AppEngInternalInventory getPatternInventory();

    @Mutable
    @Accessor("patternInventory")
    void setPatternInventory(AppEngInternalInventory inv);

    @Mutable
    @Accessor("returnInv")
    void setReturnInv(PatternProviderReturnInventory inv);

    @Accessor("patterns")
    List<IPatternDetails> getPatterns();

    /** The union of all possible pattern inputs (keys with secondary dropped). */
    @Accessor("patternInputs")
    Set<AEKey> getPatternInputs();

    @Accessor("sendList")
    List<GenericStack> getSendList();

    @Accessor("sendDirection")
    Direction getSendDirection();

    @Accessor("sendDirection")
    void setSendDirection(Direction direction);

    @Accessor("unlockStack")
    void setUnlockStack(GenericStack unlockStack);

    @Invoker("addToSendList")
    void invokeAddToSendList(AEKey what, long amount);

    @Invoker("sendStacksOut")
    boolean invokeSendStacksOut();

}
