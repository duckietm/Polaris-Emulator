package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;

/**
 * Interface abstracting side effects for wired execution.
 * <p>
 * This abstraction layer provides several benefits:
 * <ul>
 *   <li>Testability - mock implementations for unit tests</li>
 *   <li>Extensibility - add queueing, RNG, signals later</li>
 *   <li>Consistency - all room mutations go through one place</li>
 *   <li>Safety - can add rate limiting, validation, etc.</li>
 * </ul>
 * </p>
 * 
 * <h3>Usage in Effects:</h3>
 * <pre>{@code
 * public void execute(WiredContext ctx) {
 *     ctx.actor().ifPresent(user -> 
 *         ctx.services().teleportUser(ctx.room(), user, targetTile));
 * }
 * }</pre>
 * 
 * @see DefaultWiredServices
 * @see WiredContext
 */
public interface WiredServices {

    // ========== Debug/Logging ==========

    /**
     * Log a debug message for wired execution tracing.
     * @param room the room context
     * @param message the message to log
     */
    void debug(Room room, String message);

    /**
     * Log a debug message with format arguments.
     * @param room the room context
     * @param format the format string
     * @param args format arguments
     */
    void debug(Room room, String format, Object... args);

    // ========== User Operations ==========

    /**
     * Teleport a user to a specific tile instantly.
     * @param room the room
     * @param user the user to teleport
     * @param tile the destination tile
     */
    void teleportUser(Room room, RoomUnit user, RoomTile tile);

    /**
     * Move a user to a specific tile (walks there).
     * @param room the room
     * @param user the user to move
     * @param tile the destination tile
     */
    void moveUser(Room room, RoomUnit user, RoomTile tile);

    /**
     * Kick a user from the room.
     * @param room the room
     * @param user the user to kick
     */
    void kickUser(Room room, RoomUnit user);

    /**
     * Give an effect to a user.
     * @param room the room
     * @param user the user
     * @param effectId the effect ID to apply
     */
    void giveEffect(Room room, RoomUnit user, int effectId);

    /**
     * Give an effect to a user for a limited duration.
     * @param room the room
     * @param user the user
     * @param effectId the effect ID
     * @param duration duration in seconds (0 = permanent)
     */
    void giveEffect(Room room, RoomUnit user, int effectId, int duration);

    /**
     * Whisper a message to a user (only they see it).
     * @param room the room
     * @param user the user
     * @param message the message to whisper
     */
    void whisperToUser(Room room, RoomUnit user, String message);

    /**
     * Give a hand item to a user.
     * @param room the room
     * @param user the user
     * @param handItemId the hand item ID
     */
    void giveHandItem(Room room, RoomUnit user, int handItemId);

    /**
     * Mute a user.
     * @param room the room
     * @param user the user
     * @param durationMinutes mute duration in minutes
     */
    void muteUser(Room room, RoomUnit user, int durationMinutes);

    // ========== Furniture Operations ==========

    /**
     * Toggle a furniture item's state.
     * @param room the room
     * @param item the item to toggle
     */
    void toggleFurni(Room room, HabboItem item);

    /**
     * Set a furniture item to a specific state.
     * @param room the room
     * @param item the item
     * @param state the state value
     */
    void setFurniState(Room room, HabboItem item, int state);

    /**
     * Move furniture to a specific tile and rotation.
     * @param room the room
     * @param item the item to move
     * @param tile the destination tile
     * @param rotation the new rotation
     */
    void moveFurni(Room room, HabboItem item, RoomTile tile, int rotation);

    /**
     * Reset furniture to its original state (from match furni).
     * @param room the room
     * @param item the item to reset
     */
    void resetFurniState(Room room, HabboItem item);

    // ========== Game Operations ==========

    /**
     * Give score to a user in a game context.
     * @param room the room
     * @param user the user
     * @param score the score to add
     */
    void giveScore(Room room, RoomUnit user, int score);

    /**
     * Give score to a team.
     * @param room the room
     * @param teamId the team ID
     * @param score the score to add
     */
    void giveScoreToTeam(Room room, int teamId, int score);

    /**
     * Add a user to a team.
     * @param room the room
     * @param user the user
     * @param teamId the team to join
     */
    void joinTeam(Room room, RoomUnit user, int teamId);

    /**
     * Remove a user from their team.
     * @param room the room
     * @param user the user
     */
    void leaveTeam(Room room, RoomUnit user);

    // ========== Bot Operations ==========

    /**
     * Make a bot say something.
     * @param room the room
     * @param botName the bot's name
     * @param message the message
     * @param shout true to shout, false to talk
     */
    void botTalk(Room room, String botName, String message, boolean shout);

    /**
     * Make a bot whisper to a user.
     * @param room the room
     * @param botName the bot's name
     * @param user the target user
     * @param message the message
     */
    void botWhisperTo(Room room, String botName, RoomUnit user, String message);

    /**
     * Make a bot walk to a furniture item.
     * @param room the room
     * @param botName the bot's name
     * @param item the target item
     */
    void botWalkToFurni(Room room, String botName, HabboItem item);

    /**
     * Make a bot teleport to a tile.
     * @param room the room
     * @param botName the bot's name
     * @param tile the destination tile
     */
    void botTeleport(Room room, String botName, RoomTile tile);

    /**
     * Make a bot follow a user.
     * @param room the room
     * @param botName the bot's name
     * @param user the user to follow
     */
    void botFollowUser(Room room, String botName, RoomUnit user);

    /**
     * Change a bot's clothes.
     * @param room the room
     * @param botName the bot's name
     * @param figure the new figure string
     */
    void botSetClothes(Room room, String botName, String figure);

    /**
     * Make a bot give a hand item to a user.
     * @param room the room
     * @param botName the bot's name
     * @param user the target user
     * @param handItemId the hand item ID
     */
    void botGiveHandItem(Room room, String botName, RoomUnit user, int handItemId);

    // ========== Messaging ==========

    /**
     * Show an alert message to a user.
     * @param room the room
     * @param user the user
     * @param message the alert message
     */
    void showAlert(Room room, RoomUnit user, String message);

    // ========== Room Operations ==========

    /**
     * Reset all wired timers in the room.
     * @param room the room
     */
    void resetTimers(Room room);
}
