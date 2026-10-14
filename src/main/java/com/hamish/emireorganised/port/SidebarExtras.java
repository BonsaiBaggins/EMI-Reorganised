package com.hamish.emireorganised.port;

import dev.emi.emi.api.widget.Bounds;

/** Size of our vertical tab bar next to the index sidebar, for rollout placement (filled in with the tab bar). */
public final class SidebarExtras {
    private SidebarExtras() {
    }

    public static int tabBarWidth() {
        return 0;
    }

    public static Bounds withTabBar(Bounds bounds) {
        return bounds;
    }
}
