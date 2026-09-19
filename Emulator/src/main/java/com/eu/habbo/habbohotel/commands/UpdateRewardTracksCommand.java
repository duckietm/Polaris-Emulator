package com.eu.habbo.habbohotel.commands;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.quests.RewardTrackManager;
import com.eu.habbo.habbohotel.rooms.RoomChatMessageBubbles;

/**
 * Reloads the reward tracks from the database and pushes the fresh list to every online client, so
 * tasks and prizes edited by staff show up without a restart.
 */
public class UpdateRewardTracksCommand extends Command {
    public UpdateRewardTracksCommand() {
        super(
                "cmd_update_reward_tracks",
                Emulator.getTexts()
                        .getValue(
                                "commands.keys.cmd_update_reward_tracks",
                                "reloadrewards;updaterewards;update_reward_tracks")
                        .split(";"));
    }

    @Override
    public boolean handle(GameClient gameClient, String[] params) throws Exception {
        int tracks = reloadAndBroadcast();
        gameClient
                .getHabbo()
                .whisper(
                        Emulator.getTexts()
                                .getValue(
                                        "commands.succes.cmd_update_reward_tracks",
                                        "Reward tracks reloaded (%count% active).")
                                .replace("%count%", Integer.toString(tracks)),
                        RoomChatMessageBubbles.ALERT);
        return true;
    }

    /** Reloads the tracks and sends the list to every client with a logged-in user; returns the active track count. */
    public static int reloadAndBroadcast() {
        RewardTrackManager manager = Emulator.getGameEnvironment().getRewardTrackManager();
        manager.reloadAndBroadcast();
        return manager.activeTracks().size();
    }
}
