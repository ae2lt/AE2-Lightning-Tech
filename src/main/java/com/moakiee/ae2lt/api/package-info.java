/**
 * Public, first-party API for AE2 Lightning Tech (mod id {@code ae2lt}).
 *
 * <p>This package, and its sub-packages
 * {@link com.moakiee.ae2lt.api.lightning lightning},
 * {@link com.moakiee.ae2lt.api.event event}, and
 * {@link com.moakiee.ae2lt.api.ids ids}, are the only stable contract for addon
 * authors. Anything else under {@code com.moakiee.ae2lt.*} is internal and may
 * change between minor versions without notice.
 *
 * <h2>Frozen on release</h2>
 * <ul>
 *   <li>All public type signatures in {@code com.moakiee.ae2lt.api.*}</li>
 *   <li>{@link com.moakiee.ae2lt.api.lightning.LightningTier} constants and their
 *       serialized names ({@code "high_voltage"}, {@code "extreme_high_voltage"})</li>
 *   <li>{@link com.moakiee.ae2lt.api.AE2LTCapabilities} {@code ResourceLocation}s</li>
 *   <li>The block entity and recipe IDs in
 *       {@link com.moakiee.ae2lt.api.ids}</li>
 *   <li>The fields and trigger timing of
 *       {@link com.moakiee.ae2lt.api.event.LightningCollectedEvent}</li>
 * </ul>
 *
 * <h2>Constraints on the API package itself</h2>
 * <p>Code under {@code com.moakiee.ae2lt.api.*} only depends on JDK, Minecraft,
 * NeoForge, AE2 public API, and other types from the same {@code api} sub-tree. It
 * does not import any non-api package of this mod, so addons can compile against
 * the API surface without pulling in implementation classes.
 */
package com.moakiee.ae2lt.api;
