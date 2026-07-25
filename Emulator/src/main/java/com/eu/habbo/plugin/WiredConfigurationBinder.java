package com.eu.habbo.plugin;

import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.config.ConfigurationBinder;
import com.eu.habbo.habbohotel.items.interactions.wired.effects.WiredEffectSendSignal;
import com.eu.habbo.habbohotel.wired.arrays.WiredArrayDefinition;
import com.eu.habbo.habbohotel.wired.arrays.WiredArraySettings;
import com.eu.habbo.habbohotel.wired.core.WiredEngine;
import com.eu.habbo.habbohotel.wired.core.WiredManager;

final class WiredConfigurationBinder extends ConfigurationBinder {

    WiredConfigurationBinder(ConfigurationManager configuration) {
        super(configuration);
    }

    void bind() {
        this.apply(
                "hotel.wired.furni.selection.count",
                () -> WiredManager.MAXIMUM_FURNI_SELECTION =
                        this.configuration.getInt("hotel.wired.furni.selection.count"));
        this.apply(
                "wired.effect.teleport.delay",
                () -> WiredManager.TELEPORT_DELAY = this.configuration.getInt("wired.effect.teleport.delay", 500));
        this.apply(
                "wired.signal.max.depth",
                () -> WiredEffectSendSignal.MAX_SIGNAL_DEPTH =
                        this.configuration.getInt("wired.signal.max.depth", 100));
        this.apply(
                "wired.abuse.max.recursion.depth",
                () -> WiredEngine.MAX_RECURSION_DEPTH =
                        this.configuration.getInt("wired.abuse.max.recursion.depth", 10));
        this.apply(
                "wired.abuse.max.events.per.window",
                () -> WiredEngine.MAX_EVENTS_PER_WINDOW =
                        this.configuration.getInt("wired.abuse.max.events.per.window", 100));
        this.apply(
                "wired.abuse.rate.limit.window.ms",
                () -> WiredEngine.RATE_LIMIT_WINDOW_MS =
                        this.configuration.getInt("wired.abuse.rate.limit.window.ms", 10000));
        this.apply(
                "wired.abuse.ban.duration.ms",
                () -> WiredEngine.WIRED_BAN_DURATION_MS =
                        this.configuration.getInt("wired.abuse.ban.duration.ms", 600000));
        this.apply(
                "wired.monitor.usage.window.ms",
                () -> WiredEngine.MONITOR_USAGE_WINDOW_MS =
                        this.configuration.getInt("wired.monitor.usage.window.ms", 1000));
        this.apply(
                "wired.monitor.usage.limit",
                () -> WiredEngine.MONITOR_USAGE_LIMIT = this.configuration.getInt("wired.monitor.usage.limit", 50000));
        this.apply(
                "wired.monitor.delayed.events.limit",
                () -> WiredEngine.MONITOR_DELAYED_EVENTS_LIMIT =
                        this.configuration.getInt("wired.monitor.delayed.events.limit", 50000));
        this.apply(
                "wired.monitor.overload.average.ms",
                () -> WiredEngine.MONITOR_OVERLOAD_AVERAGE_MS =
                        this.configuration.getInt("wired.monitor.overload.average.ms", 50));
        this.apply(
                "wired.monitor.overload.peak.ms",
                () -> WiredEngine.MONITOR_OVERLOAD_PEAK_MS =
                        this.configuration.getInt("wired.monitor.overload.peak.ms", 150));
        this.apply(
                "wired.monitor.overload.consecutive.windows",
                () -> WiredEngine.MONITOR_OVERLOAD_CONSECUTIVE_WINDOWS =
                        this.configuration.getInt("wired.monitor.overload.consecutive.windows", 2));
        this.apply(
                "wired.monitor.heavy.usage.percent",
                () -> WiredEngine.MONITOR_HEAVY_USAGE_PERCENT =
                        this.configuration.getInt("wired.monitor.heavy.usage.percent", 70));
        this.apply(
                "wired.monitor.heavy.consecutive.windows",
                () -> WiredEngine.MONITOR_HEAVY_CONSECUTIVE_WINDOWS =
                        this.configuration.getInt("wired.monitor.heavy.consecutive.windows", 5));
        this.apply(
                "wired.monitor.heavy.delayed.percent",
                () -> WiredEngine.MONITOR_HEAVY_DELAYED_PERCENT =
                        this.configuration.getInt("wired.monitor.heavy.delayed.percent", 60));
        this.apply(
                "hotel.wired.arrays",
                () -> WiredArraySettings.configure(
                        this.configuration.getInt(
                                "hotel.wired.arrays.max_entries", WiredArrayDefinition.ABSOLUTE_MAX_ENTRIES),
                        this.configuration.getInt(
                                "hotel.wired.arrays.max_populated_cells_per_owner",
                                WiredArrayDefinition.DEFAULT_MAX_POPULATED_CELLS),
                        this.configuration.getInt(
                                "hotel.wired.arrays.max_owners_per_execution",
                                WiredArraySettings.DEFAULT_MAX_OWNERS_PER_EXECUTION)));

        if (WiredManager.getEngine() != null) {
            WiredManager.getEngine().clearAllDiagnostics();
        }
    }
}
