package com.eu.habbo.habbohotel.gameclients;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserEffectComposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

public class SessionResumeManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(SessionResumeManager.class);

    private static SessionResumeManager instance;

    private final ConcurrentHashMap<Integer, GhostSession> ghostSessions = new ConcurrentHashMap<>();

    public static SessionResumeManager getInstance() {
        if (instance == null) {
            instance = new SessionResumeManager();
        }
        return instance;
    }

    public int getGracePeriodSeconds() {
        return Emulator.getConfig().getInt("session.reconnect.grace.seconds", 30);
    }

    public int getPausedEffectId() {
        return Emulator.getConfig().getInt("session.reconnect.effect.id", 170);
    }

    public boolean parkHabbo(Habbo habbo, String ssoTicket) {
        int graceSeconds = getGracePeriodSeconds();
        if (graceSeconds <= 0) {
            return false;
        }

        int userId = habbo.getHabboInfo().getId();

        GhostSession existing = ghostSessions.remove(userId);
        if (existing != null && existing.disposeFuture != null) {
            existing.disposeFuture.cancel(false);
        }

        LOGGER.info("[SessionResume] Parking {} (id={}) for {}s grace period",
                habbo.getHabboInfo().getUsername(), userId, graceSeconds);

        if (ssoTicket != null && !ssoTicket.isEmpty()) {
            restoreSsoTicket(userId, ssoTicket);
        }

        int previousEffectId = 0;
        int previousEffectEnd = 0;
        RoomUnit unit = habbo.getRoomUnit();
        if (unit != null) {
            previousEffectId = unit.getEffectId();
            previousEffectEnd = unit.getEffectEndTimestamp();
        }

        ScheduledFuture<?> future = Emulator.getThreading().run(() -> {
            GhostSession ghost = ghostSessions.remove(userId);
            if (ghost != null) {
                LOGGER.info("[SessionResume] Grace period expired for {} (id={}) - performing full disconnect",
                        ghost.habbo.getHabboInfo().getUsername(), userId);
                performFullDisconnect(ghost.habbo);
            }
        }, graceSeconds * 1000);

        if (future == null) {
            // The scheduler refused the grace-expiry task (pool saturated or
            // shutting down). Parking now would leave a GhostSession that nothing
            // can ever reap (the Habbo + room refs pinned for the JVM lifetime),
            // so disconnect immediately instead.
            performFullDisconnect(habbo);
            return false;
        }

        ghostSessions.put(userId, new GhostSession(habbo, ssoTicket, future, previousEffectId, previousEffectEnd));

        applyPausedEffect(habbo);

        return true;
    }

    public Habbo resumeSession(int userId) {
        GhostSession ghost = ghostSessions.remove(userId);
        if (ghost == null) {
            return null;
        }

        if (ghost.disposeFuture != null) {
            ghost.disposeFuture.cancel(false);
        }

        LOGGER.info("[SessionResume] Resuming session for {} (id={})",
                ghost.habbo.getHabboInfo().getUsername(), userId);

        restorePausedEffect(ghost);

        return ghost.habbo;
    }

    public boolean hasGhostSession(int userId) {
        return ghostSessions.containsKey(userId);
    }

    public void disposeAll() {
        for (GhostSession ghost : ghostSessions.values()) {
            if (ghost.disposeFuture != null) {
                ghost.disposeFuture.cancel(false);
            }
            performFullDisconnect(ghost.habbo);
        }
        ghostSessions.clear();
    }

    private void performFullDisconnect(Habbo habbo) {
        try {
            habbo.getHabboInfo().setOnline(false);
            habbo.disconnect();
        } catch (Exception e) {
            LOGGER.error("[SessionResume] Error during deferred disconnect", e);
        }

        // NON svuotare il ticket SSO qui. Dietro Cloudflare la pagina si ricarica
        // lentamente (~15s) e la grace (5s) scade prima che la nuova connessione
        // arrivi: svuotando il ticket si cancellava quello NUOVO appena scritto dal
        // CMS per il refresh → "non-existing SSO token" → bisognava refreshare 2 volte.
        // Il ticket vive col suo TTL (auth_ticket_expires_at) e viene sovrascritto dal
        // CMS al prossimo /client o azzerato al logout.
    }

    private void restoreSsoTicket(int userId, String ssoTicket) {
        // Restore the old ticket ONLY if no fresh ticket has been written in the
        // meantime. On a hard-refresh the CMS writes a NEW auth_ticket for the same
        // user before this parking restore runs; without the guard we'd clobber it
        // with the old ticket, so the new connection's SSO wouldn't be found and the
        // client would get "session expired" on the first attempt. The guard means:
        // normal reconnect (ticket cleared to '' after login) -> restore; hard-refresh
        // (CMS already wrote a new ticket) -> leave the new ticket untouched.
        try (var connection = Emulator.getDatabase().getDataSource().getConnection();
             var statement = connection.prepareStatement("UPDATE users SET auth_ticket = ? WHERE id = ? AND (auth_ticket = '' OR auth_ticket IS NULL) LIMIT 1")) {
            statement.setString(1, ssoTicket);
            statement.setInt(2, userId);
            int updated = statement.executeUpdate();
            if (updated > 0) {
                LOGGER.info("[SessionResume] Restored SSO ticket for user {} during grace period", userId);
            } else {
                LOGGER.info("[SessionResume] Skipped SSO restore for user {} — a newer ticket is already present (likely a fresh login/hard-refresh)", userId);
            }
        } catch (Exception e) {
            LOGGER.error("[SessionResume] Failed to restore SSO ticket for user " + userId, e);
        }
    }

    private void applyPausedEffect(Habbo habbo) {
        int effectId = getPausedEffectId();
        if (effectId <= 0) return;
        try {
            RoomUnit unit = habbo.getRoomUnit();
            Room room = habbo.getHabboInfo() == null ? null : habbo.getHabboInfo().getCurrentRoom();
            if (unit == null || room == null) return;
            int endTimestamp = Emulator.getIntUnixTimestamp() + getGracePeriodSeconds() + 10;
            unit.setEffectId(effectId, endTimestamp);
            room.sendComposer(new RoomUserEffectComposer(unit).compose());
        } catch (Exception e) {
            LOGGER.error("[SessionResume] Failed to apply paused effect", e);
        }
    }

    private void restorePausedEffect(GhostSession ghost) {
        try {
            Habbo habbo = ghost.habbo;
            RoomUnit unit = habbo.getRoomUnit();
            Room room = habbo.getHabboInfo() == null ? null : habbo.getHabboInfo().getCurrentRoom();
            if (unit == null || room == null) return;

            int pausedEffectId = getPausedEffectId();
            if (unit.getEffectId() == pausedEffectId) {
                unit.setEffectId(ghost.previousEffectId, ghost.previousEffectEnd);
                room.sendComposer(new RoomUserEffectComposer(unit).compose());
            }
        } catch (Exception e) {
            LOGGER.error("[SessionResume] Failed to restore previous effect", e);
        }
    }

    private void clearSsoTicket(int userId) {
        try (var connection = Emulator.getDatabase().getDataSource().getConnection();
             var statement = connection.prepareStatement("UPDATE users SET auth_ticket = ? WHERE id = ? LIMIT 1")) {
            statement.setString(1, "");
            statement.setInt(2, userId);
            statement.execute();
        } catch (Exception e) {
            LOGGER.error("[SessionResume] Failed to clear SSO ticket for user " + userId, e);
        }
    }

    private static class GhostSession {
        final Habbo habbo;
        final String ssoTicket;
        final ScheduledFuture<?> disposeFuture;
        final int previousEffectId;
        final int previousEffectEnd;

        GhostSession(Habbo habbo, String ssoTicket, ScheduledFuture<?> disposeFuture,
                     int previousEffectId, int previousEffectEnd) {
            this.habbo = habbo;
            this.ssoTicket = ssoTicket;
            this.disposeFuture = disposeFuture;
            this.previousEffectId = previousEffectId;
            this.previousEffectEnd = previousEffectEnd;
        }
    }
}
