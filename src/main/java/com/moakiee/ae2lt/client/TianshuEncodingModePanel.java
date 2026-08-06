/*
 * Derived from Applied Energistics 2's encoding mode panels.
 * Copyright (c) Applied Energistics 2 contributors.
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */

package com.moakiee.ae2lt.client;

import appeng.client.Point;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.Icon;
import appeng.client.gui.WidgetContainer;
import com.moakiee.ae2lt.menu.TianshuPatternEncodingTermMenu;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;

abstract class TianshuEncodingModePanel implements ICompositeWidget {
    protected final TianshuPatternEncodingTermScreen<?> screen;
    protected final TianshuPatternEncodingTermMenu menu;
    protected final WidgetContainer widgets;
    protected boolean visible;
    protected int x;
    protected int y;

    TianshuEncodingModePanel(
            TianshuPatternEncodingTermScreen<?> screen,
            WidgetContainer widgets) {
        this.screen = screen;
        this.menu = screen.getMenu();
        this.widgets = widgets;
    }

    abstract Icon getIcon();

    abstract Component getTabTooltip();

    @Override
    public void setPosition(Point position) {
        x = position.getX();
        y = position.getY();
    }

    @Override
    public void setSize(int width, int height) {
    }

    @Override
    public Rect2i getBounds() {
        return new Rect2i(x, y, 124, 66);
    }

    @Override
    public final boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }
}
