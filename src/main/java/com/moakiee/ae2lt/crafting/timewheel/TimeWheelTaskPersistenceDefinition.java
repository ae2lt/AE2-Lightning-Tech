package com.moakiee.ae2lt.crafting.timewheel;

import appeng.api.stacks.AEItemKey;

/**
 * Lets an execution-only pattern wrapper provide a decodable definition for time-wheel job saves
 * without changing the definition used to find and invoke its real crafting provider.
 */
public interface TimeWheelTaskPersistenceDefinition {
    AEItemKey timeWheelPersistenceDefinition();
}
