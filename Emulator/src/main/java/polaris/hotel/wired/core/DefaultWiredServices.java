package polaris.hotel.wired.core;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.WiredCompatibilityDiagnostics;
import polaris.hotel.bots.Bot;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.users.User;
import polaris.hotel.users.UserItem;
import polaris.messages.outgoing.rooms.users.RoomUserEffectComposer;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import polaris.threading.runnables.RoomUnitTeleport;
import polaris.threading.runnables.SendRoomUnitEffectComposer;

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
    public void teleportUser(Room room, RoomUnit unit, RoomTile tile) {
        if (room == null || unit == null || tile == null) return;

        // Show teleport effect
        room.sendComposer(new RoomUserEffectComposer(unit, 4).compose());
        Emulator.getThreading().run(new SendRoomUnitEffectComposer(room, unit), WiredManager.TELEPORT_DELAY + 1000);

        // Execute teleport
        double height = tile.getStackHeight();
        Emulator.getThreading()
                .run(() -> unit.isWiredTeleporting = true, Math.max(0, WiredManager.TELEPORT_DELAY - 500));
        Emulator.getThreading()
                .run(
                        new RoomUnitTeleport(unit, room, tile.x, tile.y, height, unit.getEffectId()),
                        WiredManager.TELEPORT_DELAY);
    }

    @Override
    public void moveUser(Room room, RoomUnit unit, RoomTile tile) {
        if (room == null || unit == null || tile == null) return;

        unit.setGoalLocation(tile);
        unit.setCanWalk(true);
    }

    @Override
    public void kickUser(Room room, RoomUnit unit) {
        if (room == null || unit == null) return;

        User user = room.getUser(unit);
        if (user != null && !room.isOwner(user)) {
            room.kickUser(user, false);
        }
    }

    @Override
    public void giveEffect(Room room, RoomUnit unit, int effectId) {
        giveEffect(room, unit, effectId, -1);
    }

    @Override
    public void giveEffect(Room room, RoomUnit unit, int effectId, int duration) {
        if (room == null || unit == null) return;

        room.giveEffect(unit, effectId, duration);
    }

    @Override
    public void whisperToUser(Room room, RoomUnit unit, String message) {
        if (room == null || unit == null || message == null) return;

        User user = room.getUser(unit);
        if (user != null) {
            user.getClient()
                    .sendResponse(new RoomUserWhisperComposer(
                            new RoomChatMessage(message, user, user, RoomChatMessageBubbles.WIRED)));
        }
    }

    @Override
    public void giveHandItem(Room room, RoomUnit unit, int handItemId) {
        if (room == null || unit == null) return;

        unit.setHandItem(handItemId);
        room.sendComposer(new polaris.messages.outgoing.rooms.users.RoomUserHandItemComposer(unit).compose());
    }

    @Override
    public void muteUser(Room room, RoomUnit unit, int durationMinutes) {
        if (room == null || unit == null) return;

        User user = room.getUser(unit);
        if (user != null) {
            room.muteUser(user, durationMinutes);
        }
    }

    // ========== Furniture Operations ==========

    @Override
    public void toggleFurni(Room room, UserItem item) {
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
    public void setFurniState(Room room, UserItem item, int state) {
        if (room == null || item == null) return;

        item.setExtradata(String.valueOf(state));
        room.updateItemState(item);
    }

    @Override
    public void moveFurni(Room room, UserItem item, RoomTile tile, int rotation) {
        if (room == null || item == null || tile == null) return;

        room.moveFurniTo(item, tile, rotation, null, true);
    }

    @Override
    public void resetFurniState(Room room, UserItem item) {
        if (room == null || item == null) return;

        // Reset to state 0
        item.setExtradata("0");
        room.updateItemState(item);
    }

    // ========== Game Operations ==========

    @Override
    public void giveScore(Room room, RoomUnit unit, int score) {
        if (room == null || unit == null) return;

        User user = room.getUser(unit);
        if (user != null && user.getUserInfo().getGamePlayer() != null) {
            user.getUserInfo().getGamePlayer().addScore(score);
        }
    }

    @Override
    public void giveScoreToTeam(Room room, int teamId, int score) {
        if (room == null) return;

        // This would need access to game manager - implementation depends on game context
        debug(room, "giveScoreToTeam called: team={}, score={}", teamId, score);
    }

    @Override
    public void joinTeam(Room room, RoomUnit unit, int teamId) {
        if (room == null || unit == null) return;

        // Team joining logic depends on active game
        debug(room, "joinTeam called: user={}, team={}", unit.getId(), teamId);
    }

    @Override
    public void leaveTeam(Room room, RoomUnit unit) {
        if (room == null || unit == null) return;

        User user = room.getUser(unit);
        if (user != null && user.getUserInfo().getGamePlayer() != null) {
            // Leave team logic
            debug(room, "leaveTeam called: user={}", unit.getId());
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
    public void botWhisperTo(Room room, String botName, RoomUnit unit, String message) {
        if (room == null || botName == null || unit == null || message == null) return;

        User user = room.getUser(unit);
        if (user != null) {
            List<Bot> bots = room.getBots(botName);
            for (Bot bot : bots) {
                user.getClient()
                        .sendResponse(new RoomUserWhisperComposer(new RoomChatMessage(
                                message, bot.getRoomUnit(), RoomChatMessageBubbles.getBubble(bot.getBubbleId()))));
            }
        }
    }

    @Override
    public void botWalkToFurni(Room room, String botName, UserItem item) {
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
    public void botFollowUser(Room room, String botName, RoomUnit unit) {
        if (room == null || botName == null || unit == null) return;

        User user = room.getUser(unit);
        if (user != null) {
            List<Bot> bots = room.getBots(botName);
            for (Bot bot : bots) {
                bot.startFollowingUser(user);
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
            room.sendComposer(new polaris.messages.outgoing.rooms.users.RoomUsersComposer(bot).compose());
        }
    }

    @Override
    public void botGiveHandItem(Room room, String botName, RoomUnit unit, int handItemId) {
        if (room == null || botName == null || unit == null) return;

        // Bot gives hand item by walking to unit and transferring
        User user = room.getUser(unit);
        if (user != null) {
            giveHandItem(room, unit, handItemId);
        }
    }

    // ========== Messaging ==========

    @Override
    public void showAlert(Room room, RoomUnit unit, String message) {
        if (room == null || unit == null || message == null) return;

        User user = room.getUser(unit);
        if (user != null) {
            user.alert(message);
        }
    }

    // ========== Room Operations ==========

    @Override
    public void resetTimers(Room room) {
        if (room == null) return;
        WiredManager.resetTimers(room);
    }
}
