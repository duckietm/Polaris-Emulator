package com.eu.habbo.messages.outgoing.hotelview;

import com.eu.habbo.habbohotel.hotelview.HotelViewScene;
import com.eu.habbo.habbohotel.hotelview.HotelViewSlot;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.Outgoing;

import java.util.List;

public class HotelViewLandingComposer extends MessageComposer {
    private final boolean canEdit;
    private final HotelViewScene scene;
    private final List<HotelViewSlot> slots;

    public HotelViewLandingComposer(boolean canEdit, HotelViewScene scene, List<HotelViewSlot> slots) {
        this.canEdit = canEdit;
        this.scene = scene;
        this.slots = slots;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotelViewLandingComposer);
        this.response.appendBoolean(this.canEdit);
        this.response.appendString(this.scene.backgroundUrl());
        this.response.appendString(this.scene.leftUrl());
        this.response.appendString(this.scene.rightUrl());
        this.response.appendString(this.scene.drapeUrl());
        this.response.appendInt(this.slots.size());

        for (HotelViewSlot slot : this.slots) {
            this.response.appendInt(slot.id());
            this.response.appendBoolean(slot.enabled());
            this.response.appendString(slot.type());
            this.response.appendString(slot.title());
            this.response.appendString(slot.body());
            this.response.appendString(slot.imageUrl());
            this.response.appendString(slot.buttonText());
            this.response.appendString(slot.link());
            this.response.appendInt(slot.progress());
            this.response.appendString(slot.progressLabel());
            this.response.appendString(slot.configJson());
        }

        this.response.appendBoolean(this.scene.hallOfFameEnabled());
        this.response.appendString(this.scene.hallOfFameMode());
        this.response.appendInt(this.scene.hallOfFameCurrencyType());
        this.response.appendInt(this.scene.hallOfFameUsers().size());

        for (var user : this.scene.hallOfFameUsers()) {
            this.response.appendInt(user.id());
            this.response.appendString(user.username());
            this.response.appendString(user.figure());
            this.response.appendString(user.gender());
        }

        this.response.appendInt(this.scene.leftX());
        this.response.appendInt(this.scene.leftY());
        this.response.appendInt(this.scene.rightX());
        this.response.appendInt(this.scene.rightY());
        this.response.appendInt(this.scene.drapeX());
        this.response.appendInt(this.scene.drapeY());
        this.response.appendInt(this.scene.hallOfFameX());
        this.response.appendInt(this.scene.hallOfFameY());

        return this.response;
    }
}
