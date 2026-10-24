package com.eu.habbo.messages.incoming.users;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.TextsManager;
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
        TextsManager texts = Emulator.getTexts();

        try {
            gender = HabboGender.valueOf(genderCode);
        } catch (IllegalArgumentException e) {
            String message = texts.getValue("scripter.warning.look.gender")
                    .replace("%username%", this.client.getHabbo().getHabboInfo().getUsername())
                    .replace("%gender%", genderCode);
            ScripterManager.scripterDetected(this.client, message);
            LOGGER.info(message);
            return;
        }

        String look = this.packet.readString();

        // Checked even when figuredata validation is off (the default): a figure
        // longer than users.look breaks every later profile write for the session,
        // and the raw string is broadcast to the whole room.
        if (!ClothingValidationManager.isStructurallyValidFigure(look)) {
            String message = texts.getValueQuietly(
                            "scripter.warning.look.figure",
                            "%username% tried to script an invalid figure of %length% characters")
                    .replace("%username%", this.client.getHabbo().getHabboInfo().getUsername())
                    .replace("%length%", String.valueOf(look == null ? 0 : look.length()));
            ScripterManager.scripterDetected(this.client, message);
            LOGGER.info(message);
            // Re-sync the client, which already shows the look it just sent.
            this.client.sendResponse(new UpdateUserLookComposer(this.client.getHabbo()));
            return;
        }

        UserSavedLookEvent lookEvent = new UserSavedLookEvent(this.client.getHabbo(), gender, look);
        Emulator.getPluginManager().fireEvent(lookEvent);
        if (lookEvent.isCancelled()) return;

        String previousLook = this.client.getHabbo().getHabboInfo().getLook();
        this.client
                .getHabbo()
                .getHabboInfo()
                .setLook(
                        ClothingValidationManager.VALIDATE_ON_CHANGE_LOOKS
                                ? ClothingValidationManager.validateLook(
                                        this.client.getHabbo(), lookEvent.newLook, lookEvent.gender.name())
                                : lookEvent.newLook);
        this.client.getHabbo().getHabboInfo().setGender(lookEvent.gender);
        if (!java.util.Objects.equals(
                previousLook, this.client.getHabbo().getHabboInfo().getLook())) {
            com.eu.habbo.habbohotel.quests.QuestProgressEvents.progress(
                    this.client.getHabbo(), com.eu.habbo.habbohotel.quests.QuestGoalType.CHANGE_FIGURE, 1);
        }
        Emulator.getThreading().run(this.client.getHabbo().getHabboInfo());
        this.client.sendResponse(new UpdateUserLookComposer(this.client.getHabbo()));
        if (this.client.getHabbo().getHabboInfo().getCurrentRoom() != null) {
            this.client
                    .getHabbo()
                    .getHabboInfo()
                    .getCurrentRoom()
                    .sendComposer(new RoomUserDataComposer(this.client.getHabbo()).compose());
        }
        this.client
                .getHabbo()
                .getMessenger()
                .connectionChanged(
                        this.client.getHabbo(),
                        this.client.getHabbo().isOnline(),
                        this.client.getHabbo().getHabboInfo().getCurrentRoom() != null);

        AchievementManager.progressAchievement(
                this.client.getHabbo(),
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("AvatarLooks"));
    }
}
