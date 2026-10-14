package com.hamish.emireorganised.compat;

import com.hamish.emireorganised.port.BannerView;
import java.nio.file.Path;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Optional Simulated integration: section artwork and titles for banners linked to a Simulated section. */
public final class SimulatedCompat {
    private static Boolean loaded;

    private SimulatedCompat() {
    }

    public static boolean isLoaded() {
        if (loaded == null) {
            loaded = ModList.get() != null && ModList.get().isLoaded("simulated");
        }
        return loaded;
    }

    @Nullable
    public static BannerView view(ResourceLocation section, @Nullable String name, @Nullable Path image) {
        return isLoaded() ? Impl.view(section, name, image) : null;
    }

    /** Plays or pauses a Simulated banner sprite's animation (no-op without Simulated). */
    public static void setPlaying(@Nullable ResourceLocation sprite, boolean playing) {
        if (sprite != null && isLoaded()) {
            Impl.setPlaying(sprite, playing);
        }
    }

    /** The Simulated section an item belongs to, or null. */
    @Nullable
    public static ResourceLocation sectionOf(net.minecraft.world.item.Item item) {
        return isLoaded() ? Impl.sectionOf(item) : null;
    }

    /** A Simulated section's banner: title, colours and the GUI sprite holding its (possibly animated) artwork. */
    public record Section(ResourceLocation id, Component title, int color, int secondaryColor, int background, ResourceLocation sprite) {
    }

    /** Every loaded section (Simulated's own and those added by addons such as Aeronautics), in Simulated's order. */
    public static java.util.List<Section> sections() {
        return isLoaded() ? Impl.sections() : java.util.List.of();
    }

    @Nullable
    public static Section section(ResourceLocation id) {
        return isLoaded() ? Impl.section(id) : null;
    }

    private static final class Impl {
        static java.util.List<Section> sections() {
            java.util.List<Section> out = new java.util.ArrayList<>();
            var manager = dev.simulated_team.simulated.index.SimResourceManagers.SIMULATED_SECTION;
            for (var section : manager.sortedEntries()) {
                ResourceLocation id = manager.getId(section);
                if (id != null) {
                    out.add(convert(id, section));
                }
            }
            return out;
        }

        @Nullable
        static Section section(ResourceLocation id) {
            var section = dev.simulated_team.simulated.index.SimResourceManagers.SIMULATED_SECTION.get(id);
            return section == null ? null : convert(id, section);
        }

        private static Section convert(ResourceLocation id, dev.simulated_team.simulated.client.sections.SimulatedSection section) {
            var title = section.title();
            int light = title.color().argb();
            int dark = title.secondaryColor().orElse(title.color().darken(0.2F, new foundry.veil.api.client.color.Color())).argb();
            return new Section(id, title.text(), light, dark, title.background().argb(), section.sprite());
        }

        @Nullable
        static ResourceLocation sectionOf(net.minecraft.world.item.Item item) {
            return dev.simulated_team.simulated.registrate.SimulatedRegistrate.sectionOf(item);
        }

        static void setPlaying(ResourceLocation sprite, boolean playing) {
            dev.simulated_team.simulated.registrate.simulated_tab.SimulatedCreativeTab.setPlaying(sprite, playing);
        }

        @Nullable
        static BannerView view(ResourceLocation id, @Nullable String name, @Nullable Path image) {
            var section = dev.simulated_team.simulated.index.SimResourceManagers.SIMULATED_SECTION.get(id);
            if (section == null) {
                return null;
            }
            var title = section.title();
            int light = title.color().argb();
            int dark = title.secondaryColor().orElse(title.color().darken(0.2F, new foundry.veil.api.client.color.Color())).argb();
            Component text = name != null && !name.isEmpty() ? Component.literal(name) : title.text();
            return new BannerView(text, light, dark, title.background().argb(), section.sprite(), image,
                    image != null || section.animateOnHover(), BannerView.DEFAULT_FILL, BannerView.DEFAULT_FILL);
        }
    }
}
