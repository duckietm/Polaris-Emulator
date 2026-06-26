package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.clothingvalidation.ClothingValidationManager;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UpdateUserLookComposer;
import com.eu.habbo.plugin.events.users.UserSavedLookEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserSaveLookEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserSaveLookEvent.class);

    @Override
    public void handle() throws Exception {
        String genderCode = this.packet.readString();
        HabboGender gender;

        try {
            gender = HabboGender.valueOf(genderCode);
        } catch (IllegalArgumentException e) {
            String message = Emulator.getTexts().getValue("scripter.warning.look.gender").replace("%username%", this.client.getHabbo().getHabboInfo().getUsername()).replace("%gender%", genderCode);
            ScripterManager.scripterDetected(this.client, message);
            LOGGER.info(message);
            return;
        }

        String look = this.packet.readString();

        var lookEvent = new UserSavedLookEvent(this.client.getHabbo(), gender, look);
        Emulator.getPluginManager().fireEvent(lookEvent);
        if (lookEvent.isCancelled())
            return;

        this.client.getHabbo().getHabboInfo().setLook(ClothingValidationManager.VALIDATE_ON_CHANGE_LOOKS ? ClothingValidationManager.validateLook(this.client.getHabbo(), lookEvent.newLook, lookEvent.gender.name()) : lookEvent.newLook);
        this.client.getHabbo().getHabboInfo().setGender(lookEvent.gender);
        Emulator.getThreading().run(this.client.getHabbo().getHabboInfo());
        this.client.sendResponse(new UpdateUserLookComposer(this.client.getHabbo()));
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            this.client.getHabbo().getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(this.client.getHabbo()).compose());
        }
        this.client.getHabbo().getMessenger().connectionChanged(
                this.client.getHabbo(),
                this.client.getHabbo().isOnline(),
                this.client.getHabbo().getHabboInfo().getCurrentRoom() != null
        );

        AchievementManager.progressAchievement(this.client.getHabbo(), Emulator.getGameEnvironment().getAchievementManager().getAchievement("AvatarLooks"));
    }
}
