package polaris.messages.incoming.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.core.TextsManager;
import polaris.hotel.achievements.AchievementManager;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.clothingvalidation.ClothingValidationManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;
import polaris.messages.outgoing.users.UpdateUserLookComposer;
import polaris.plugin.events.users.UserSavedLookEvent;

public class UserSaveLookEvent extends MessageHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserSaveLookEvent.class);

    @Override
    public void handle() throws Exception {
        String genderCode = this.packet.readString();
        UserGender gender;
        TextsManager texts = Emulator.getTexts();

        try {
            gender = UserGender.valueOf(genderCode);
        } catch (IllegalArgumentException e) {
            String message = texts.getValue("scripter.warning.look.gender")
                    .replace("%username%", this.client.getUser().getUserInfo().getUsername())
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
                    .replace("%username%", this.client.getUser().getUserInfo().getUsername())
                    .replace("%length%", String.valueOf(look == null ? 0 : look.length()));
            ScripterManager.scripterDetected(this.client, message);
            LOGGER.info(message);
            // Re-sync the client, which already shows the look it just sent.
            this.client.sendResponse(new UpdateUserLookComposer(this.client.getUser()));
            return;
        }

        UserSavedLookEvent lookEvent = new UserSavedLookEvent(this.client.getUser(), gender, look);
        Emulator.getPluginManager().fireEvent(lookEvent);
        if (lookEvent.isCancelled()) return;

        String previousLook = this.client.getUser().getUserInfo().getLook();
        this.client
                .getUser()
                .getUserInfo()
                .setLook(
                        ClothingValidationManager.VALIDATE_ON_CHANGE_LOOKS
                                ? ClothingValidationManager.validateLook(
                                        this.client.getUser(), lookEvent.newLook, lookEvent.gender.name())
                                : lookEvent.newLook);
        this.client.getUser().getUserInfo().setGender(lookEvent.gender);
        if (!java.util.Objects.equals(
                previousLook, this.client.getUser().getUserInfo().getLook())) {
            polaris.hotel.quests.QuestProgressEvents.progress(
                    this.client.getUser(), polaris.hotel.quests.QuestGoalType.CHANGE_FIGURE, 1);
        }
        Emulator.getThreading().run(this.client.getUser().getUserInfo());
        this.client.sendResponse(new UpdateUserLookComposer(this.client.getUser()));
        if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
            this.client
                    .getUser()
                    .getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new RoomUserDataComposer(this.client.getUser()).compose());
        }
        this.client
                .getUser()
                .getMessenger()
                .connectionChanged(
                        this.client.getUser(),
                        this.client.getUser().isOnline(),
                        this.client.getUser().getUserInfo().getCurrentRoom() != null);

        AchievementManager.progressAchievement(
                this.client.getUser(),
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("AvatarLooks"));
    }
}
