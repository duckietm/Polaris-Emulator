package polaris.hotel.commands;

import java.util.List;
import polaris.Emulator;
import polaris.core.TextsManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.quests.RewardTrack;
import polaris.hotel.quests.RewardTrackManager;
import polaris.hotel.rooms.RoomChatMessageBubbles;
import polaris.hotel.users.User;

/**
 * :rewardpoints &lt;user&gt; &lt;points&gt; [track]: gives reward track points to an online user, or takes them
 * away with a negative number; the total never goes below zero. Without a track it is the first
 * active one.
 */
public class RewardTrackPointsCommand extends Command {
    public RewardTrackPointsCommand() {
        super(
                "cmd_reward_points",
                Emulator.getTexts()
                        .getValue("commands.keys.cmd_reward_points", "rewardpoints;rtpoints")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        if (params.length < 3) {
            return usage(gameClient);
        }
        int delta;
        try {
            delta = Integer.parseInt(params[2]);
        } catch (NumberFormatException exception) {
            return usage(gameClient);
        }
        if (delta == 0 || Math.abs(delta) > 1_000_000) {
            return usage(gameClient);
        }
        User target = Emulator.getGameEnvironment().getUserManager().getUser(params[1]);
        if (target == null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_reward_points.offline", "%user% is not online.")
                                    .replace("%user%", params[1]),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
        RewardTrackManager manager = Emulator.getGameEnvironment().getRewardTrackManager();
        RewardTrack track = null;
        if (params.length >= 4) {
            track = manager.getTrack(params[3]);
        } else {
            List<RewardTrack> active = manager.activeTracks();
            if (!active.isEmpty()) {
                track = active.get(0);
            }
        }
        if (track == null) {
            gameClient
                    .getUser()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_reward_points.track", "No such reward track."),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
        int points = manager.adjustPoints(target, track, delta);
        TextsManager texts = Emulator.getTexts();
        if (points < 0) {
            gameClient
                    .getUser()
                    .whisper(
                            texts.getValue(
                                            "commands.error.cmd_reward_points.load",
                                            "Could not load the reward track of %user%, nothing changed.")
                                    .replace("%user%", target.getUserInfo().getUsername()),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
        gameClient
                .getUser()
                .whisper(
                        texts.getValue(
                                        "commands.succes.cmd_reward_points",
                                        "%user% now has %points% points on %track% (%delta%).")
                                .replace("%user%", target.getUserInfo().getUsername())
                                .replace("%points%", Integer.toString(points))
                                .replace("%track%", track.getId())
                                .replace("%delta%", (delta > 0 ? "+" : "") + delta),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }

    private boolean usage(GameClient gameClient) {
        gameClient
                .getUser()
                .whisper(
                        Emulator.getTexts()
                                .getValue(
                                        "commands.error.cmd_reward_points",
                                        "Usage: :rewardpoints <user> <points> [track]"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
