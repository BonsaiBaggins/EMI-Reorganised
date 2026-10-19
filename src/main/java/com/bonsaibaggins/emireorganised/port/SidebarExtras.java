package com.bonsaibaggins.emireorganised.port;

import com.bonsaibaggins.emireorganised.sidebar.VerticalTabs;
import dev.emi.emi.api.widget.Bounds;
import dev.emi.emi.screen.EmiScreenManager;

/** Size of our vertical tab columns next to EMI's sidebars, for rollout placement. */
public final class SidebarExtras {
    private SidebarExtras() {
    }

    public static int tabBarWidth() {
        return VerticalTabs.maxWidth();
    }

    /** Widens a sidebar's bounds to include its tab column, if it has one. */
    public static Bounds withTabBar(EmiScreenManager.SidebarPanel panel, Bounds bounds) {
        return VerticalTabs.withColumn(panel, bounds);
    }
}
