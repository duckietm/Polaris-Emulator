package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.achievements.AchievementManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;
import polaris.messages.outgoing.users.ExtendedProfileChangedComposer;
import polaris.plugin.events.users.UserSavedMottoEvent;

public class SaveMottoEvent extends MessageHandler {
    // Each save wrote the user row and broadcast the room data, as often as the client sent it.
    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        String motto = UserInputGuard.normalizeText(
                Emulator.getGameEnvironment().getWordFilter().filter(this.packet.readString(), this.client.getUser()));
        UserSavedMottoEvent event = new UserSavedMottoEvent(
                this.client.getUser(), this.client.getUser().getUserInfo().getMotto(), motto);
        Emulator.getPluginManager().fireEvent(event);
        motto = UserInputGuard.normalizeText(event.newMotto);

        if (motto.length() > Emulator.getConfig().getInt("motto.max_length", 38)) return;

        boolean changed = !motto.equals(this.client.getUser().getUserInfo().getMotto());
        // An unchanged motto needs no write and no broadcast.
        if (!changed) return;

        this.client.getUser().getUserInfo().setMotto(motto);
        this.client.getUser().getUserInfo().run();
        if (changed) {
            polaris.hotel.quests.QuestProgressEvents.progress(
                    this.client.getUser(), polaris.hotel.quests.QuestGoalType.CHANGE_MOTTO, 1);
        }

        if (this.client.getUser().getUserInfo().getCurrentRoom() != null) {
            this.client
                    .getUser()
                    .getUserInfo()
                    .getCurrentRoom()
                    .sendComposer(new RoomUserDataComposer(this.client.getUser()).compose());
        } else {
            this.client.sendResponse(new RoomUserDataComposer(this.client.getUser()));
        }

        // A profile open on this screen still shows the old motto: tell it the profile changed.
        this.client.sendResponse(new ExtendedProfileChangedComposer(
                this.client.getUser().getUserInfo().getId()));

        AchievementManager.progressAchievement(
                this.client.getUser(),
                Emulator.getGameEnvironment().getAchievementManager().getAchievement("Motto"));
    }
}
