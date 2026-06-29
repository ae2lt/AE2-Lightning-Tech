package com.moakiee.ae2lt.logic.batch;

import java.util.Iterator;

import appeng.api.stacks.AEKeyType;
import appeng.crafting.inv.ListCraftingInventory;

public interface BatchJobView {
    Iterator<BatchTaskHandle> taskIterator();

    ListCraftingInventory waitingFor();

    void addContainerMaxItems(long count, AEKeyType type);
}
