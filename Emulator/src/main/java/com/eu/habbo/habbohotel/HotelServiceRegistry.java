package com.eu.habbo.habbohotel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns hotel-service construction and verified lifecycle callbacks.
 */
final class HotelServiceRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(HotelServiceRegistry.class);

    private final Map<String, Object> services = new LinkedHashMap<>();
    private final List<LifecycleAction> beforeDispose = new ArrayList<>();
    private final List<LifecycleAction> disposalActions = new ArrayList<>();
    private final AtomicBoolean disposed = new AtomicBoolean();

    <T> T create(String name, Factory<T> factory) throws Exception {
        return create(name, factory, null);
    }

    <T> T create(String name, Factory<T> factory, Consumer<T> disposer) throws Exception {
        if (this.services.containsKey(name)) {
            throw new IllegalStateException("Hotel service already exists: " + name);
        }

        T service = factory.create();
        this.services.put(name, service);
        if (disposer != null) {
            this.disposalActions.add(new LifecycleAction(name, () -> disposer.accept(service)));
        }
        return service;
    }

    void onDispose(String name, Runnable action) {
        this.disposalActions.add(new LifecycleAction(name, action));
    }

    void beforeDispose(String name, Runnable action) {
        this.beforeDispose.add(new LifecycleAction(name, action));
    }

    boolean hasServices() {
        return !this.services.isEmpty();
    }

    void dispose() {
        if (!this.disposed.compareAndSet(false, true)) {
            return;
        }

        for (LifecycleAction action : this.beforeDispose) {
            run(action);
        }
        for (int index = this.disposalActions.size() - 1; index >= 0; index--) {
            run(this.disposalActions.get(index));
        }
    }

    private static void run(LifecycleAction action) {
        try {
            action.action().run();
        } catch (Throwable throwable) {
            LOGGER.error("Hotel service lifecycle action failed: {}", action.name(), throwable);
        }
    }

    @FunctionalInterface
    interface Factory<T> {
        T create() throws Exception;
    }

    private record LifecycleAction(String name, Runnable action) {}
}
