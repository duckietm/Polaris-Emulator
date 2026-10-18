package com.eu.habbo.habbohotel.wired;

/**
 * Wired trigger codes as the official AIR client numbers them (TriggerConfCodes, build
 * WIN63-202610131318); the client picks a trigger's editor by this code. Triggers the official client
 * does not have use 1000 and up, so they never land on an official one.
 */
public enum WiredTriggerType {
    SAY_SOMETHING(0),
    WALKS_ON_FURNI(1),
    WALKS_OFF_FURNI(2),
    AT_GIVEN_TIME(3),
    STATE_CHANGED(4),
    PERIODICALLY(6),
    ENTER_ROOM(7),
    GAME_STARTS(8),
    GAME_ENDS(9),
    SCORE_ACHIEVED(10),
    COLLISION(11),
    PERIODICALLY_LONG(12),
    BOT_REACHED_STF(13),
    BOT_REACHED_AVTR(14),
    LEAVE_ROOM(23),
    PERIODICALLY_SHORT(19),
    CLICKS_FURNI(18),
    CLICKS_TILE(21),
    CLICKS_USER(24),
    USER_PERFORMS_ACTION(16),
    CLOCK_COUNTER(15),
    VARIABLE_CHANGED(22),
    SAY_COMMAND(0),
    IDLES(11),
    UNIDLES(11),
    CUSTOM(13),
    STARTS_DANCING(11),
    STOPS_DANCING(11),
    RECEIVE_SIGNAL(17),
    // Phase-2 transaction outcome triggers. Each requires the matching Nitro WiredTriggerLayoutCode value.
    TRANSACTION_COMPLETE(25),
    TRANSACTION_FAIL(26),
    // New client dialogs. Each requires the matching Nitro WiredTriggerLayoutCode value.
    USER_GETS_HANDITEM(1001),
    DICE_ROLLED(1000),
    // Fired by the PressKeybindEvent packet handler (header 9311) via WiredManager.triggerKeybind.
    PRESS_KEYBIND(1002),
    // The two team-result triggers used to answer CUSTOM, which shares code 13 with
    // BOT_REACHED_STF - so the client drew the bot-reached dialog for them and asked which bot
    // had arrived. They take no settings. WiredEvent.Type maps TEAM_WINS and TEAM_LOSES here
    // too, so RoomSpecialTypes still finds them; matches() tells the two events apart.
    TEAM_GAME_RESULT(1003),
    // The long one-shot timer. It reported AT_GIVEN_TIME, so the client drew the half-second dialog
    // for a trigger that counts in five-second steps. Needs the matching Nitro WiredTriggerLayoutCode
    // value and a WiredEvent.Type whose legacy type maps back here, or the room index never finds it.
    AT_GIVEN_TIME_LONG(1004),
    // "Say your username" borrowed the keyword dialog, whose keyword and match-mode controls it
    // never reads. Its own code lets the client draw only what the box uses.
    USERNAME_AS_TRIGGER(1005),
    // The official "furni state is changed" trigger: any state update of the picked furni, whether a
    // user toggled it or a wired effect set it. STATE_CHANGED (4) stays the user-toggle-only box.
    STATE_CHANGE(20),
    // Official: fires once when the room loads, before any other trigger.
    ROOM_INITIALIZED(28),
    // Official WIRED Link triggers: a picked Link received a link message / a link dataframe.
    RECEIVE_LINK_MESSAGE(29),
    RECEIVE_LINK_DATAFRAME(30);

    public final int code;

    WiredTriggerType(int code) {
        this.code = code;
    }
}
