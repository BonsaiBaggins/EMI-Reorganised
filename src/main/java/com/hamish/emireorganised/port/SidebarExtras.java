package com.hamish.emireorganised.port;

import com.hamish.emireorganised.sidebar.VerticalTabs;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.config.SidebarType;
import dev.emi.emi.screen.EmiScreenManager;

/** Size of our vertical tab column next to the index sidebar, for rollout placement. */
public final class SidebarExtras {
    private SidebarExtras() {
    }

    public static int tabBarWidth() {
        return VerticalTabs.width();
    }

    /** Widens a sidebar's bounds to include the tab column when it is the index sidebar. */
    public static Bounds withTabBar(Bounds bounds) {
        int w = VerticalTabs.width();
        EmiScreenManager.SidebarPanel panel = EmiScreenManager.getPanelFor(SidebarType.INDEX);
        if (w <= 0 || panel == null || !panel.isVisible() || !bounds.equals(panel.getBounds())) {
            return bounds;
        }
        if (com.hamish.emireorganised.Config.get(com.hamish.emireorganised.Config.TAB_SIDE) == com.hamish.emireorganised.Config.TabSide.LEFT) {
            return new Bounds(bounds.x() - w, bounds.y(), bounds.width() + w, bounds.height());
        }
        return new Bounds(bounds.x(), bounds.y(), bounds.width() + w, bounds.height());
    }
}
