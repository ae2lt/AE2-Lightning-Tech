/*
 * Derived from Applied Energistics 2's SetProcessingPatternAmountScreen.
 * Copyright (c) 2013-2014 AlgorithmX2 and Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.moakiee.ae2lt.client;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.AESubScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.me.common.ClientDisplaySlot;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.client.gui.widgets.TabButton;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import com.google.common.primitives.Longs;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import java.util.function.Consumer;

final class TianshuSetProcessingPatternAmountScreen<M extends TianshuPatternEncodingTermMenu>
        extends AESubScreen<M, TianshuPatternEncodingTermScreen<M>> {
    private final NumberEntryWidget amount;
    private final GenericStack currentStack;
    private final Consumer<GenericStack> setter;

    TianshuSetProcessingPatternAmountScreen(
            TianshuPatternEncodingTermScreen<M> parentScreen,
            GenericStack currentStack,
            Consumer<GenericStack> setter) {
        super(parentScreen, "/screens/set_processing_pattern_amount.json");
        this.currentStack = currentStack;
        this.setter = setter;

        widgets.addButton("save", GuiText.Set.text(), this::confirm);

        var icon = getMenu().getHost().getMainMenuIcon();
        var button = new TabButton(Icon.BACK, icon.getHoverName(), ignored -> returnToParent());
        widgets.add("back", button);

        amount = widgets.addNumberEntryWidget("amountToStock", NumberEntryType.of(currentStack.what()));
        amount.setLongValue(currentStack.amount());
        amount.setMaxValue(getMaxAmount());
        amount.setTextFieldStyle(style.getWidget("amountToStockInput"));
        amount.setMinValue(0);
        amount.setHideValidationIcon(true);
        amount.setOnConfirm(this::confirm);

        addClientSideSlot(new ClientDisplaySlot(currentStack), SlotSemantics.MACHINE_OUTPUT);
    }

    @Override
    protected void init() {
        super.init();
        setSlotsHidden(SlotSemantics.TOOLBOX, true);
    }

    private void confirm() {
        amount.getLongValue().ifPresent(newAmount -> {
            var constrainedAmount = Longs.constrainToRange(newAmount, 0, getMaxAmount());
            setter.accept(constrainedAmount <= 0
                    ? null
                    : new GenericStack(currentStack.what(), constrainedAmount));
            returnToParent();
        });
    }

    private long getMaxAmount() {
        return 999999L * currentStack.what().getAmountPerUnit();
    }
}
