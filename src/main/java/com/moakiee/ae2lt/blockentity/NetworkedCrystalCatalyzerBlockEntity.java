package com.moakiee.ae2lt.blockentity;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.moakiee.ae2lt.grid.FrequencyBindingHelper;
import com.moakiee.ae2lt.grid.FrequencyBindingHost;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Frequency binding belongs exclusively to the normal crystal catalyzer. */
public final class NetworkedCrystalCatalyzerBlockEntity extends CrystalCatalyzerBlockEntity
        implements FrequencyBindingHost {
    public NetworkedCrystalCatalyzerBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public FrequencyBindingHelper getFrequencyBinding() {
        return frequencyBinding;
    }

    @Override
    public AENetworkedBlockEntity getFrequencyBindingBlockEntity() {
        return this;
    }

    @Override
    public void saveFrequencyBindingChanges() {
        saveChanges();
    }

    @Override
    public void markFrequencyBindingForUpdate() {
        markForUpdate();
    }
}
