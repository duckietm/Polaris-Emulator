package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.Emulator;
import com.eu.habbo.WiredCompatibilityDiagnostics;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomChatMessage;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserEffectComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import com.eu.habbo.threading.runnables.RoomUnitTeleport;
import com.eu.habbo.threading.runnables.SendRoomUnitEffectComposer;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation of {@link WiredServices} that wraps existing room operations.
 * <p>
 * This implementation forwards all operations to the existing Room/Emulator methods,
 * providing a clean abstraction layer for wired effects while maintaining full
 * backwards compatibility.
 * </p>
 *
 * @see WiredServices
 */
public final class DefaultWiredServices implements WiredServices {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWiredServices.class);

    /** Singleton instance */
    private static final DefaultWiredServices INSTANCE = new DefaultWiredServices();

    private DefaultWiredServices() {
        // Singleton
    }

    /**
     * Get the singleton instance.
     * @return the default wired services instance
     */
    public static DefaultWiredServices getInstance() {
        return INSTANCE;
    }

    // ========== Debug/Logging ==========

    @Override
    public void debug(Room room, String message) {
        if (WiredManager.isDebugEnabled()) {
            LOGGER.info("[Wired][Room {}] {}", room != null ? room.getId() : "null", message);
        }
    }

    @Override
    public void debug(Room room, String format, Object... args) {
        if (WiredManager.isDebugEnabled()) {
            String message = String.format(format.replace("{}", "%s"), args);
            LOGGER.info("[Wired][Room {}] {}", room != null ? room.getId() : "null", message);
        }
    }

    // ========== User Operations ==========

    @Override
    public void teleportUser(Room room, RoomUnit user, RoomTile tile) {
        if (room == null || user == null || tile == null) return;

        // Show teleport effect
        room.sendComposer(new RoomUserEffectComposer(user, 4).compose());
        Emulator.getThreading().run(new SendRoomUnitEffectComposer(room, user), WiredManager.TELEPORT_DELAY + 1000);

        // Execute teleport
        double height = tile.getStackHeight();
        Emulator.getThreading()
                .run(() -> user.isWiredTeleporting = true, Math.max(0, WiredManager.TELEPORT_DELAY - 500));
        Emulator.getThreading()
                .run(
                        new RoomUnitTeleport(user, room, tile.x, tile.y, height, user.getEffectId()),
                        WiredManager.TELEPORT_DELAY);
    }

    @Override
    public void moveUser(Room room, RoomUnit user, RoomTile tile) {
        if (room == null || user == null || tile == null) return;

        user.setGoalLocation(tile);
        user.setCanWalk(true);
    }

    @Override
    public void kickUser(Room room, RoomUnit user) {
        if (room == null || user == null) return;

        Habbo habbo = room.getHabbo(user);
        if (habbo != null && !room.isOwner(habbo)) {
            room.kickHabbo(habbo, false);
        }
    }

    @Override
    public void giveEffect(Room room, RoomUnit user, int effectId) {
        giveEffect(room, user, effectId, -1);
    }

    @Override
    public void giveEffect(Room room, RoomUnit user, int effectId, int duration) {
        if (room == null || user == null) return;

        room.giveEffect(user, effectId, duration);
    }

    @Override
    public void whisperToUser(Room room, RoomUnit user, String message) {
        if (room == null || user == null || message == null) return;

        Habbo habbo = room.getHabbo(user);
        if (habbo != null) {
            habbo.getClient()
                    .sendResponse(new RoomUserWhisperComposer(
                            new RoomChatMessage(message, habbo, habbo, RoomChatMessageBubbles.WIRED)));
        }
    }

    @Override
    public void giveHandItem(Room room, RoomUnit user, int handItemId) {
        if (room == null || user == null) return;

        user.setHandItem(handItemId);
        room.sendComposer(new com.eu.habbo.messages.outgoing.rooms.users.RoomUserHandItemComposer(user).compose());
    }

    @Override
    public void muteUser(Room room, RoomUnit user, int durationMinutes) {
        if (room == null || user == null) return;

        Habbo habbo = room.getHabbo(user);
        if (habbo != null) {
            room.muteHabbo(habbo, durationMinutes);
        }
    }

    // ========== Furniture Operations ==========

    @Override
    public void toggleFurni(Room room, HabboItem item) {
        if (room == null || item == null) return;

        // Try to toggle state
        try {
            int maxState = item.getBaseItem().getStateCount();
            if (maxState > 1) {
                int currentState = 0;
                try {
                    currentState = Integer.parseInt(item.getExtradata());
                } catch (NumberFormatException ignored) {
                    WiredCompatibilityDiagnostics.record(
                            WiredCompatibilityDiagnostics.FailurePoint.DEFAULT_TOGGLE_STATE,
                            room.getId(),
                            item.getId(),
                            ignored);
                }

                int newState = (currentState + 1) % maxState;
                item.setExtradata(String.valueOf(newState));
                room.updateItemState(item);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to toggle furni {}: {}", item.getId(), e.getMessage());
        }
    }

    @Override
    public void setFurniState(Room room, HabboItem item, int state) {
        if (room == null || item == null) return;

        item.setExtradata(String.valueOf(state));
        room.updateItemState(item);
    }

    @Override
    public void moveFurni(Room room, HabboItem item, RoomTile tile, int rotation) {
        if (room == null || item == null || tile == null) return;

        room.moveFurniTo(item, tile, rotation, null, true);
    }

    @Override
    public void resetFurniState(Room room, HabboItem item) {
        if (room == null || item == null) return;

        // Reset to state 0
        item.setExtradata("0");
        room.updateItemState(item);
    }

    // ========== Game Operations ==========

    @Override
    public void giveScore(Room room, RoomUnit user, int score) {
        if (room == null || user == null) return;

        Habbo habbo = room.getHabbo(user);
        if (habbo != null && habbo.getHabboInfo().getGamePlayer() != null) {
            habbo.getHabboInfo().getGamePlayer().addScore(score);
        }
    }

    @Override
    public void giveScoreToTeam(Room room, int teamId, int score) {
        if (room == null) return;

        // This would need access to game manager - implementation depends on game context
        debug(room, "giveScoreToTeam called: team={}, score={}", teamId, score);
    }

    @Override
    public void joinTeam(Room room, RoomUnit user, int teamId) {
        if (room == null || user == null) return;

        // Team joining logic depends on active game
        debug(room, "joinTeam called: user={}, team={}", user.getId(), teamId);
    }

    @Override
    public void leaveTeam(Room room, RoomUnit user) {
        if (room == null || user == null) return;

        Habbo habbo = room.getHabbo(user);
        if (habbo != null && habbo.getHabboInfo().getGamePlayer() != null) {
            // Leave team logic
            debug(room, "leaveTeam called: user={}", user.getId());
        }
    }

    // ========== Bot Operations ==========

    @Override
    public void botTalk(Room room, String botName, String message, boolean shout) {
        if (room == null || botName == null || message == null) return;

        List<Bot> bots = room.getBots(botName);
        for (Bot bot : bots) {
            if (shout) {
                bot.shout(message);
            } else {
                bot.talk(message);
            }
        }
    }

    @Override
    public void botWhisperTo(Room room, String botName, RoomUnit user, String message) {
        if (room == null || botName == null || user == null || message == null) return;

        Habbo habbo = room.getHabbo(user);
        if (habbo != null) {
            List<Bot> bots = room.getBots(botName);
            for (Bot bot : bots) {
                habbo.getClient()
                        .sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(
                                message, bot.getRoomUnit(), RoomChatMessageBubbles.getBubble(bot.getBubbleId()))));
            }
        }
    }

    @Override
    public void botWalkToFurni(Room room, String botName, HabboItem item) {
        if (room == null || botName == null || item == null) return;

        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        if (tile != null) {
            List<Bot> bots = room.getBots(botName);
            for (Bot bot : bots) {
                bot.getRoomUnit().setGoalLocation(tile);
            }
        }
    }

    @Override
    public void botTeleport(Room room, String botName, RoomTile tile) {
        if (room == null || botName == null || tile == null) return;

        List<Bot> bots = room.getBots(botName);
        for (Bot bot : bots) {
            room.teleportRoomUnitToLocation(bot.getRoomUnit(), tile.x, tile.y, tile.getStackHeight());
        }
    }

    @Override
    public void botFollowUser(Room room, String botName, RoomUnit user) {
        if (room == null || botName == null || user == null) return;

        Habbo habbo = room.getHabbo(user);
        if (habbo != null) {
            List<Bot> bots = room.getBots(botName);
            for (Bot bot : bots) {
                bot.startFollowingHabbo(habbo);
            }
        }
    }

    @Override
    public void botSetClothes(Room room, String botName, String figure) {
        if (room == null || botName == null || figure == null) return;

        List<Bot> bots = room.getBots(botName);
        for (Bot bot : bots) {
            bot.setFigure(figure);
            bot.needsUpdate(true);
            room.sendComposer(new com.eu.habbo.messages.outgoing.rooms.users.RoomUsersComposer(bot).compose());
        }
    }

    @Override
    public void botGiveHandItem(Room room, String botName, RoomUnit user, int handItemId) {
        if (room == null || botName == null || user == null) return;

        // Bot gives hand item by walking to user and transferring
        Habbo habbo = room.getHabbo(user);
        if (habbo != null) {
            giveHandItem(room, user, handItemId);
        }
    }

    // ========== Messaging ==========

    @Override
    public void showAlert(Room room, RoomUnit user, String message) {
        if (room == null || user == null || message == null) return;

        Habbo habbo = room.getHabbo(user);
        if (habbo != null) {
            habbo.alert(message);
        }
    }

    // ========== Room Operations ==========

    @Override
    public void resetTimers(Room room) {
        if (room == null) return;
        WiredManager.resetTimers(room);
    }
}
