package com.eu.habbo.habbohotel.wired.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Typed capability index for Polaris-owned internal wired variables and their legacy aliases. */
final class WiredInternalVariableRegistry {
    private static final BooleanSupplier ALWAYS_ENABLED = () -> true;
    static final WiredInternalVariableRegistry DEFAULT = builtIns();

    enum Capability {
        USER_REFERENCE,
        USER_DESTINATION,
        FURNI_REFERENCE,
        FURNI_DESTINATION,
        ROOM_REFERENCE,
        CONTEXT_REFERENCE
    }

    private final Map<String, Definition> definitions;
    private final Map<String, String> aliases;

    private WiredInternalVariableRegistry(Map<String, Definition> definitions, Map<String, String> aliases) {
        this.definitions = definitions;
        this.aliases = aliases;
    }

    String normalize(String key) {
        if (key == null) {
            return "";
        }

        String trimmed = key.trim();
        return aliases.getOrDefault(trimmed, trimmed);
    }

    boolean supports(String key, Capability capability) {
        Definition definition = definitions.get(normalize(key));
        return definition != null
                && definition.enabled().getAsBoolean()
                && definition.capabilities().contains(capability);
    }

    Set<String> keys(Capability capability) {
        Set<String> result = new LinkedHashSet<>();
        definitions.forEach((key, definition) -> {
            if (definition.enabled().getAsBoolean() && definition.capabilities().contains(capability)) {
                result.add(key);
            }
        });
        return Collections.unmodifiableSet(result);
    }

    Map<String, String> aliases() {
        return aliases;
    }

    private static WiredInternalVariableRegistry builtIns() {
        Builder builder = new Builder();
        builder.register(
                Capability.USER_REFERENCE,
                "@index",
                "@type",
                "@gender",
                "@level",
                "@achievement_score",
                "@is_hc",
                "@has_rights",
                "@is_group_admin",
                "@is_owner",
                "@is_muted",
                "@is_trading",
                "@is_frozen",
                "@effect_id",
                "@team_score",
                "@team_color",
                "@team_type",
                "@sign",
                "@dance",
                "@is_idle",
                "@handitem_id",
                "@position_x",
                "@position_y",
                "@direction",
                "@altitude",
                "@favourite_group_id",
                "@room_entry.method",
                "@room_entry.teleport_id",
                "@user_id",
                "@bot_id",
                "@pet_id",
                "@pet_owner_id");
        builder.register(Capability.USER_DESTINATION, "@position_x", "@position_y", "@direction");
        builder.register(
                Capability.FURNI_REFERENCE,
                "~teleport.target_id",
                "@id",
                "@class_id",
                "@height",
                "@state",
                "@position_x",
                "@position_y",
                "@rotation",
                "@altitude",
                "@is_invisible",
                "@type",
                "@is_stackable",
                "@can_stand_on",
                "@can_sit_on",
                "@can_lay_on",
                "@owner_id",
                "@wallitem_offset",
                "@dimensions.x",
                "@dimensions.y");
        builder.register(
                Capability.FURNI_DESTINATION, "@state", "@position_x", "@position_y", "@rotation", "@altitude");
        builder.register(
                Capability.ROOM_REFERENCE,
                "@furni_count",
                "@user_count",
                "@wired_timer",
                "@team_red_score",
                "@team_green_score",
                "@team_blue_score",
                "@team_yellow_score",
                "@team_red_size",
                "@team_green_size",
                "@team_blue_size",
                "@team_yellow_size",
                "@room_id",
                "@group_id",
                "@timezone_server",
                "@timezone_client",
                "@current_time",
                "@current_time.millisecond_of_second",
                "@current_time.seconds_of_minute",
                "@current_time.minute_of_hour",
                "@current_time.hour_of_day",
                "@current_time.day_of_week",
                "@current_time.day_of_month",
                "@current_time.day_of_year",
                "@current_time.week_of_year",
                "@current_time.month_of_year",
                "@current_time.year");
        builder.register(
                Capability.CONTEXT_REFERENCE,
                "@selector_furni_count",
                "@selector_user_count",
                "@signal_furni_count",
                "@signal_user_count",
                "@antenna_id",
                "@chat_type",
                "@chat_style");

        builder.alias("@position.x", "@position_x");
        builder.alias("@position.y", "@position_y");
        builder.alias("@effect", "@effect_id");
        builder.alias("@handitems", "@handitem_id");
        builder.alias("@is_mute", "@is_muted");
        builder.alias("@teams.red.score", "@team_red_score");
        builder.alias("@teams.green.score", "@team_green_score");
        builder.alias("@teams.blue.score", "@team_blue_score");
        builder.alias("@teams.yellow.score", "@team_yellow_score");
        builder.alias("@teams.red.size", "@team_red_size");
        builder.alias("@teams.green.size", "@team_green_size");
        builder.alias("@teams.blue.size", "@team_blue_size");
        builder.alias("@teams.yellow.size", "@team_yellow_size");
        return builder.build();
    }

    static final class Builder {
        private final Map<String, EnumSet<Capability>> definitions = new LinkedHashMap<>();
        private final Map<String, BooleanSupplier> availability = new LinkedHashMap<>();
        private final Map<String, String> aliases = new LinkedHashMap<>();

        Builder register(Capability capability, String... keys) {
            return registerWhen(ALWAYS_ENABLED, EnumSet.of(capability), keys);
        }

        Builder registerWhen(BooleanSupplier enabled, Set<Capability> capabilities, String... keys) {
            if (enabled == null || capabilities == null || capabilities.isEmpty()) {
                throw new IllegalArgumentException(
                        "Internal variable registration requires availability and capabilities");
            }
            for (String key : keys) {
                validateKey(key);
                definitions
                        .computeIfAbsent(key, ignored -> EnumSet.noneOf(Capability.class))
                        .addAll(capabilities);
                BooleanSupplier previous = availability.putIfAbsent(key, enabled);
                if (previous != null && previous != enabled) {
                    throw new IllegalArgumentException("Conflicting internal variable availability: " + key);
                }
            }
            return this;
        }

        Builder alias(String alias, String canonicalKey) {
            validateKey(alias);
            validateKey(canonicalKey);
            String previous = aliases.putIfAbsent(alias, canonicalKey);
            if (previous != null && !previous.equals(canonicalKey)) {
                throw new IllegalArgumentException("Conflicting internal variable alias: " + alias);
            }
            return this;
        }

        WiredInternalVariableRegistry build() {
            aliases.forEach((alias, canonicalKey) -> {
                if (definitions.containsKey(alias)) {
                    throw new IllegalArgumentException("Internal variable alias shadows canonical key: " + alias);
                }
                if (!definitions.containsKey(canonicalKey)) {
                    throw new IllegalArgumentException("Unknown internal variable alias target: " + canonicalKey);
                }
            });

            Map<String, Definition> frozenDefinitions = new LinkedHashMap<>();
            definitions.forEach((key, capabilities) -> frozenDefinitions.put(
                    key,
                    new Definition(
                            Collections.unmodifiableSet(EnumSet.copyOf(capabilities)),
                            availability.getOrDefault(key, ALWAYS_ENABLED))));
            return new WiredInternalVariableRegistry(
                    Collections.unmodifiableMap(frozenDefinitions),
                    Collections.unmodifiableMap(new LinkedHashMap<>(aliases)));
        }

        private static void validateKey(String key) {
            if (key == null || key.isBlank() || !key.equals(key.trim())) {
                throw new IllegalArgumentException("Invalid internal variable key");
            }
        }
    }

    private record Definition(Set<Capability> capabilities, BooleanSupplier enabled) {}
}
