package polaris.hotel.commands;

import polaris.Emulator;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.permissions.Permission;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.clothingvalidation.ClothingValidationManager;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;
import polaris.messages.outgoing.users.UserDataComposer;
import polaris.util.figure.FigureUtil;

public class MimicCommand extends Command {
    public MimicCommand() {
        super(
                "cmd_mimic",
                Emulator.getTexts().getValue("commands.keys.cmd_mimic").split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length == 2) {
            User user = gameClient.getUser().getUserInfo().getCurrentRoom().getUser(params[1]);

            if (user == null) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_mimic.not_found")
                                        .replace("%user%", ""),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }

            if (user == gameClient.getUser()) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_mimic.not_self"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else if (user.hasPermission(Permission.ACC_NOT_MIMICED)
                    && !gameClient.getUser().hasPermission(Permission.ACC_NOT_MIMICED)) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.error.cmd_mimic.blocked")
                                        .replace("%user%", params[1])
                                        .replace(
                                                "%gender_name%",
                                                (user.getUserInfo().getGender().equals(UserGender.M)
                                                        ? Emulator.getTexts().getValue("gender.him")
                                                        : Emulator.getTexts().getValue("gender.her"))),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else if (!user.hasPermission("acc_mimic_unredeemed")
                    && FigureUtil.hasBlacklistedClothing(
                            user.getUserInfo().getLook(), gameClient.getUser().getForbiddenClothing())) {
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts().getValue("commands.error.cmd_mimic.forbidden_clothing"),
                                RoomChatMessageBubbles.ALERT);
                return true;
            } else {
                gameClient
                        .getUser()
                        .getUserInfo()
                        .setLook(
                                ClothingValidationManager.VALIDATE_ON_MIMIC
                                        ? ClothingValidationManager.validateLook(
                                                gameClient.getUser(),
                                                user.getUserInfo().getLook(),
                                                user.getUserInfo().getGender().name())
                                        : user.getUserInfo().getLook());
                gameClient.getUser().getUserInfo().setGender(user.getUserInfo().getGender());
                gameClient.sendResponse(new UserDataComposer(gameClient.getUser()));
                gameClient
                        .getUser()
                        .getUserInfo()
                        .getCurrentRoom()
                        .sendComposer(new RoomUserDataComposer(gameClient.getUser()).compose());
                gameClient
                        .getUser()
                        .whisper(
                                Emulator.getTexts()
                                        .getValue("commands.succes.cmd_mimic.copied")
                                        .replace("%user%", params[1])
                                        .replace(
                                                "%gender_name%",
                                                (user.getUserInfo().getGender().equals(UserGender.M)
                                                        ? Emulator.getTexts().getValue("gender.him")
                                                        : Emulator.getTexts().getValue("gender.her"))),
                                RoomChatMessageBubbles.ALERT);
                return true;
            }
        } else {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_mimic.not_found")
                                    .replace("%user%", ""),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
    }
}
