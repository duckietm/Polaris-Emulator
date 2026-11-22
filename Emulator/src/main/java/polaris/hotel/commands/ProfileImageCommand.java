package polaris.hotel.commands;

import polaris.hotel.gameclients.GameClient;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserManager;
import polaris.hotel.users.profile.ProfileImageService;

/**
 * {@code :profileimage <username> <remove|block|unblock>}: removes the pictures a user uploaded to their
 * profile window; {@code block} also stops them uploading new ones, {@code unblock} lets them again.
 */
public class ProfileImageCommand extends Command {
    private static final String[] KEYS = {"profileimage", "profilepicture"};

    private final ProfileImageService service;

    public ProfileImageCommand() {
        this(ProfileImageService.get());
    }

    ProfileImageCommand(ProfileImageService service) {
        super("cmd_profileimage", KEYS);
        this.service = service;
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        User staff = gameClient.getUser();
        String action = params.length >= 3 ? params[2].toLowerCase() : "";
        if (!action.equals("remove") && !action.equals("block") && !action.equals("unblock")) {
            staff.whisperLocalizedOrDefault(
                    "commands.error.cmd_profileimage.usage",
                    "Use :profileimage <username> <remove|block|unblock>",
                    RoomChatMessageBubbles.ALERT);
            return true;
        }

        UserInfo target = UserManager.getOfflineUserInfo(params[1]);
        if (target == null) {
            staff.whisperLocalized(
                    "commands.error.cmd_profileimage.not_found", "%username%", params[1], RoomChatMessageBubbles.ALERT);
            return true;
        }

        switch (action) {
            case "remove" ->
                this.service.removeAll(target.getId(), null, staff.getUserInfo().getId());
            case "block" ->
                this.service.removeAll(target.getId(), true, staff.getUserInfo().getId());
            default -> this.service.setBlocked(target.getId(), false);
        }

        staff.whisperLocalized(
                "commands.succes.cmd_profileimage." + action,
                "%username%",
                target.getUsername(),
                RoomChatMessageBubbles.ALERT);
        return true;
    }
}
