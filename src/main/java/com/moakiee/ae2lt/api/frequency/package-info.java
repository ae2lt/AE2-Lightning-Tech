/**
 * Public API for the AE2LT wireless frequency system.
 *
 * <p>Frozen on release: all public type signatures in this package,
 * {@link com.moakiee.ae2lt.api.frequency.FrequencySecurity} constant order and
 * names, and the NBT key ({@code "FrequencyId"}) used by
 * {@link com.moakiee.ae2lt.api.frequency.FrequencyBindingAccess#save} /
 * {@link com.moakiee.ae2lt.api.frequency.FrequencyBindingAccess#load}.
 *
 * <p>Like the rest of {@code com.moakiee.ae2lt.api.*}, this package only
 * depends on JDK, Minecraft, Forge, AE2 public API, and other types from the
 * same {@code api} sub-tree. It does not import any non-{@code api} classes of
 * this mod, so addons can compile against the API surface alone.
 *
 * <h2>Read-only queries</h2>
 * <pre>
 * OptionalInt id = FrequencyApi.getBoundFrequencyId(level.getBlockEntity(pos));
 * id.ifPresent(freqId -&gt; {
 *     FrequencyApi.getFrequencyInfo(server, freqId).ifPresent(info -&gt; ...);
 *     FrequencyApi.getTransmitter(server, freqId).ifPresent(tx -&gt; ...);
 * });
 * </pre>
 *
 * <h2>Integrating a third-party block entity as a receiver</h2>
 * <pre>
 * public class MyMachineBE extends AENetworkBlockEntity implements FrequencyBindingHost {
 *     private final FrequencyBindingAccess binding = FrequencyApi.createBinding(this);
 *
 *     public void serverTick() { binding.serverTick(); }
 *
 *     &#64;Override public void onReady()      { super.onReady();      binding.onReady(); }
 *     &#64;Override public void setRemoved()   { super.setRemoved();   binding.setRemoved(); }
 *     &#64;Override public void clearRemoved() { super.clearRemoved(); binding.clearRemoved(); }
 *
 *     &#64;Override public void saveAdditional(CompoundTag tag) {
 *         super.saveAdditional(tag);
 *         binding.save(tag);
 *     }
 *
 *     &#64;Override public void loadTag(CompoundTag tag) {
 *         super.loadTag(tag);
 *         binding.load(tag);
 *     }
 *
 *     &#64;Override public BlockEntity getFrequencyBindingBlockEntity()       { return this; }
 *     &#64;Override public IManagedGridNode getFrequencyBindingMainNode()     { return getMainNode(); }
 *     &#64;Override public FrequencyBindingAccess getFrequencyBindingAccess() { return binding; }
 *     &#64;Override public void saveFrequencyBindingChanges()                 { saveChanges(); }
 *     &#64;Override public void markFrequencyBindingForUpdate()               { markForUpdate(); }
 * }
 * </pre>
 *
 * <h2>Opening the binding UI from a custom screen</h2>
 * <pre>
 * public class MyMachineMenu extends AbstractContainerMenu implements FrequencyBindingMenuHost {
 *     &#64;Override public BlockPos getFrequencyBindingBlockPos() { return host.getBlockPos(); }
 * }
 *
 * // inside MyMachineScreen:
 * frequencyBindButton.onPress(b -&gt; FrequencyApi.openBindingScreen(this.menu));
 * </pre>
 */
package com.moakiee.ae2lt.api.frequency;
