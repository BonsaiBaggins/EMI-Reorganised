package com.bonsaibaggins.emireorganised.index;

import java.util.HashSet;
import java.util.Set;

/** Which item groups are open in EMI's index. Groups start closed and stay as left until the game closes. */
public final class GroupState {
    private static final Set<String> EXPANDED = new HashSet<>();
    private static int version;

    private GroupState() {
    }

    public static String key(String tabId, String groupId) {
        return tabId + "/" + groupId;
    }

    public static boolean isExpanded(String key) {
        return EXPANDED.contains(key);
    }

    public static void toggle(String key) {
        if (!EXPANDED.remove(key)) {
            EXPANDED.add(key);
        }
        version++;
    }

    public static int version() {
        return version;
    }
}
