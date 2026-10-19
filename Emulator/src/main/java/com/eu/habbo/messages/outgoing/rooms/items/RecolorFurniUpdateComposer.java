package com.eu.habbo.messages.outgoing.rooms.items;

import com.eu.habbo.habbohotel.rooms.BuildersClubItemIds;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;
import java.util.List;

/**
 * Official recolour update (AIR 181): furni that took a new colour. Each update is the furni ids and
 * one colour, written as three shorts.
 */
public class RecolorFurniUpdateComposer extends MessageComposer {
    /** Furni ids and the packed 0xRRGGBB colour they now have. */
    public record Update(List<Integer> furniIds, int rgb) {
        public Update {
            furniIds = List.copyOf(furniIds);
        }
    }

    private final List<Update> updates;

    public RecolorFurniUpdateComposer(List<Update> updates) {
        this.updates = (updates != null) ? List.copyOf(updates) : List.of();
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.RecolorFurniUpdateComposer);
        this.response.appendInt(this.updates.size());
        for (Update update : this.updates) {
            this.response.appendInt(update.furniIds().size());
            for (int furniId : update.furniIds()) {
                this.response.appendInt(BuildersClubItemIds.toWire(furniId));
            }
            this.response.appendShort((update.rgb() >> 16) & 0xFF);
            this.response.appendShort((update.rgb() >> 8) & 0xFF);
            this.response.appendShort(update.rgb() & 0xFF);
        }
        return this.response;
    }
}
