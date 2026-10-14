package com.hamish.emireorganised.data;

import org.jetbrains.annotations.Nullable;

/** Which of our tabs the EMI index shows; null means the full index. */
public final class TabState {
    @Nullable private static String selected;
    private static int version;

    private TabState() {
    }

    @Nullable
    public static String selected() {
        return selected;
    }

    public static void select(@Nullable String id) {
        selected = id;
        version++;
    }

    public static int version() {
        return version;
    }
}
