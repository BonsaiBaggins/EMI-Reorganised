package com.bonsaibaggins.emireorganised.compat;

import com.bonsaibaggins.emireorganised.port.BannerView;
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

    /**
     * Talks to Simulated through reflection, so building this mod doesn't need Simulated (which only ships inside the
     * Create Aeronautics bundle). Lookups are cached; none of this runs while drawing.
     */
    private static final class Impl {
        private static final java.util.Map<String, java.lang.reflect.Method> METHODS = new java.util.concurrent.ConcurrentHashMap<>();
        @Nullable private static Object manager;

        private static Object manager() throws ReflectiveOperationException {
            if (manager == null) {
                manager = Class.forName("dev.simulated_team.simulated.index.SimResourceManagers").getField("SIMULATED_SECTION").get(null);
            }
            return manager;
        }

        private static java.lang.reflect.Method method(Class<?> type, String name, int args) throws NoSuchMethodException {
            String key = type.getName() + "#" + name + "/" + args;
            java.lang.reflect.Method m = METHODS.get(key);
            if (m == null) {
                for (Class<?> c = type; c != null && m == null; c = c.getSuperclass()) {
                    for (java.lang.reflect.Method candidate : c.getMethods()) {
                        if (candidate.getName().equals(name) && candidate.getParameterCount() == args) {
                            m = candidate;
                            break;
                        }
                    }
                }
                if (m == null) {
                    throw new NoSuchMethodException(key);
                }
                m.setAccessible(true);
                METHODS.put(key, m);
            }
            return m;
        }

        private static Object call(Object target, String name, Object... args) throws ReflectiveOperationException {
            return method(target.getClass(), name, args.length).invoke(target, args);
        }

        private static Object callStatic(String className, String name, Object... args) throws ReflectiveOperationException {
            return method(Class.forName(className), name, args.length).invoke(null, args);
        }

        private static int argb(Object colour) throws ReflectiveOperationException {
            return (Integer) call(colour, "argb");
        }

        private static int darken(int argb) {
            int r = (int) (((argb >> 16) & 0xFF) * 0.8F);
            int g = (int) (((argb >> 8) & 0xFF) * 0.8F);
            int b = (int) ((argb & 0xFF) * 0.8F);
            return (argb & 0xFF000000) | r << 16 | g << 8 | b;
        }

        private static void warn(Exception e) {
            com.bonsaibaggins.emireorganised.EmiReorganised.LOGGER.warn("Simulated integration failed", e);
        }

        static java.util.List<Section> sections() {
            java.util.List<Section> out = new java.util.ArrayList<>();
            try {
                Object m = manager();
                for (Object section : (Iterable<?>) call(m, "sortedEntries")) {
                    Object id = call(m, "getId", section);
                    if (id instanceof ResourceLocation rl) {
                        out.add(convert(rl, section));
                    }
                }
            } catch (Exception e) {
                warn(e);
            }
            return out;
        }

        @Nullable
        private static Object get(ResourceLocation id) throws ReflectiveOperationException {
            return call(manager(), "get", id);
        }

        @Nullable
        static Section section(ResourceLocation id) {
            try {
                Object section = get(id);
                return section == null ? null : convert(id, section);
            } catch (Exception e) {
                warn(e);
                return null;
            }
        }

        private static Section convert(ResourceLocation id, Object section) throws ReflectiveOperationException {
            Object title = call(section, "title");
            int light = argb(call(title, "color"));
            java.util.Optional<?> secondary = (java.util.Optional<?>) call(title, "secondaryColor");
            int dark = secondary.isPresent() ? argb(secondary.get()) : darken(light);
            return new Section(id, (Component) call(title, "text"), light, dark, argb(call(title, "background")),
                    (ResourceLocation) call(section, "sprite"));
        }

        @Nullable
        static ResourceLocation sectionOf(net.minecraft.world.item.Item item) {
            try {
                return (ResourceLocation) callStatic("dev.simulated_team.simulated.registrate.SimulatedRegistrate", "sectionOf", item);
            } catch (Exception e) {
                warn(e);
                return null;
            }
        }

        static void setPlaying(ResourceLocation sprite, boolean playing) {
            try {
                callStatic("dev.simulated_team.simulated.registrate.simulated_tab.SimulatedCreativeTab", "setPlaying", sprite, playing);
            } catch (Exception e) {
                warn(e);
            }
        }

        @Nullable
        static BannerView view(ResourceLocation id, @Nullable String name, @Nullable Path image) {
            try {
                Object section = get(id);
                if (section == null) {
                    return null;
                }
                Section s = convert(id, section);
                boolean animate = (Boolean) call(section, "animateOnHover");
                Component text = name != null && !name.isEmpty() ? Component.literal(name) : s.title();
                return new BannerView(text, s.color(), s.secondaryColor(), s.background(), s.sprite(), image,
                        image != null || animate, BannerView.DEFAULT_LEFT, BannerView.DEFAULT_FILL, null, null);
            } catch (Exception e) {
                warn(e);
                return null;
            }
        }
    }
}
