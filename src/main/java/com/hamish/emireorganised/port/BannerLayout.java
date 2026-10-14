package com.hamish.emireorganised.port;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.screen.EmiScreenManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A sidebar page list with banner rows: each banner starts a new row and fills it with empty spacer slots, so the
 * banner can be drawn over that row. {@code sequence} holds {@link EmiIngredient}s and {@link BannerView}s.
 */
public final class BannerLayout {
    public record Banner(int index, BannerView view) {
    }

    public record Key(List<?> sequence, int sequenceSize, int tw, int th, int[] widths) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && k.sequence == sequence && k.sequenceSize == sequenceSize && k.tw == tw && k.th == th
                    && Arrays.equals(k.widths, widths);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(sequence) * 31 + sequenceSize * 7 + tw * 3 + th + Arrays.hashCode(widths);
        }
    }

    public static final BannerLayout NONE = new BannerLayout(null, List.of(), List.of());

    public final Key key;
    public final List<? extends EmiIngredient> stacks;
    public final List<Banner> banners;

    private BannerLayout(Key key, List<? extends EmiIngredient> stacks, List<Banner> banners) {
        this.key = key;
        this.stacks = stacks;
        this.banners = banners;
    }

    public static Key keyFor(EmiScreenManager.ScreenSpace space, List<?> sequence) {
        int[] widths = new int[Math.max(1, space.th)];
        for (int row = 0; row < widths.length; row++) {
            widths[row] = space.getWidth(row);
        }
        return new Key(sequence, sequence.size(), space.tw, space.th, widths);
    }

    public static BannerLayout build(Key key, List<?> sequence) {
        List<EmiIngredient> out = new ArrayList<>();
        List<Banner> banners = new ArrayList<>();
        int rows = key.widths().length;
        int row = 0;
        int col = 0;
        for (Object entry : sequence) {
            int width = Math.max(1, key.widths()[row % rows]);
            if (entry instanceof BannerView view) {
                if (col > 0) {
                    for (; col < width; col++) {
                        out.add(EmiStack.EMPTY);
                    }
                    row++;
                    col = 0;
                    width = Math.max(1, key.widths()[row % rows]);
                }
                banners.add(new Banner(out.size(), view));
                for (int i = 0; i < width; i++) {
                    out.add(EmiStack.EMPTY);
                }
                row++;
            } else if (entry instanceof EmiIngredient ingredient) {
                out.add(ingredient);
                if (++col >= width) {
                    row++;
                    col = 0;
                }
            }
        }
        return new BannerLayout(key, out, banners);
    }
}
