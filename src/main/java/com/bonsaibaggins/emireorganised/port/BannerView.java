package com.bonsaibaggins.emireorganised.port;

import com.bonsaibaggins.emireorganised.data.BannerDef;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Everything needed to draw one banner. The background is a custom image if set, else an atlas sprite (Simulated
 * artwork), else a horizontal gradient.
 */
public record BannerView(Component title, int color, int secondaryColor, int titleBackground,
                         @Nullable ResourceLocation sprite, @Nullable Path image, boolean animateOnHover,
                         int fillLeft, int fillRight, @Nullable Integer fillMid1, @Nullable Integer fillMid2) {
    /** Default gradient: dark grey to light grey. */
    public static final int DEFAULT_LEFT = 0xFF3C3C3C;
    public static final int DEFAULT_FILL = 0xFFC6C6C6;
    public static final int DEFAULT_TITLE_BACKGROUND = 0xAA000000;

    public static BannerView custom(String name, @Nullable Path image) {
        return new BannerView(Component.literal(name), 0xFFFFFFFF, 0xFFCCCCCC, DEFAULT_TITLE_BACKGROUND, null, image, true,
                DEFAULT_LEFT, DEFAULT_FILL, null, null);
    }

    /** The four dither colours: left, two intermediates (automatic unless set) and right. */
    public int[] fillColours() {
        return DitherGradient.colours(fillLeft, fillMid1, fillMid2, fillRight);
    }

    /** The view for a banner definition: Simulated section artwork when linked and installed, then user colours. */
    public static BannerView of(BannerDef banner, @Nullable Path image) {
        BannerView base = banner.section != null ? com.bonsaibaggins.emireorganised.compat.SimulatedCompat.view(banner.section, banner.name, image) : null;
        return (base != null ? base : custom(banner.name, image)).with(banner.colors);
    }

    /** Applies user-chosen colours; a set fill gradient replaces the section artwork when there's no custom image. */
    public BannerView with(BannerDef.Colors colors) {
        if (colors == null || colors.equals(BannerDef.Colors.NONE)) {
            return this;
        }
        boolean fill = colors.left() != null || colors.right() != null || colors.mid1() != null || colors.mid2() != null;
        int left = colors.left() != null ? colors.left() : fill && colors.right() != null ? colors.right() : fillLeft;
        int right = colors.right() != null ? colors.right() : left;
        return new BannerView(title, colors.title() != null ? colors.title() : color,
                colors.outline() != null ? colors.outline() : secondaryColor,
                colors.titleBackground() != null ? colors.titleBackground() : titleBackground,
                fill && image == null ? null : sprite, image, animateOnHover, left, right,
                colors.mid1() != null ? colors.mid1() : fillMid1, colors.mid2() != null ? colors.mid2() : fillMid2);
    }

    /** Identity used to share hover animation state between every banner drawing the same background. */
    public Object animationKey() {
        return image != null ? image : sprite;
    }
}
