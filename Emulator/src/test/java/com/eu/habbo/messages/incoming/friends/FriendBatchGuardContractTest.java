package com.eu.habbo.messages.incoming.friends;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendBatchGuardContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Path.of("src/main/java/com/eu/habbo/messages/incoming/friends/" + name + ".java"));
    }

    @Test
    void declineFriendRequestsBoundsClientSuppliedBatchCount() throws Exception {
        String source = source("DeclineFriendRequestEvent");

        int count = source.indexOf("int count = this.packet.readInt()");
        int guard = source.indexOf("count <= 0 || count > MAX_BATCH_SIZE", count);
        int loop = source.indexOf("for (int i = 0; i < count; i++)", count);
        int delete = source.indexOf("deleteFriendRequests", loop);

        assertTrue(source.contains("MAX_BATCH_SIZE = 100"),
                "Friend request decline batches should have a conservative cap");
        assertTrue(count > -1, "DeclineFriendRequestEvent must read the client supplied count");
        assertTrue(guard > count, "DeclineFriendRequestEvent must validate the count after reading it");
        assertTrue(guard < loop, "DeclineFriendRequestEvent must validate the count before looping");
        assertTrue(loop < delete, "DeclineFriendRequestEvent should only mutate after the bounded loop starts");
    }

    @Test
    void removeFriendsBoundsClientSuppliedBatchCountBeforeMutations() throws Exception {
        String source = source("RemoveFriendEvent");

        int count = source.indexOf("int count = this.packet.readInt()");
        int guard = source.indexOf("count <= 0 || count > MAX_BATCH_SIZE", count);
        int loop = source.indexOf("for (int i = 0; i < count; i++)", count);
        int idGuard = source.indexOf("habboId <= 0", loop);
        int unfriend = source.indexOf("Messenger.unfriend", loop);

        assertTrue(source.contains("MAX_BATCH_SIZE = 100"),
                "Friend removal batches should have a conservative cap");
        assertTrue(count > -1, "RemoveFriendEvent must read the client supplied count");
        assertTrue(guard > count, "RemoveFriendEvent must validate the count after reading it");
        assertTrue(guard < loop, "RemoveFriendEvent must validate the count before looping");
        assertTrue(idGuard > loop && idGuard < unfriend,
                "RemoveFriendEvent must skip invalid ids before mutating friendships");
    }

    @Test
    void acceptFriendRequestsBoundsClientSuppliedBatchCountBeforeLoadingTargets() throws Exception {
        String source = source("AcceptFriendRequestEvent");

        int count = source.indexOf("int count = this.packet.readInt()");
        int guard = source.indexOf("count <= 0 || count > 100", count);
        int loop = source.indexOf("for (int i = 0; i < count; i++)", count);
        int idGuard = source.indexOf("userId <= 0", loop);
        int loadTarget = source.indexOf("getHabbo(userId)", loop);

        assertTrue(count > -1, "AcceptFriendRequestEvent must read the client supplied count");
        assertTrue(guard > count && guard < loop,
                "AcceptFriendRequestEvent must validate the count before looping");
        assertTrue(idGuard > loop && idGuard < loadTarget,
                "AcceptFriendRequestEvent must skip invalid ids before loading targets");
    }

    @Test
    void friendRequestAndMessagesUseSharedInputGuards() throws Exception {
        String guard = source("FriendInputGuard");
        String request = source("FriendRequestEvent");
        String privateMessage = source("FriendPrivateMessageEvent");
        String invite = source("InviteFriendsEvent");

        assertTrue(guard.contains("MAX_USERNAME_LENGTH = 15"),
                "Friend request usernames should keep the Habbo username length bound");
        assertTrue(guard.contains("MAX_MESSAGE_LENGTH = 255"),
                "Messenger payloads should keep the client message length bound");
        assertTrue(request.contains("FriendInputGuard.normalizeUsername"),
                "Friend requests should normalize usernames before lookup");
        assertTrue(request.contains("FriendInputGuard.isValidUsername"),
                "Friend requests should reject empty or oversized usernames before DB lookup");
        assertTrue(request.contains("Messenger.friendRequested(targetId, this.client.getHabbo().getHabboInfo().getId())"),
                "Friend requests should reject duplicate outgoing requests");
        assertTrue(privateMessage.contains("FriendInputGuard.normalizeMessage"),
                "Private messages should be normalized and capped before plugin dispatch");
        assertTrue(invite.contains("FriendInputGuard.normalizeMessage"),
                "Room invites should be normalized and capped before fan-out");
    }

    @Test
    void relationshipChangesFirePluginEventAndValidatePluginMutation() throws Exception {
        String source = source("ChangeRelationEvent");

        int event = source.indexOf("new UserRelationShipEvent");
        int fire = source.indexOf("Emulator.getPluginManager().fireEvent(event)", event);
        int pluginGuard = source.indexOf("FriendInputGuard.isValidRelation(event.relationShip)", fire);
        int setRelation = source.indexOf("buddy.setRelation(event.relationShip)", pluginGuard);

        assertTrue(source.contains("FriendInputGuard.isValidRelation(relationId)"),
                "Relationship changes should reject invalid client relation ids");
        assertTrue(event > -1 && fire > event,
                "Relationship changes should dispatch the plugin event before applying changes");
        assertTrue(pluginGuard > fire && pluginGuard < setRelation,
                "Relationship changes should reject invalid plugin-mutated relation ids");
    }
}
