package com.eu.habbo.habbohotel.wired.api;

/**
 * Optional plugin contract for values that cross a delayed WIRED execution boundary.
 *
 * <p>Polaris rehydrates room furniture and room units from their live identifiers. A plugin-owned
 * component or value has no equivalent identifier, so legacy plugins historically caused the
 * scheduler to retain the original instance until execution. Implementing this interface lets a
 * plugin provide a detached, schedule-time copy instead.
 *
 * <p>The returned value must be a different instance assignable to the provider's concrete class.
 * It must not retain a {@code Room}, {@code HabboItem}, {@code RoomUnit}, client session, database
 * connection or another live mutable runtime object. Polaris calls this method synchronously once
 * while capturing a delayed batch. A null, incompatible, identical or throwing result keeps the
 * legacy identity fallback so existing plugins continue to work.
 *
 * @param <T> the concrete plugin value or WIRED component type
 */
@FunctionalInterface
public interface WiredDelayedSnapshotProvider<T> {

    /**
     * Create the detached value that the delayed execution should observe.
     *
     * @return a detached copy assignable to the provider's concrete class
     */
    T snapshotForDelayedExecution();
}
