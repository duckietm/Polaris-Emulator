package polaris.messages.outgoing.treasurehunt;

import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * AIR 13 TreasureHuntFail: the find was refused because the player is below the
 * level the hunt asks for ({@code treasure_hunt.level_fail.desc} shows both the
 * regular and the Habbo Club threshold).
 */
public class TreasureHuntFailComposer extends MessageComposer {
    private final String huntCode;
    private final int requiredLevel;
    private final int requiredLevelPaying;

    public TreasureHuntFailComposer(String huntCode, int requiredLevel, int requiredLevelPaying) {
        this.huntCode = huntCode;
        this.requiredLevel = requiredLevel;
        this.requiredLevelPaying = requiredLevelPaying;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.TreasureHuntFailComposer);
        this.response.appendString(this.huntCode);
        this.response.appendInt(this.requiredLevel);
        this.response.appendInt(this.requiredLevelPaying);
        return this.response;
    }
}
