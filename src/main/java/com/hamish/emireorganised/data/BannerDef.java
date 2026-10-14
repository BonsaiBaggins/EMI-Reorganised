package com.hamish.emireorganised.data;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** A banner placed in a tab. {@code section} links it to a Simulated section for its artwork. */
public final class BannerDef {
    public String id;
    public String name;
    @Nullable public ResourceLocation section;
    @Nullable public String image;
    public Colors colors = Colors.NONE;

    public BannerDef(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public BannerDef copy(String newId) {
        BannerDef copy = new BannerDef(newId, name);
        copy.section = section;
        copy.image = image;
        copy.colors = colors;
        return copy;
    }

    /** ARGB colours; null means the default. */
    public record Colors(@Nullable Integer title, @Nullable Integer outline, @Nullable Integer titleBackground,
                         @Nullable Integer left, @Nullable Integer right) {
        public static final Colors NONE = new Colors(null, null, null, null, null);
    }
}
