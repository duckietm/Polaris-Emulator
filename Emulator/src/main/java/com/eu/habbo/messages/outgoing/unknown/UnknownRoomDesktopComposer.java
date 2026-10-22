package com.eu.habbo.messages.outgoing.unknown;

import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.Map;

/**
 * AIR's BotSkillListUpdate (header 69): int bot id (the negative room id the client knows the bot by),
 * then int count and per skill int id, string data. The client replaces the rentable bot's skills
 * with this list and refreshes its menu.
 */
public class UnknownRoomDesktopComposer extends MessageComposer {
    private final int unknownInt1;
    private final Map<Integer, String> unknownMap;

    public UnknownRoomDesktopComposer(int unknownInt1, Map<Integer, String> unknownMap) {
        this.unknownInt1 = unknownInt1;
        this.unknownMap = unknownMap;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.UnknownRoomDesktopComposer);
        this.response.appendInt(this.unknownInt1);
        this.response.appendInt(this.unknownMap.size());
        for (Map.Entry<Integer, String> entry : this.unknownMap.entrySet()) {
            this.response.appendInt(entry.getKey());
            this.response.appendString(entry.getValue());
        }
        return this.response;
    }

    public int getUnknownInt1() {
        return unknownInt1;
    }

    public Map<Integer, String> getUnknownMap() {
        return unknownMap;
    }
}
