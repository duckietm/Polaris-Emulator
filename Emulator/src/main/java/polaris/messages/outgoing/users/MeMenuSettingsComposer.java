package polaris.messages.outgoing.users;

import polaris.hotel.users.User;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class MeMenuSettingsComposer extends MessageComposer {
    private final User user;

    public MeMenuSettingsComposer(User user) {
        this.user = user;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.MeMenuSettingsComposer);
        this.response.appendInt(this.user.getUserStats().volumeSystem);
        this.response.appendInt(this.user.getUserStats().volumeFurni);
        this.response.appendInt(this.user.getUserStats().volumeTrax);
        this.response.appendBoolean(this.user.getUserStats().preferOldChat);
        this.response.appendBoolean(this.user.getUserStats().blockRoomInvites);
        this.response.appendBoolean(this.user.getUserStats().blockCameraFollow);
        this.response.appendInt(this.user.getUserStats().uiFlags);
        this.response.appendInt(this.user.getUserStats().chatColor.getType());
        // Game privacy flags, broadcast in the positive form the client expects
        // (SaveGamePrivacySettingsEvent stores them inverted as hide/block).
        this.response.appendBoolean(!this.user.getUserStats().hideOnline);
        this.response.appendBoolean(!this.user.getUserStats().blockFollowing);
        this.response.appendBoolean(!this.user.getUserStats().blockFriendRequests);
        this.response.appendInt(this.user.getUserStats().volumeSoundboard);
        // Per-user preferences of the official AIR 13 UserSettings packet (wired whisper switch, chat
        // mode / bubble width / scroll speed, friend-online notification preference).
        this.response.appendBoolean(this.user.getUserStats().wiredWhisperDisabled);
        this.response.appendInt(this.user.getUserStats().chatMode);
        this.response.appendInt(this.user.getUserStats().chatBubbleWidth);
        this.response.appendInt(this.user.getUserStats().chatScrollSpeed);
        this.response.appendInt(this.user.getUserStats().onlineIndicatorPreference);
        // Appended last so older clients, which stop reading earlier, are unaffected: whether the
        // extended profile is visible to others, in the same positive form as the flags above.
        this.response.appendBoolean(!this.user.getUserStats().hideProfile);
        // Then the stored wired menu preferences ("" until saved once), also last for older clients.
        this.response.appendString(this.user.getUserStats().wiredMenuPreferences);
        return this.response;
    }

    public User getUser() {
        return user;
    }
}
