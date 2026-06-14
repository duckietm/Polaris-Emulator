package com.eu.habbo.messages.rcon;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.MeMenuSettingsComposer;
import com.eu.habbo.messages.outgoing.users.UpdateUserLookComposer;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.regex.Pattern;

public class UpdateUser extends RCONMessage<UpdateUser.JSON> {
    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateUser.class);
    static final int DEFAULT_MAX_ACHIEVEMENT_SCORE_DELTA = 10_000;
    static final int MAX_LOOK_LENGTH = 256;
    private static final Pattern LOOK_PATTERN = Pattern.compile("^[A-Za-z0-9.-]{1,256}$");

    public UpdateUser() {
        super(UpdateUser.JSON.class);
    }

    @Override
    public void handle(Gson gson, JSON json) {
        if (json.user_id > 0) {
            int maxAchievementScoreDelta = parseMaxAchievementScoreDelta(Emulator.getConfig().getValue("rcon.updateuser.max_achievement_score_delta", String.valueOf(DEFAULT_MAX_ACHIEVEMENT_SCORE_DELTA)));
            if (!isValidAchievementScoreDelta(json.achievement_score, maxAchievementScoreDelta)) {
                this.status = RCONMessage.STATUS_ERROR;
                this.message = "invalid achievement score";
                return;
            }

            if (!isValidLook(json.look)) {
                this.status = RCONMessage.STATUS_ERROR;
                this.message = "invalid look";
                return;
            }

            Habbo habbo = Emulator.getGameEnvironment().getHabboManager().getHabbo(json.user_id);

            if (habbo != null) {
                habbo.getHabboStats().addAchievementScore(json.achievement_score);

                if (json.block_following != -1) {
                    habbo.getHabboStats().blockFollowing = json.block_following == 1;
                }

                if (json.block_friendrequests != -1) {
                    habbo.getHabboStats().blockFriendRequests = json.block_friendrequests == 1;
                }

                if (json.block_roominvites != -1) {
                    habbo.getHabboStats().blockRoomInvites = json.block_roominvites == 1;
                }

                if (json.old_chat != -1) {
                    habbo.getHabboStats().preferOldChat = json.old_chat == 1;
                }

                if (json.block_camera_follow != -1) {
                    habbo.getHabboStats().blockCameraFollow = json.block_camera_follow == 1;
                }

                if (!json.look.isEmpty()) {
                    habbo.getHabboInfo().setLook(json.look);
                    if (habbo.getClient() != null) {
                        habbo.getClient().sendResponse(new UpdateUserLookComposer(habbo).compose());
                    }

                    if (habbo.getHabboInfo().getCurrentRoom() != null) {
                        habbo.getHabboInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
                    }
                }

                habbo.getHabboStats().run();
                habbo.getClient().sendResponse(new MeMenuSettingsComposer(habbo));
            } else {
                try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
                    try (PreparedStatement statement = connection.prepareStatement("UPDATE users_settings SET achievement_score = achievement_score + ? " +
                            (json.block_following != -1 ? ", block_following = ?" : "") +
                            (json.block_friendrequests != -1 ? ", block_friendrequests = ?" : "") +
                            (json.block_roominvites != -1 ? ", block_roominvites = ?" : "") +
                            (json.old_chat != -1 ? ", old_chat = ?" : "") +
                            (json.block_camera_follow != -1 ? ", block_camera_follow = ?" : "") +
                            " WHERE user_id = ? LIMIT 1"))

                    {
                        int index = 1;
                        statement.setInt(index, json.achievement_score);
                        index++;

                        if (json.block_following != -1) {
                            statement.setString(index, json.block_following == 1 ? "1" : "0");
                            index++;
                        }
                        if (json.block_friendrequests != -1) {
                            statement.setString(index, json.block_friendrequests == 1 ? "1" : "0");
                            index++;
                        }
                        if (json.block_roominvites != -1) {
                            statement.setString(index, json.block_roominvites == 1 ? "1" : "0");
                            index++;
                        }
                        if (json.old_chat != -1) {
                            statement.setString(index, json.old_chat == 1 ? "1" : "0");
                            index++;
                        }
                        if (json.block_camera_follow != -1) {
                            statement.setString(index, json.block_camera_follow == 1 ? "1" : "0");
                            index++;
                        }
                        statement.setInt(index, json.user_id);
                        if (statement.executeUpdate() == 0) {
                            this.status = RCONMessage.HABBO_NOT_FOUND;
                            this.message = "user not found";
                            return;
                        }
                    }

                    if (!json.look.isEmpty()) {
                        try (PreparedStatement statement = connection.prepareStatement("UPDATE users SET look = ? WHERE id = ? LIMIT 1")) {
                            statement.setString(1, json.look);
                            statement.setInt(2, json.user_id);
                            if (statement.executeUpdate() == 0) {
                                this.status = RCONMessage.HABBO_NOT_FOUND;
                                this.message = "user not found";
                                return;
                            }
                        }
                    }
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                    this.status = RCONMessage.SYSTEM_ERROR;
                    this.message = "failed to update user";
                }
            }
        } else {
            this.status = RCONMessage.STATUS_ERROR;
            this.message = "invalid user";
        }
    }

    static boolean isValidAchievementScoreDelta(int achievementScoreDelta, int maxAchievementScoreDelta) {
        return achievementScoreDelta >= 0 && achievementScoreDelta <= maxAchievementScoreDelta;
    }

    static int parseMaxAchievementScoreDelta(String configured) {
        try {
            int parsed = Integer.parseInt(configured);
            if (parsed >= 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
        }

        return DEFAULT_MAX_ACHIEVEMENT_SCORE_DELTA;
    }

    static boolean isValidLook(String look) {
        return look == null || look.isEmpty() || (look.length() <= MAX_LOOK_LENGTH && LOOK_PATTERN.matcher(look).matches());
    }

    static class JSON {

        public int user_id;


        public int achievement_score = 0;


        public int block_following = -1;


        public int block_friendrequests = -1;


        public int block_roominvites = -1;


        public int old_chat = -1;


        public int block_camera_follow = -1;


        public String look = "";

        public boolean strip_unredeemed_clothing = false;
        //More could be added in the future.
    }
}
