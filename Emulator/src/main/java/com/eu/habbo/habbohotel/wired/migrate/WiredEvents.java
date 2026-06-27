package com.eu.habbo.habbohotel.wired.migrate;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;
import com.eu.habbo.habbohotel.wired.core.WiredEvent;

/**
 * Factory methods for creating {@link WiredEvent} instances from various room events.
 * <p>
 * This class provides convenient builder methods for creating events that can be
 * passed to the {@link com.eu.habbo.habbohotel.wired.core.WiredEngine}.
 * </p>
 * 
 * <h3>Usage:</h3>
 * <pre>{@code
 * // When user walks on furniture
 * WiredEvent event = WiredEvents.userWalksOn(room, user, steppedItem);
 * engine.handleEvent(event);
 * 
 * // When user says something
 * WiredEvent event = WiredEvents.userSays(room, user, message);
 * engine.handleEvent(event);
 * }</pre>
 * 
 * @see WiredEvent
 */
public final class WiredEvents {
    
    private WiredEvents() {
        // Static utility class
    }

    // ========== User Movement Events ==========

    /**
     * Create an event for when a user walks onto furniture.
     * @param room the room
     * @param user the user who walked
     * @param item the furniture walked onto
     * @return the event
     */
    public static WiredEvent userWalksOn(Room room, RoomUnit user, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.USER_WALKS_ON, room)
                .actor(user)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    /**
     * Create an event for when a user walks off furniture.
     * @param room the room
     * @param user the user who walked
     * @param item the furniture walked off of
     * @return the event
     */
    public static WiredEvent userWalksOff(Room room, RoomUnit user, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.USER_WALKS_OFF, room)
                .actor(user)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    /**
     * Create an event for when a user clicks furniture.
     * @param room the room
     * @param user the clicking user
     * @param item the clicked furniture
     * @return the event
     */
    public static WiredEvent userClicksFurni(Room room, RoomUnit user, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.USER_CLICKS_FURNI, room)
                .actor(user)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    /**
     * Create an event for when a user clicks invisible click tile furniture.
     * @param room the room
     * @param user the clicking user
     * @param item the clicked furniture
     * @return the event
     */
    public static WiredEvent userClicksTile(Room room, RoomUnit user, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.USER_CLICKS_TILE, room)
                .actor(user)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    /**
     * Create an event for when a user clicks another user.
     * @param room the room
     * @param clickingUser the user performing the click
     * @param clickedUser the user who was clicked
     * @return the event
     */
    public static WiredEvent userClicksUser(Room room, RoomUnit clickingUser, RoomUnit clickedUser) {
        return WiredEvent.builder(WiredEvent.Type.USER_CLICKS_USER, room)
                .actor(clickingUser)
                .targetUnit(clickedUser)
                .tile(clickedUser.getCurrentLocation())
                .build();
    }

    /**
     * Create an event for when a user performs an avatar action.
     * @param room the room
     * @param user the acting user
     * @param actionId the wired action id
     * @param actionParameter sign/dance parameter, or -1 when unused
     * @return the event
     */
    public static WiredEvent userPerformsAction(Room room, RoomUnit user, int actionId, int actionParameter) {
        return WiredEvent.builder(WiredEvent.Type.USER_PERFORMS_ACTION, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .actionId(actionId)
                .actionParameter(actionParameter)
                .build();
    }

    /**
     * Create an event for when a user enters the room.
     * @param room the room
     * @param user the user who entered
     * @return the event
     */
    public static WiredEvent userEntersRoom(Room room, RoomUnit user) {
        return WiredEvent.builder(WiredEvent.Type.USER_ENTERS_ROOM, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .build();
    }

    /**
     * Create an event for when a user leaves the room.
     * @param room the room
     * @param user the user who left
     * @return the event
     */
    public static WiredEvent userLeavesRoom(Room room, RoomUnit user) {
        return WiredEvent.builder(WiredEvent.Type.USER_LEAVES_ROOM, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .build();
    }

    // ========== User Interaction Events ==========

    /**
     * Create an event for when a user says something.
     * @param room the room
     * @param user the user who spoke
     * @param message the message said
     * @return the event
     */
    public static WiredEvent userSays(Room room, RoomUnit user, String message) {
        return userSays(room, user, message, -1, -1);
    }

    public static WiredEvent userSays(Room room, RoomUnit user, String message, int chatType, int chatStyle) {
        return WiredEvent.builder(WiredEvent.Type.USER_SAYS, room)
                .actor(user)
                .text(message)
                .chatType(chatType)
                .chatStyle(chatStyle)
                .tile(user.getCurrentLocation())
                .build();
    }

    // ========== Furniture Events ==========

    /**
     * Create an event for when furniture state is toggled/changed.
     * @param room the room
     * @param user the user who changed it (may be null for automatic changes)
     * @param item the furniture that changed
     * @return the event
     */
    public static WiredEvent furniStateChanged(Room room, RoomUnit user, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.FURNI_STATE_CHANGED, room)
                .actor(user)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    public static WiredEvent userVariableChanged(Room room, RoomUnit user, int definitionItemId, boolean created, boolean deleted, WiredEvent.VariableChangeKind changeKind) {
        return WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .actor(user)
                .tile((user != null) ? user.getCurrentLocation() : null)
                .variableTargetType(0)
                .variableDefinitionItemId(definitionItemId)
                .variableCreated(created)
                .variableDeleted(deleted)
                .variableChangeKind(changeKind)
                .build();
    }

    public static WiredEvent furniVariableChanged(Room room, HabboItem item, int definitionItemId, boolean created, boolean deleted, WiredEvent.VariableChangeKind changeKind) {
        RoomTile tile = (item != null) ? room.getLayout().getTile(item.getX(), item.getY()) : null;

        return WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .sourceItem(item)
                .tile(tile)
                .variableTargetType(1)
                .variableDefinitionItemId(definitionItemId)
                .variableCreated(created)
                .variableDeleted(deleted)
                .variableChangeKind(changeKind)
                .build();
    }

    public static WiredEvent roomVariableChanged(Room room, int definitionItemId, WiredEvent.VariableChangeKind changeKind) {
        return WiredEvent.builder(WiredEvent.Type.VARIABLE_CHANGED, room)
                .variableTargetType(3)
                .variableDefinitionItemId(definitionItemId)
                .variableCreated(false)
                .variableDeleted(false)
                .variableChangeKind(changeKind)
                .build();
    }

    // ========== Timer Events ==========

    /**
     * Create an event for a timer tick (AT_GIVEN_TIME).
     * @param room the room
     * @param timerItem the timer furniture
     * @return the event
     */
    public static WiredEvent timerTick(Room room, HabboItem timerItem) {
        return WiredEvent.builder(WiredEvent.Type.TIMER_TICK, room)
                .sourceItem(timerItem)
                .build();
    }

    /**
     * Create an event for a periodic timer (PERIODICALLY).
     * @param room the room
     * @param timerItem the timer furniture
     * @return the event
     */
    public static WiredEvent timerRepeat(Room room, HabboItem timerItem) {
        return WiredEvent.builder(WiredEvent.Type.TIMER_REPEAT, room)
                .sourceItem(timerItem)
                .build();
    }

    public static WiredEvent clockCounter(Room room, HabboItem counterItem) {
        return WiredEvent.builder(WiredEvent.Type.CLOCK_COUNTER_REACHED, room)
                .sourceItem(counterItem)
                .build();
    }

    /**
     * Create an event for a long periodic timer.
     * @param room the room
     * @param timerItem the timer furniture
     * @return the event
     */
    public static WiredEvent timerRepeatLong(Room room, HabboItem timerItem) {
        return WiredEvent.builder(WiredEvent.Type.TIMER_REPEAT_LONG, room)
                .sourceItem(timerItem)
                .build();
    }

    /**
     * Create an event for a short periodic timer.
     * @param room the room
     * @param timerItem the timer furniture
     * @return the event
     */
    public static WiredEvent timerRepeatShort(Room room, HabboItem timerItem) {
        return WiredEvent.builder(WiredEvent.Type.TIMER_REPEAT_SHORT, room)
                .sourceItem(timerItem)
                .build();
    }

    // ========== Game Events ==========

    /**
     * Create an event for when a game starts.
     * @param room the room
     * @return the event
     */
    public static WiredEvent gameStarts(Room room) {
        return WiredEvent.builder(WiredEvent.Type.GAME_STARTS, room)
                .build();
    }

    /**
     * Create an event for when a game ends.
     * @param room the room
     * @return the event
     */
    public static WiredEvent gameEnds(Room room) {
        return WiredEvent.builder(WiredEvent.Type.GAME_ENDS, room)
                .build();
    }

    /**
     * Create an event for when a team wins a game.
     * @param room the room
     * @param user the user on the winning team
     * @return the event
     */
    public static WiredEvent teamWins(Room room, RoomUnit user) {
        return WiredEvent.builder(WiredEvent.Type.TEAM_WINS, room)
                .actor(user)
                .build();
    }

    /**
     * Create an event for when a team loses a game.
     * @param room the room
     * @param user the user on the losing team
     * @return the event
     */
    public static WiredEvent teamLoses(Room room, RoomUnit user) {
        return WiredEvent.builder(WiredEvent.Type.TEAM_LOSES, room)
                .actor(user)
                .build();
    }

    /**
     * Create an event for when a score threshold is achieved.
     * @param room the room
     * @param user the user who achieved the score
     * @param score the current total score
     * @param scoreAdded the amount of score just added
     * @return the event
     */
    public static WiredEvent scoreAchieved(Room room, RoomUnit user, int score, int scoreAdded) {
        return WiredEvent.builder(WiredEvent.Type.SCORE_ACHIEVED, room)
                .actor(user)
                .score(score)
                .scoreAdded(scoreAdded)
                .build();
    }

    // ========== Bot Events ==========

    /**
     * Create an event for a bot collision.
     * @param room the room
     * @param botUnit the bot's room unit
     * @return the event
     */
    public static WiredEvent botCollision(Room room, RoomUnit botUnit) {
        return WiredEvent.builder(WiredEvent.Type.BOT_COLLISION, room)
                .actor(botUnit)
                .tile(botUnit.getCurrentLocation())
                .build();
    }

    /**
     * Create an event for when a bot reaches furniture.
     * @param room the room
     * @param botUnit the bot's room unit
     * @param item the furniture reached
     * @return the event
     */
    public static WiredEvent botReachedFurni(Room room, RoomUnit botUnit, HabboItem item) {
        RoomTile tile = room.getLayout().getTile(item.getX(), item.getY());
        return WiredEvent.builder(WiredEvent.Type.BOT_REACHED_FURNI, room)
                .actor(botUnit)
                .sourceItem(item)
                .tile(tile)
                .build();
    }

    /**
     * Create an event for when a bot reaches a habbo.
     * @param room the room
     * @param botUnit the bot's room unit
     * @param targetUser the habbo reached
     * @return the event
     */
    public static WiredEvent botReachedHabbo(Room room, RoomUnit botUnit, RoomUnit targetUser) {
        return WiredEvent.builder(WiredEvent.Type.BOT_REACHED_HABBO, room)
                .actor(botUnit)
                .tile(targetUser.getCurrentLocation())
                .targetUnit(targetUser)
                .build();
    }

    // ========== User State Events ==========

    /**
     * Create an event for when a user starts idling.
     * @param room the room
     * @param user the user who started idling
     * @return the event
     */
    public static WiredEvent userIdles(Room room, RoomUnit user) {
        return WiredEvent.builder(WiredEvent.Type.USER_IDLES, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .build();
    }

    /**
     * Create an event for when a user stops idling.
     * @param room the room
     * @param user the user who stopped idling
     * @return the event
     */
    public static WiredEvent userUnidles(Room room, RoomUnit user) {
        return WiredEvent.builder(WiredEvent.Type.USER_UNIDLES, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .build();
    }

    /**
     * Create an event for when a user starts dancing.
     * @param room the room
     * @param user the user who started dancing
     * @return the event
     */
    public static WiredEvent userStartsDancing(Room room, RoomUnit user) {
        return WiredEvent.builder(WiredEvent.Type.USER_STARTS_DANCING, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .build();
    }

    /**
     * Create an event for when a user stops dancing.
     * @param room the room
     * @param user the user who stopped dancing
     * @return the event
     */
    public static WiredEvent userStopsDancing(Room room, RoomUnit user) {
        return WiredEvent.builder(WiredEvent.Type.USER_STOPS_DANCING, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .build();
    }

    /**
     * Create an event for when a user receives a hand item.
     */
    public static WiredEvent userGetsHandItem(Room room, RoomUnit user) {
        return WiredEvent.builder(WiredEvent.Type.USER_GETS_HANDITEM, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .build();
    }

    /**
     * Create an event for when a dice furni is rolled (its value finalized).
     */
    public static WiredEvent diceRolled(Room room, HabboItem dice) {
        RoomTile tile = room.getLayout().getTile(dice.getX(), dice.getY());
        return WiredEvent.builder(WiredEvent.Type.DICE_ROLLED, room)
                .sourceItem(dice)
                .tile(tile)
                .build();
    }

    /**
     * Create an event for when a user presses a configured keybind key (key code on actionParameter).
     */
    public static WiredEvent pressKeybind(Room room, RoomUnit user, int keyCode) {
        return WiredEvent.builder(WiredEvent.Type.PRESS_KEYBIND, room)
                .actor(user)
                .tile(user.getCurrentLocation())
                .actionParameter(keyCode)
                .build();
    }

    // ========== Legacy Compatibility ==========

    /**
     * Create an event from legacy trigger type and parameters.
     * This is for backwards compatibility during migration.
     * 
     * @param triggerType the legacy trigger type
     * @param room the room
     * @param roomUnit the triggering unit (may be null)
     * @param stuff legacy stuff array (now only used to extract typed data)
     * @return the event
     */
    public static WiredEvent fromLegacy(WiredTriggerType triggerType, Room room, RoomUnit roomUnit, Object[] stuff) {
        WiredEvent.Type eventType = WiredEvent.Type.fromLegacyType(triggerType);
        
        WiredEvent.Builder builder = WiredEvent.builder(eventType, room)
                .actor(roomUnit);
        
        // Try to extract common data from stuff array
        if (stuff != null) {
            for (Object obj : stuff) {
                if (obj instanceof HabboItem habboItem) {
                    builder.sourceItem(habboItem);
                } else if (obj instanceof RoomTile roomTile) {
                    builder.tile(roomTile);
                } else if (obj instanceof String str) {
                    builder.text(str);
                }
            }
        }
        
        // Add current tile from room unit if available
        if (roomUnit != null && roomUnit.getCurrentLocation() != null) {
            builder.tile(roomUnit.getCurrentLocation());
        }
        
        return builder.build();
    }
}
