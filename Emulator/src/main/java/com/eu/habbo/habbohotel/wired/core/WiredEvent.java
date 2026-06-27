package com.eu.habbo.habbohotel.wired.core;

import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.wired.WiredTriggerType;

import java.util.Optional;

/**
 * Immutable event representing what happened in the room that triggered wired execution.
 * <p>
 * This replaces the scattered {@code Object[] stuff} parameter pattern with a strongly-typed,
 * immutable event object. Triggers produce/receive this event.
 * </p>
 * 
 * <h3>Usage Example:</h3>
 * <pre>{@code
 * WiredEvent event = WiredEvent.builder(WiredEvent.Type.USER_WALKS_ON, room)
 *     .actor(roomUnit)
 *     .sourceItem(steppedItem)
 *     .tile(roomUnit.getCurrentLocation())
 *     .build();
 * }</pre>
 * 
 * @see WiredContext
 * @see WiredEngine
 */
public final class WiredEvent {

    /**
     * Types of wired events that can occur in a room.
     * Maps to the legacy {@link WiredTriggerType} but with cleaner naming.
     */
    public enum Type {
        /** User says something in chat */
        USER_SAYS(WiredTriggerType.SAY_SOMETHING),
        
        /** User walks onto furniture */
        USER_WALKS_ON(WiredTriggerType.WALKS_ON_FURNI),
        
        /** User walks off furniture */
        USER_WALKS_OFF(WiredTriggerType.WALKS_OFF_FURNI),

        /** User clicks furniture */
        USER_CLICKS_FURNI(WiredTriggerType.CLICKS_FURNI),

        /** User clicks invisible click tile furniture */
        USER_CLICKS_TILE(WiredTriggerType.CLICKS_TILE),

        /** User clicks another user */
        USER_CLICKS_USER(WiredTriggerType.CLICKS_USER),

        /** User performs an avatar action */
        USER_PERFORMS_ACTION(WiredTriggerType.USER_PERFORMS_ACTION),
        
        /** Furniture state is toggled/changed */
        FURNI_STATE_CHANGED(WiredTriggerType.STATE_CHANGED),
        
        /** Timer fires at a given time */
        TIMER_TICK(WiredTriggerType.AT_GIVEN_TIME),
        
        /** Timer fires periodically/repeatedly */
        TIMER_REPEAT(WiredTriggerType.PERIODICALLY),

        /** Up-counter reaches a configured elapsed time */
        CLOCK_COUNTER_REACHED(WiredTriggerType.CLOCK_COUNTER),

        /** A user, furni or global variable changed */
        VARIABLE_CHANGED(WiredTriggerType.VARIABLE_CHANGED),
        
        /** Long timer repeat */
        TIMER_REPEAT_LONG(WiredTriggerType.PERIODICALLY_LONG),

        /** Short timer repeat */
        TIMER_REPEAT_SHORT(WiredTriggerType.PERIODICALLY_SHORT),
        
        /** User enters the room */
        USER_ENTERS_ROOM(WiredTriggerType.ENTER_ROOM),

        /** User leaves the room */
        USER_LEAVES_ROOM(WiredTriggerType.LEAVE_ROOM),
        
        /** Game starts */
        GAME_STARTS(WiredTriggerType.GAME_STARTS),
        
        /** Game ends */
        GAME_ENDS(WiredTriggerType.GAME_ENDS),
        
        /** Bot collision */
        BOT_COLLISION(WiredTriggerType.COLLISION),
        
        /** Bot reached furniture (STF = stack tile furni) */
        BOT_REACHED_FURNI(WiredTriggerType.BOT_REACHED_STF),
        
        /** Bot reached habbo (AVTR = avatar) */
        BOT_REACHED_HABBO(WiredTriggerType.BOT_REACHED_AVTR),
        
        /** Score threshold achieved */
        SCORE_ACHIEVED(WiredTriggerType.SCORE_ACHIEVED),
        
        /** User starts idling */
        USER_IDLES(WiredTriggerType.IDLES),
        
        /** User stops idling */
        USER_UNIDLES(WiredTriggerType.UNIDLES),
        
        /** User starts dancing */
        USER_STARTS_DANCING(WiredTriggerType.STARTS_DANCING),
        
        /** User stops dancing */
        USER_STOPS_DANCING(WiredTriggerType.STOPS_DANCING),
        
        /** Team wins a game */
        TEAM_WINS(WiredTriggerType.CUSTOM),
        
        /** Team loses a game */
        TEAM_LOSES(WiredTriggerType.CUSTOM),
        
        /** Signal received from a Send Signal effect */
        SIGNAL_RECEIVED(WiredTriggerType.RECEIVE_SIGNAL),

        /** User receives a hand item */
        USER_GETS_HANDITEM(WiredTriggerType.USER_GETS_HANDITEM),

        /** A dice furni was rolled */
        DICE_ROLLED(WiredTriggerType.DICE_ROLLED),

        /** A user pressed a configured keybind key */
        PRESS_KEYBIND(WiredTriggerType.PRESS_KEYBIND),

        /** Custom trigger type for plugins */
        CUSTOM(WiredTriggerType.CUSTOM);

        private final WiredTriggerType legacyType;

        Type(WiredTriggerType legacyType) {
            this.legacyType = legacyType;
        }

        /**
         * Get the legacy trigger type for backwards compatibility.
         * @return the corresponding {@link WiredTriggerType}
         */
        public WiredTriggerType toLegacyType() {
            return legacyType;
        }

        /**
         * Convert from legacy trigger type to new event type.
         * @param legacyType the legacy trigger type
         * @return the corresponding event type
         */
        public static Type fromLegacyType(WiredTriggerType legacyType) {
            for (Type type : values()) {
                if (type.legacyType == legacyType) {
                    return type;
                }
            }
            return CUSTOM;
        }
    }

    public enum VariableChangeKind {
        NONE,
        INCREASED,
        DECREASED,
        UNCHANGED
    }

    private final Type type;
    private final Room room;
    private final RoomUnit actor;       // nullable - the user/bot that caused the event
    private final RoomUnit originActor; // nullable - original user that started the chain, preserved across signals
    private final HabboItem sourceItem; // nullable - the furniture involved
    private final RoomTile tile;        // nullable - the tile where event occurred
    private final String text;          // nullable - text for say triggers
    private final RoomUnit targetUnit;  // nullable - target user (e.g., for bot reached habbo)
    private final int score;            // score value for score achieved events
    private final int scoreAdded;       // amount added for score achieved events
    private final boolean triggeredByEffect; // true if triggered by a wired effect (to prevent loops)
    private final int callStackDepth;   // recursion depth for trigger stacks effect
    private final int signalChannel;    // channel for signal routing (0-based)
    private final int actionId;         // user action id for USER_PERFORMS_ACTION
    private final int actionParameter;  // sign/dance parameter when relevant
    private final int chatType;         // RoomChatType metadata for USER_SAYS
    private final int chatStyle;        // bubble style for USER_SAYS
    private final int signalUserCount;  // forwarded users in SIGNAL_RECEIVED
    private final int signalFurniCount; // forwarded furni in SIGNAL_RECEIVED
    private final int variableTargetType;
    private final int variableDefinitionItemId;
    private final boolean variableCreated;
    private final boolean variableDeleted;
    private final VariableChangeKind variableChangeKind;
    private final WiredContextVariableScope contextVariableScope;
    private final long createdAtMs;

    private WiredEvent(Builder builder) {
        this.type = builder.type;
        this.room = builder.room;
        this.actor = builder.actor;
        this.sourceItem = builder.sourceItem;
        this.originActor = builder.originActor;
        this.tile = builder.tile;
        this.text = builder.text;
        this.targetUnit = builder.targetUnit;
        this.score = builder.score;
        this.scoreAdded = builder.scoreAdded;
        this.triggeredByEffect = builder.triggeredByEffect;
        this.callStackDepth = builder.callStackDepth;
        this.signalChannel = builder.signalChannel;
        this.actionId = builder.actionId;
        this.actionParameter = builder.actionParameter;
        this.chatType = builder.chatType;
        this.chatStyle = builder.chatStyle;
        this.signalUserCount = builder.signalUserCount;
        this.signalFurniCount = builder.signalFurniCount;
        this.variableTargetType = builder.variableTargetType;
        this.variableDefinitionItemId = builder.variableDefinitionItemId;
        this.variableCreated = builder.variableCreated;
        this.variableDeleted = builder.variableDeleted;
        this.variableChangeKind = builder.variableChangeKind;
        this.contextVariableScope = builder.contextVariableScope;
        this.createdAtMs = builder.createdAtMs;
    }

    // Getters

    /**
     * Get the type of this event.
     * @return the event type
     */
    public Type getType() {
        return type;
    }

    /**
     * Get the room where this event occurred.
     * @return the room (never null)
     */
    public Room getRoom() {
        return room;
    }

    /**
     * Get the actor (user) that caused this event.
     * @return optional containing the actor, or empty if no user involved
     */
    public Optional<RoomUnit> getActor() {
        return Optional.ofNullable(actor);
    }

    /**
     * Get the original actor that started the current event chain.
     * For signal events this can differ from {@link #getActor()} when the
     * signal forwards users one-by-one but still needs to preserve who
     * originally triggered the chain.
     * @return optional containing the original actor, or empty if unavailable
     */
    public Optional<RoomUnit> getOriginActor() {
        return Optional.ofNullable(originActor);
    }

    /**
     * Get the source item that was involved in this event.
     * For example, the furniture that was clicked or stepped on.
     * @return optional containing the source item, or empty if no item involved
     */
    public Optional<HabboItem> getSourceItem() {
        return Optional.ofNullable(sourceItem);
    }

    /**
     * Get the tile where this event occurred.
     * @return optional containing the tile, or empty if no specific tile
     */
    public Optional<RoomTile> getTile() {
        return Optional.ofNullable(tile);
    }

    /**
     * Get the text associated with this event.
     * For example, the message in a SAY_SOMETHING trigger.
     * @return optional containing the text, or empty if no text
     */
    public Optional<String> getText() {
        return Optional.ofNullable(text);
    }

    /**
     * Get the target unit for this event.
     * Used for events like BOT_REACHED_HABBO where we need to track the target user.
     * @return optional containing the target unit
     */
    public Optional<RoomUnit> getTargetUnit() {
        return Optional.ofNullable(targetUnit);
    }

    /**
     * Get the score value for score achieved events.
     * @return the current score
     */
    public int getScore() {
        return score;
    }

    /**
     * Get the amount of score added for score achieved events.
     * @return the amount added
     */
    public int getScoreAdded() {
        return scoreAdded;
    }

    /**
     * Check if this event was triggered by a wired effect.
     * Used to prevent infinite loops (e.g., effect toggles furni -> triggers state changed -> triggers effect).
     * @return true if triggered by a wired effect
     */
    public boolean isTriggeredByEffect() {
        return triggeredByEffect;
    }

    /**
     * Get the call stack depth for recursion tracking.
     * Used by WiredEffectTriggerStacks to prevent infinite recursion.
     * @return the current recursion depth
     */
    public int getCallStackDepth() {
        return callStackDepth;
    }

    public int getSignalChannel() {
        return signalChannel;
    }

    public int getActionId() {
        return actionId;
    }

    public int getActionParameter() {
        return actionParameter;
    }

    public int getChatType() {
        return chatType;
    }

    public int getChatStyle() {
        return chatStyle;
    }

    public int getSignalUserCount() {
        return signalUserCount;
    }

    public int getSignalFurniCount() {
        return signalFurniCount;
    }

    public int getVariableTargetType() {
        return variableTargetType;
    }

    public int getVariableDefinitionItemId() {
        return variableDefinitionItemId;
    }

    public boolean isVariableCreated() {
        return variableCreated;
    }

    public boolean isVariableDeleted() {
        return variableDeleted;
    }

    public VariableChangeKind getVariableChangeKind() {
        return variableChangeKind;
    }

    public WiredContextVariableScope getContextVariableScope() {
        return contextVariableScope;
    }

    /**
     * Get the timestamp when this event was created.
     * @return milliseconds since epoch
     */
    public long getCreatedAtMs() {
        return createdAtMs;
    }

    /**
     * Create a new builder for constructing events.
     * @param type the event type (required)
     * @param room the room where event occurred (required)
     * @return a new builder instance
     */
    public static Builder builder(Type type, Room room) {
        return new Builder(type, room);
    }

    /**
     * Create a new builder from a legacy trigger type.
     * @param legacyType the legacy trigger type
     * @param room the room where event occurred
     * @return a new builder instance
     */
    public static Builder fromLegacy(WiredTriggerType legacyType, Room room) {
        return new Builder(Type.fromLegacyType(legacyType), room);
    }

    @Override
    public String toString() {
        return "WiredEvent{" +
                "type=" + type +
                ", room=" + (room != null ? room.getId() : "null") +
                ", actor=" + (actor != null ? actor.getId() : "null") +
                ", sourceItem=" + (sourceItem != null ? sourceItem.getId() : "null") +
                ", createdAtMs=" + createdAtMs +
                '}';
    }

    /**
     * Builder for constructing immutable WiredEvent instances.
     */
    public static final class Builder {
        private final Type type;
        private final Room room;
        private RoomUnit actor;
        private RoomUnit originActor;
        private HabboItem sourceItem;
        private RoomTile tile;
        private String text;
        private RoomUnit targetUnit;
        private int score;
        private int scoreAdded;
        private boolean triggeredByEffect;
        private int callStackDepth;
        private int signalChannel;
        private int actionId;
        private int actionParameter = -1;
        private int chatType = -1;
        private int chatStyle = -1;
        private int signalUserCount;
        private int signalFurniCount;
        private int variableTargetType = -1;
        private int variableDefinitionItemId;
        private boolean variableCreated;
        private boolean variableDeleted;
        private VariableChangeKind variableChangeKind = VariableChangeKind.NONE;
        private WiredContextVariableScope contextVariableScope;
        private long createdAtMs = System.currentTimeMillis();

        private Builder(Type type, Room room) {
            if (type == null) throw new IllegalArgumentException("Event type cannot be null");
            if (room == null) throw new IllegalArgumentException("Room cannot be null");
            this.type = type;
            this.room = room;
        }

        /**
         * Set the actor (user) that caused this event.
         * @param actor the room unit
         * @return this builder
         */
        public Builder actor(RoomUnit actor) {
            this.actor = actor;
            return this;
        }

        public Builder originActor(RoomUnit originActor) {
            this.originActor = originActor;
            return this;
        }

        /**
         * Set the source item involved in this event.
         * @param sourceItem the habbo item
         * @return this builder
         */
        public Builder sourceItem(HabboItem sourceItem) {
            this.sourceItem = sourceItem;
            return this;
        }

        /**
         * Set the tile where this event occurred.
         * @param tile the room tile
         * @return this builder
         */
        public Builder tile(RoomTile tile) {
            this.tile = tile;
            return this;
        }

        /**
         * Set the text associated with this event.
         * @param text the text content
         * @return this builder
         */
        public Builder text(String text) {
            this.text = text;
            return this;
        }

        /**
         * Set the target unit for this event.
         * @param targetUnit the target room unit
         * @return this builder
         */
        public Builder targetUnit(RoomUnit targetUnit) {
            this.targetUnit = targetUnit;
            return this;
        }

        /**
         * Set the score value for score achieved events.
         * @param score the current score
         * @return this builder
         */
        public Builder score(int score) {
            this.score = score;
            return this;
        }

        /**
         * Set the amount of score added for score achieved events.
         * @param scoreAdded the amount added
         * @return this builder
         */
        public Builder scoreAdded(int scoreAdded) {
            this.scoreAdded = scoreAdded;
            return this;
        }

        /**
         * Mark this event as triggered by a wired effect.
         * @param triggeredByEffect true if triggered by effect
         * @return this builder
         */
        public Builder triggeredByEffect(boolean triggeredByEffect) {
            this.triggeredByEffect = triggeredByEffect;
            return this;
        }

        /**
         * Set the call stack depth for recursion tracking.
         * @param callStackDepth the recursion depth
         * @return this builder
         */
        public Builder callStackDepth(int callStackDepth) {
            this.callStackDepth = callStackDepth;
            return this;
        }

        public Builder signalChannel(int signalChannel) {
            this.signalChannel = signalChannel;
            return this;
        }

        public Builder actionId(int actionId) {
            this.actionId = actionId;
            return this;
        }

        public Builder actionParameter(int actionParameter) {
            this.actionParameter = actionParameter;
            return this;
        }

        public Builder chatType(int chatType) {
            this.chatType = chatType;
            return this;
        }

        public Builder chatStyle(int chatStyle) {
            this.chatStyle = chatStyle;
            return this;
        }

        public Builder signalUserCount(int signalUserCount) {
            this.signalUserCount = Math.max(0, signalUserCount);
            return this;
        }

        public Builder signalFurniCount(int signalFurniCount) {
            this.signalFurniCount = Math.max(0, signalFurniCount);
            return this;
        }

        public Builder variableTargetType(int variableTargetType) {
            this.variableTargetType = variableTargetType;
            return this;
        }

        public Builder variableDefinitionItemId(int variableDefinitionItemId) {
            this.variableDefinitionItemId = Math.max(0, variableDefinitionItemId);
            return this;
        }

        public Builder variableCreated(boolean variableCreated) {
            this.variableCreated = variableCreated;
            return this;
        }

        public Builder variableDeleted(boolean variableDeleted) {
            this.variableDeleted = variableDeleted;
            return this;
        }

        public Builder variableChangeKind(VariableChangeKind variableChangeKind) {
            this.variableChangeKind = (variableChangeKind != null) ? variableChangeKind : VariableChangeKind.NONE;
            return this;
        }

        public Builder contextVariableScope(WiredContextVariableScope contextVariableScope) {
            this.contextVariableScope = contextVariableScope;
            return this;
        }

        /**
         * Set a custom creation timestamp.
         * @param createdAtMs milliseconds since epoch
         * @return this builder
         */
        public Builder createdAtMs(long createdAtMs) {
            this.createdAtMs = createdAtMs;
            return this;
        }

        /**
         * Build the immutable WiredEvent.
         * @return the constructed event
         */
        public WiredEvent build() {
            return new WiredEvent(this);
        }
    }
}
