package com.bonsaibaggins.emireorganised.config;

import com.bonsaibaggins.emireorganised.Config;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Config screen built with Yet Another Config Lib; only loaded when YACL is installed. */
public final class YaclConfigScreen {
    private YaclConfigScreen() {
    }

    public static Screen create(Screen parent) {
        YetAnotherConfigLib.Builder builder = YetAnotherConfigLib.createBuilder()
                .title(Component.translatable("emi_reorganised.config.title"))
                .save(Config.SPEC::save);
        for (Map.Entry<String, List<ModConfigSpec.ConfigValue<?>>> section : sections().entrySet()) {
            ConfigCategory.Builder category = ConfigCategory.createBuilder().name(name(section.getKey()));
            for (ModConfigSpec.ConfigValue<?> value : section.getValue()) {
                Option<?> option = option(value.getPath().get(value.getPath().size() - 1), value);
                if (option != null) {
                    category.option(option);
                }
            }
            builder.category(category.build());
        }
        return builder.build().generateScreen(parent);
    }

    /**
     * Options grouped by section, both in the order {@link Config} declares them. The spec's own maps don't keep that
     * order.
     */
    private static Map<String, List<ModConfigSpec.ConfigValue<?>>> sections() {
        Map<String, List<ModConfigSpec.ConfigValue<?>>> sections = new LinkedHashMap<>();
        for (Field field : Config.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !ModConfigSpec.ConfigValue.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                ModConfigSpec.ConfigValue<?> value = (ModConfigSpec.ConfigValue<?>) field.get(null);
                sections.computeIfAbsent(value.getPath().get(0), k -> new ArrayList<>()).add(value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        return sections;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Option<?> option(String key, Object value) {
        if (!(value instanceof ModConfigSpec.ConfigValue<?> config)) {
            return null;
        }
        ModConfigSpec.ValueSpec spec = Config.SPEC.getSpec().get(config.getPath());
        OptionDescription description = spec != null && spec.getComment() != null
                ? OptionDescription.of(Component.literal(spec.getComment())) : OptionDescription.EMPTY;
        if (config instanceof ModConfigSpec.BooleanValue bool) {
            return Option.<Boolean>createBuilder().name(name(key)).description(description)
                    .binding(bool.getDefault(), bool::get, bool::set)
                    .controller(TickBoxControllerBuilder::create).build();
        }
        if (config instanceof ModConfigSpec.IntValue integer) {
            ModConfigSpec.Range<Integer> range = spec != null ? (ModConfigSpec.Range<Integer>) spec.getRange() : null;
            int min = range != null ? range.getMin() : 0;
            int max = range != null ? range.getMax() : 100;
            return Option.<Integer>createBuilder().name(name(key)).description(description)
                    .binding(integer.getDefault(), integer::get, integer::set)
                    .controller(opt -> IntegerSliderControllerBuilder.create(opt).range(min, max).step(max - min > 200 ? 10 : 1)).build();
        }
        if (config instanceof ModConfigSpec.EnumValue enumValue) {
            Class<? extends Enum> type = ((Enum) enumValue.getDefault()).getDeclaringClass();
            return Option.createBuilder().name(name(key)).description(description)
                    .binding(enumValue.getDefault(), enumValue::get, v -> enumValue.set(v))
                    .controller(opt -> EnumControllerBuilder.create((Option) opt).enumClass(type)).build();
        }
        return null;
    }

    private static Component name(String key) {
        String pretty = key.replaceAll("([a-z])([A-Z])", "$1 $2");
        pretty = pretty.substring(0, 1).toUpperCase(Locale.ROOT) + pretty.substring(1).toLowerCase(Locale.ROOT);
        return Component.translatableWithFallback("emi_reorganised.config." + key, pretty);
    }
}
