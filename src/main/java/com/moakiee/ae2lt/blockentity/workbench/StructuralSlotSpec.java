package com.moakiee.ae2lt.blockentity.workbench;

import appeng.menu.SlotSemantic;

import com.moakiee.ae2lt.device.DeviceSlotType;

public record StructuralSlotSpec(int index, DeviceSlotType slotType, SlotSemantic semantic) {
}
