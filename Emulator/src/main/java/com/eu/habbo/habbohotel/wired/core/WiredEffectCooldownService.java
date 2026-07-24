package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.items.interactions.InteractionWiredEffect;
import com.eu.habbo.habbohotel.wired.api.IWiredEffect;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import java.util.function.BooleanSupplier;

/** Atomic cooldown admission for first-party and plugin WIRED effects. */
final class WiredEffectCooldownService {

    private final BooleanSupplier customWiredEnabled;
    private final Map<IWiredEffect, Long> pluginCooldowns = Collections.synchronizedMap(new WeakHashMap<>());

    WiredEffectCooldownService(BooleanSupplier customWiredEnabled) {
        this.customWiredEnabled = Objects.requireNonNull(customWiredEnabled, "customWiredEnabled");
    }

    boolean tryAcquire(IWiredEffect effect, WiredContext context, long timestamp) {
        if (effect == null) {
            return false;
        }
        if (effect instanceof InteractionWiredEffect interactionEffect) {
            int actorId =
                    context != null ? context.actor().map(unit -> unit.getId()).orElse(-1) : -1;
            return tryAcquireFirstParty(interactionEffect, actorId, timestamp);
        }
        return tryAcquirePlugin(effect, timestamp);
    }

    void clear() {
        this.pluginCooldowns.clear();
    }

    private boolean tryAcquireFirstParty(InteractionWiredEffect effect, int actorId, long timestamp) {
        synchronized (effect) {
            boolean globallyEligible = effect.canExecute(timestamp);
            boolean userBypassEligible = !globallyEligible
                    && actorId >= 0
                    && effect.requiresTriggeringUser()
                    && this.customWiredEnabled.getAsBoolean()
                    && effect.userCanExecute(actorId, timestamp);
            if (!globallyEligible && !userBypassEligible) {
                return false;
            }

            effect.setCooldown(timestamp);
            if (actorId >= 0) {
                effect.addUserExecutionCache(actorId, timestamp);
            }
            return true;
        }
    }

    private boolean tryAcquirePlugin(IWiredEffect effect, long timestamp) {
        long requiredCooldown = Math.max(0L, effect.getCooldown());
        if (requiredCooldown == 0L) {
            return true;
        }
        synchronized (this.pluginCooldowns) {
            Long previous = this.pluginCooldowns.get(effect);
            if (previous != null && timestamp - previous < requiredCooldown) {
                return false;
            }
            this.pluginCooldowns.put(effect, timestamp);
            return true;
        }
    }
}
