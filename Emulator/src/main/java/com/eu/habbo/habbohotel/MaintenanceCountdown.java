package com.eu.habbo.habbohotel;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClientManager;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import com.eu.habbo.messages.outgoing.handshake.DisconnectReasonComposer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

/**
 * Maintenance with a countdown. Online users are told the hotel closes in N minutes and reminded
 * as the time runs down; when it ends, maintenance is switched on and everyone below the
 * maintenance rank is disconnected with the maintenance reason. With no countdown the last step
 * happens at once. Maintenance itself (the login gate and its message) stays in
 * {@link MaintenanceMode}.
 */
public final class MaintenanceCountdown {
    public static final int MAX_MINUTES = 120;

    /** Minutes left at which online users are reminded, after the announcement at the start. */
    static final int[] REMINDER_MINUTES = {30, 15, 10, 5, 3, 2, 1};

    private static final long TICK_MS = 60_000L;

    /** The one countdown of the hotel; its state is guarded by its own monitor. */
    private static final MaintenanceCountdown INSTANCE = new MaintenanceCountdown();

    private ScheduledFuture<?> pending;
    private int generation;
    private int endsAt;
    private String closingMessage;

    private MaintenanceCountdown() {}

    /** Unix time the running countdown ends at, or 0 when none is running. */
    public static int getEndsAt() {
        synchronized (INSTANCE) {
            return INSTANCE.pending == null ? 0 : INSTANCE.endsAt;
        }
    }

    /**
     * Starts a countdown of {@code minutes} and replaces a running one; 0 switches maintenance on
     * and disconnects at once. {@code message} replaces the maintenance message when not blank.
     */
    public static void start(int minutes, String message) {
        synchronized (INSTANCE) {
            INSTANCE.cancelPending();
            INSTANCE.closingMessage = message;

            if (minutes <= 0) {
                INSTANCE.close();
                return;
            }

            INSTANCE.endsAt = now() + minutes * 60;
            broadcast(new HotelWillCloseInMinutesComposer(minutes).compose());
            INSTANCE.scheduleTick(INSTANCE.generation, TICK_MS);
        }
    }

    /** Stops a running countdown; maintenance stays as it is. Returns whether one was running. */
    public static boolean cancel() {
        synchronized (INSTANCE) {
            boolean running = INSTANCE.pending != null;
            INSTANCE.cancelPending();
            return running;
        }
    }

    static boolean isReminder(int minutesLeft) {
        for (int minutes : REMINDER_MINUTES) {
            if (minutes == minutesLeft) return true;
        }

        return false;
    }

    /** Whole minutes left, rounded up, so the last minute still reads 1. */
    static int minutesLeft(int endsAt, int now) {
        int seconds = endsAt - now;
        return seconds <= 0 ? 0 : (seconds + 59) / 60;
    }

    private void tick(int tickGeneration) {
        synchronized (this) {
            // A countdown cancelled or replaced while this tick waited must not act.
            if (tickGeneration != generation || pending == null) return;

            int minutesLeft = minutesLeft(endsAt, now());

            if (minutesLeft <= 0) {
                pending = null;
                close();
                return;
            }

            if (isReminder(minutesLeft)) {
                broadcast(new HotelWillCloseInMinutesComposer(minutesLeft).compose());
            }

            scheduleTick(tickGeneration, Math.min(TICK_MS, (endsAt - now()) * 1000L));
        }
    }

    private void close() {
        MaintenanceMode.setEnabled(true, closingMessage);

        GameClientManager clients = Emulator.getGameServer().getGameClientManager();

        for (Habbo habbo : onlineHabbos()) {
            if (habbo.getClient() == null
                    || habbo.getHabboInfo() == null
                    || habbo.getHabboInfo().getRank() == null) {
                continue;
            }

            if (MaintenanceMode.canLogin(habbo.getHabboInfo().getRank().getId())) continue;

            clients.disconnectWithReason(habbo.getClient(), DisconnectReasonComposer.MAINTENANCE);
        }
    }

    private void scheduleTick(int tickGeneration, long delayMs) {
        this.pending = Emulator.getThreading().run(() -> this.tick(tickGeneration), Math.max(1_000L, delayMs));
    }

    private void cancelPending() {
        generation++;

        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
    }

    private static void broadcast(ServerMessage message) {
        for (Habbo habbo : onlineHabbos()) {
            if (habbo.getClient() != null) habbo.getClient().sendResponse(message);
        }
    }

    /** A copy, so disconnecting while iterating does not touch the live map. */
    private static List<Habbo> onlineHabbos() {
        Collection<Habbo> online = Emulator.getGameEnvironment()
                .getHabboManager()
                .getOnlineHabbos()
                .values();
        List<Habbo> copy = new ArrayList<>(online.size());

        for (Habbo habbo : online) {
            if (habbo != null) copy.add(habbo);
        }

        return copy;
    }

    private static int now() {
        return (int) (System.currentTimeMillis() / 1000L);
    }
}
