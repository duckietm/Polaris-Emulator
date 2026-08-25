package com.eu.habbo.habbohotel.items.interactions.totems;

import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.HabboItem;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

public class InteractionTotemPlanet extends InteractionDefault {
    public InteractionTotemPlanet(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }

    public InteractionTotemPlanet(int id, int userId, Item item, String extradata, int limitedStack, int limitedSells) {
        super(id, userId, item, extradata, limitedStack, limitedSells);
    }

    public TotemPlanetType getPlanetType() {
        int extraData;
        try {
            extraData = Integer.parseInt(this.getExtradata());
        } catch(NumberFormatException ex) {
            extraData = 0;
        }
        return TotemPlanetType.fromInt(extraData);
    }

    @Override
    public void onClick(GameClient client, Room room, Object[] objects) throws Exception {
        if(client.getHabbo().getHabboInfo().getId() != this.getUserId()) {
            super.onClick(client, room, objects);
            return;
        }

        InteractionTotemLegs legs = null;
        InteractionTotemHead head = null;

        Set<HabboItem> items = room.getItemsAt(room.getLayout().getTile(this.getX(), this.getY()));

        for(HabboItem item : items) {
            if(item instanceof InteractionTotemLegs && item.getZ() < this.getZ())
                legs = (InteractionTotemLegs)item;
        }

        if(legs == null) {
            super.onClick(client, room, objects);
            return;
        }

        for(HabboItem item : items) {
            if(item instanceof InteractionTotemHead && item.getZ() > legs.getZ())
                head = (InteractionTotemHead)item;
        }

        if(head == null) {
            super.onClick(client, room, objects);
            return;
        }

        int effectId = 0;

        if(getPlanetType() == TotemPlanetType.SUN && head.getTotemType() == TotemType.BIRD && legs.getTotemType() == TotemType.BIRD && legs.getTotemColor() == TotemColor.RED) {
            effectId = 25;
        }
        else if(getPlanetType() == TotemPlanetType.EARTH && head.getTotemType() == TotemType.TROLL && legs.getTotemType() == TotemType.TROLL && legs.getTotemColor() == TotemColor.YELLOW) {
            effectId = 23;
        }
        else if(getPlanetType() == TotemPlanetType.EARTH && head.getTotemType() == TotemType.SNAKE && legs.getTotemType() == TotemType.BIRD && legs.getTotemColor() == TotemColor.YELLOW) {
            effectId = 26;
        }
        else if(getPlanetType() == TotemPlanetType.MOON && head.getTotemType() == TotemType.SNAKE && legs.getTotemType() == TotemType.SNAKE && legs.getTotemColor() == TotemColor.BLUE) {
            effectId = 24;
        }

        if(effectId > 0) {
            if(client.getHabbo().getInventory().getEffectsComponent().ownsEffect(effectId)) {
                client.getHabbo().getInventory().getEffectsComponent().enableEffect(effectId);
                return;
            }

            client.getHabbo().getInventory().getEffectsComponent().createEffect(effectId);
            client.getHabbo().getInventory().getEffectsComponent().enableEffect(effectId);
            return;
        }

        super.onClick(client, room, objects);
    }
}
