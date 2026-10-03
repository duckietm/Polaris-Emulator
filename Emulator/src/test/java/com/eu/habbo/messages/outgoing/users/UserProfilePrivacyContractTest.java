package com.eu.habbo.messages.outgoing.users;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.eu.habbo.messages.outgoing.Outgoing;
import io.netty.buffer.ByteBuf;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserProfilePrivacyContractTest {
    private static final Path MAIN = Path.of("src/main/java/com/eu/habbo");

    private static String source(String path) throws Exception {
        return Files.readString(MAIN.resolve(path)).replaceAll("\\s+", "");
    }

    @Test
    void friendRequestSentFlagLooksForARequestFromTheViewerToTheProfile() throws Exception {
        String profile = source("messages/outgoing/users/UserProfileComposer.java");
        String messenger = source("habbohotel/messenger/Messenger.java");

        // friendRequested(to, from) matches user_to_id = to AND user_from_id = from.
        assertTrue(messenger.contains("friendRequested(intuserTo,intuserFrom)"));
        assertTrue(messenger.contains("statement.setInt(1,userTo);statement.setInt(2,userFrom);"));
        assertTrue(profile.contains(
                "Messenger.friendRequested(this.habboInfo.getId(),this.viewer.getHabbo().getHabboInfo().getId())"));
    }

    @Test
    void rconFriendRequestChecksForAnExistingRequestInTheSameDirection() throws Exception {
        String rcon = source("messages/rcon/FriendRequest.java");

        assertTrue(rcon.contains("Messenger.friendRequested(json.target_id,json.user_id)"));
        assertTrue(rcon.contains("Messenger.makeFriendRequest(json.user_id,json.target_id)"));
    }

    @Test
    void hiddenProfileLeavesOutCountsGroupsAndLastOnlineForOtherViewers() throws Exception {
        String profile = source("messages/outgoing/users/UserProfileComposer.java");

        assertTrue(profile.contains(
                "ProfileVisibility.hidesDetailsFrom(this.viewer.getHabbo(),this.habboInfo.getId(),profileHidden)"));
        assertTrue(profile.contains("hideDetails?ProfileVisibility.HIDDEN_VALUE:Messenger.getFriendCount("));
        assertTrue(profile.contains("if(hideDetails)guilds=newArrayList<>();"));
        assertTrue(profile.contains("hideDetails?ProfileVisibility.HIDDEN_VALUE:Emulator.getIntUnixTimestamp()"));
        assertTrue(profile.contains("this.response.appendBoolean(profileHidden);"), "isHidden stays the last field");
    }

    @Test
    void hiddenProfileSendsNoRelationships() throws Exception {
        String event = source("messages/incoming/users/RequestProfileFriendsEvent.java");
        int check = event.indexOf("ProfileVisibility.hidesDetailsFrom(");
        int empty = event.indexOf("newProfileFriendsComposer(userId)");
        int full = event.indexOf("newProfileFriendsComposer(habbo)");

        assertTrue(
                check > 0 && check < empty && empty < full, "the privacy check runs before any relationship is sent");
    }

    @Test
    void emptyRelationshipListKeepsThePacketLayout() {
        ByteBuf payload = new ProfileFriendsComposer(42).compose().get();
        payload.skipBytes(4);

        assertEquals(Outgoing.ProfileFriendsComposer, payload.readUnsignedShort());
        assertEquals(42, payload.readInt());
        assertEquals(0, payload.readInt());
        assertFalse(payload.isReadable());
    }
}
