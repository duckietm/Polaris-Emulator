package com.eu.habbo.messages.outgoing.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.eu.habbo.habbohotel.modtool.ModToolIssue;
import com.eu.habbo.habbohotel.modtool.ModToolIssueChatlogType;
import com.eu.habbo.habbohotel.modtool.ModToolTicketType;
import com.eu.habbo.habbohotel.users.HabboItem;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ModToolIssueChatlogComposerTest {
    private static ModToolIssue issue(ModToolTicketType type, int roomId) {
        ModToolIssue issue = new ModToolIssue(1, "caller", 2, "reported", roomId, "", type);
        issue.id = 9;
        return issue;
    }

    private static ByteBuf header(ByteBuf packet, int roomId) {
        packet.skipBytes(4);
        assertEquals(607, packet.readShort());
        assertEquals(9, packet.readInt());
        assertEquals(1, packet.readInt());
        assertEquals(2, packet.readInt());
        assertEquals(roomId, packet.readInt());
        return packet;
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        return packet.readCharSequence(length, StandardCharsets.UTF_8).toString();
    }

    private static void assertContextKey(ByteBuf packet, String key, int type) {
        assertEquals(key, readString(packet));
        assertEquals(type, packet.readByte());
    }

    @Test
    void emptyImLogStillSendsARecordWithoutContext() {
        ByteBuf packet = new ModToolIssueChatlogComposer(
                        issue(ModToolTicketType.IM, 0), new ArrayList<>(), "", ModToolIssueChatlogType.IM)
                .compose()
                .get();
        try {
            header(packet, 0);
            assertEquals(ModToolIssueChatlogType.IM.getType(), packet.readByte());
            assertEquals(0, packet.readShort());
            assertEquals(0, packet.readShort());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void forumCommentSendsGroupThreadAndMessage() {
        ModToolIssue issue = issue(ModToolTicketType.DISCUSSION, 0);
        issue.groupId = 4;
        issue.threadId = 5;
        issue.commentId = 6;

        ByteBuf packet = new ModToolIssueChatlogComposer(
                        issue, new ArrayList<>(), "", ModToolIssueChatlogType.FORUM_COMMENT)
                .compose()
                .get();
        try {
            header(packet, 0);
            assertEquals(ModToolIssueChatlogType.FORUM_COMMENT.getType(), packet.readByte());
            assertEquals(3, packet.readShort());
            assertContextKey(packet, "groupId", 1);
            assertEquals(4, packet.readInt());
            assertContextKey(packet, "threadId", 1);
            assertEquals(5, packet.readInt());
            assertContextKey(packet, "messageId", 1);
            assertEquals(6, packet.readInt());
            assertEquals(0, packet.readShort());
            assertFalse(packet.isReadable());
        } finally {
            packet.release();
        }
    }

    @Test
    void photoSendsRoomIdAndToleratesAMissingPhoto() {
        ModToolIssue withPhoto = issue(ModToolTicketType.PHOTO, 77);
        withPhoto.photoItem = mock(HabboItem.class);
        when(withPhoto.photoItem.getId()).thenReturn(123);

        for (ModToolIssue issue : new ModToolIssue[] {withPhoto, issue(ModToolTicketType.PHOTO, 77)}) {
            ByteBuf packet = new ModToolIssueChatlogComposer(
                            issue, new ArrayList<>(), "Lobby", ModToolIssueChatlogType.PHOTO)
                    .compose()
                    .get();
            try {
                header(packet, 77);
                assertEquals(ModToolIssueChatlogType.PHOTO.getType(), packet.readByte());
                assertEquals(3, packet.readShort());
                assertContextKey(packet, "roomName", 2);
                assertEquals("Lobby", readString(packet));
                assertContextKey(packet, "roomId", 1);
                assertEquals(77, packet.readInt());
                assertContextKey(packet, "extraDataId", 2);
                assertEquals(issue.photoItem == null ? "" : "123", readString(packet));
                assertEquals(0, packet.readShort());
                assertFalse(packet.isReadable());
            } finally {
                packet.release();
            }
        }
    }
}
