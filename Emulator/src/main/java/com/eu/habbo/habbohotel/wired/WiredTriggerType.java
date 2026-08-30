package com.eu.habbo.habbohotel.wired;

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
    LEAVE_ROOM(16),
    PERIODICALLY_SHORT(17),
    CLICKS_FURNI(18),
    CLICKS_TILE(19),
    CLICKS_USER(20),
    USER_PERFORMS_ACTION(21),
    CLOCK_COUNTER(22),
    VARIABLE_CHANGED(23),
    SAY_COMMAND(0),
    IDLES(11),
    UNIDLES(11),
    CUSTOM(13),
    STARTS_DANCING(11),
    STOPS_DANCING(11),
    RECEIVE_SIGNAL(15),
    // Phase-2 transaction outcome triggers. Each requires the matching Nitro WiredTriggerLayoutCode value.
    TRANSACTION_COMPLETE(27),
    TRANSACTION_FAIL(28),
    // New client dialogs. Each requires the matching Nitro WiredTriggerLayoutCode value.
    USER_GETS_HANDITEM(25),
    DICE_ROLLED(24),
    // Fired by the PressKeybindEvent packet handler (header 9311) via WiredManager.triggerKeybind.
    PRESS_KEYBIND(26),
    // The two team-result triggers used to answer CUSTOM, which shares code 13 with
    // BOT_REACHED_STF - so the client drew the bot-reached dialog for them and asked which bot
    // had arrived. They take no settings. WiredEvent.Type maps TEAM_WINS and TEAM_LOSES here
    // too, so RoomSpecialTypes still finds them; matches() tells the two events apart.
    TEAM_GAME_RESULT(29);

    public final int code;

    WiredTriggerType(int code) {
        this.code = code;
    }
}
