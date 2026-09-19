package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.quests.RewardTrack;
import com.eu.habbo.habbohotel.quests.RewardTrackManager;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;
import com.eu.habbo.habbohotel.users.Habbo;
import java.util.List;

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
        Habbo target = Emulator.getGameEnvironment().getHabboManager().getHabbo(params[1]);
        if (target == null) {
            gameClient
                    .getHabbo()
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
                    .getHabbo()
                    .whisper(
                            Emulator.getTexts()
                                    .getValue("commands.error.cmd_reward_points.track", "No such reward track."),
                            RoomChatMessageBubbles.ALERT);
            return true;
        }
        int points = manager.adjustPoints(target, track, delta);
        gameClient
                .getHabbo()
                .whisper(
                        Emulator.getTexts()
                                .getValue(
                                        "commands.succes.cmd_reward_points",
                                        "%user% now has %points% points on %track% (%delta%).")
                                .replace("%user%", target.getHabboInfo().getUsername())
                                .replace("%points%", Integer.toString(points))
                                .replace("%track%", track.getId())
                                .replace("%delta%", (delta > 0 ? "+" : "") + delta),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }

    private boolean usage(GameClient gameClient) {
        gameClient
                .getHabbo()
                .whisper(
                        Emulator.getTexts()
                                .getValue(
                                        "commands.error.cmd_reward_points",
                                        "Usage: :rewardpoints <user> <points> [track]"),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }
}
