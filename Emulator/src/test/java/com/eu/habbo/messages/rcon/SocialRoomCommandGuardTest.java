package com.eu.habbo.messages.rcon;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialRoomCommandGuardTest {
    @Test
    void forwardUserDoesNotOverwriteSuccessfulStatusWithNotFound() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/ForwardUser.java"));

        assertTrue(source.contains("enterRoom(habbo, object.room_id, \"\", true);") && source.contains("return;"),
                "ForwardUser must return after a successful room forward instead of falling through to HABBO_NOT_FOUND");
        assertTrue(source.contains("@Positive(message = \"invalid user\")"),
                "ForwardUser must reject invalid user ids before execution");
        assertTrue(source.contains("@Positive(message = \"invalid room\")"),
                "ForwardUser must reject invalid room ids before execution");
    }

    @Test
    void alertUserOnlyReportsNotFoundWhenTargetIsMissing() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/AlertUser.java"));

        assertTrue(source.contains("habbo.alert(object.message);") && source.contains("return;"),
                "AlertUser must return after delivering the alert");
        assertTrue(source.contains("@NotBlank(message = \"invalid message\")"),
                "AlertUser must reject blank alerts before execution");
        assertTrue(source.contains("@Size(max = 4096"),
                "AlertUser must bound alert payload size");
    }

    @Test
    void friendAndIgnoreRequestsValidateBothUsers() throws Exception {
        String friend = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/FriendRequest.java"));
        String ignore = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/IgnoreUser.java"));

        assertTrue(friend.contains("json.user_id == json.target_id"),
                "FriendRequest must reject self-friend requests");
        assertTrue(friend.contains("RconUserLookup.userExists(json.user_id)") && friend.contains("RconUserLookup.userExists(json.target_id)"),
                "FriendRequest must reject missing source or target users");
        assertTrue(ignore.contains("object.user_id == object.target_id"),
                "IgnoreUser must reject self-ignore requests");
        assertTrue(ignore.contains("RconUserLookup.userExists(object.user_id)") && ignore.contains("RconUserLookup.userExists(object.target_id)"),
                "IgnoreUser must reject missing source or target users");
        assertTrue(ignore.contains("INSERT IGNORE INTO users_ignored"),
                "IgnoreUser offline writes must avoid duplicate rows");
    }

    @Test
    void talkAndStalkPayloadsAreValidated() throws Exception {
        String talk = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/TalkUser.java"));
        String stalk = Files.readString(Path.of("src/main/java/com/eu/habbo/messages/rcon/StalkUser.java"));

        assertTrue(talk.contains("@NotBlank(message = \"invalid type\")"),
                "TalkUser must reject blank talk types before execution");
        assertTrue(talk.contains("@Size(max = 512"),
                "TalkUser must bound impersonated chat message size");
        assertTrue(stalk.contains("@Positive(message = \"invalid target\")"),
                "StalkUser must reject invalid target ids before execution");
        assertTrue(stalk.contains("this.status = HABBO_NOT_FOUND"),
                "StalkUser must report missing source users");
    }
}
