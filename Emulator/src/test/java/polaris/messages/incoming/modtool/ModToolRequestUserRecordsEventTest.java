package polaris.messages.incoming.modtool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.core.TextsManager;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.modtool.ModToolUserRecords;
import polaris.hotel.modtool.ScripterManager;
import polaris.hotel.permissions.Permission;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.messages.ClientMessage;
import polaris.messages.incoming.Incoming;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.modtool.ModToolUserRecordsComposer;

/** What {@link ModToolRequestUserRecordsEvent} answers: staff only, a positive user, one of the four kinds. */
class ModToolRequestUserRecordsEventTest {

    @Test
    void theHeadersMatchTheContractAndTheHandlerIsRegistered() throws Exception {
        assertEquals(9481, Incoming.ModToolRequestUserRecordsEvent);
        assertEquals(9482, Outgoing.ModToolUserRecordsComposer);
        assertTrue(Files.readString(Path.of("src/main/java/polaris/messages/PacketManager.java"))
                .contains("Incoming.ModToolRequestUserRecordsEvent, ModToolRequestUserRecordsEvent.class"));
        assertEquals(500, new ModToolRequestUserRecordsEvent().getRatelimit());
    }

    @Test
    void aUserWithoutTheSupportToolIsReportedAndGetsNothing() throws Exception {
        GameClient client = client(false);
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue("scripter.warning.userinfo")).thenReturn("%username% asked for user records");

        try (MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
                MockedStatic<ScripterManager> scripter = mockStatic(ScripterManager.class);
                MockedStatic<ModToolUserRecords> records = mockStatic(ModToolUserRecords.class)) {
            emulator.when(Emulator::getTexts).thenReturn(texts);

            handle(client, 42, ModToolUserRecords.BANS);

            scripter.verify(() -> ScripterManager.scripterDetected(client, "alice asked for user records"));
            records.verify(() -> ModToolUserRecords.load(anyInt(), anyInt()), never());
        }

        verify(client, never()).sendResponse(any(MessageComposer.class));
    }

    @Test
    void anInvalidUserOrKindIsIgnored() throws Exception {
        for (int[] request : new int[][] {{0, 1}, {-3, 0}, {42, 4}, {42, -1}}) {
            GameClient client = client(true);

            try (MockedStatic<ModToolUserRecords> records = mockStatic(ModToolUserRecords.class)) {
                records.when(() -> ModToolUserRecords.isKind(anyInt())).thenCallRealMethod();

                handle(client, request[0], request[1]);

                records.verify(() -> ModToolUserRecords.load(anyInt(), anyInt()), never());
            }

            verify(client, never()).sendResponse(any(MessageComposer.class));
        }
    }

    @Test
    void everyKindIsAnsweredWithItsRecords() throws Exception {
        for (int kind = ModToolUserRecords.CAUTIONS; kind <= ModToolUserRecords.TRADE_LOCKS; kind++) {
            GameClient client = client(true);
            List<ModToolUserRecords.Entry> entries =
                    List.of(new ModToolUserRecords.Entry(1_700_000_000, "bob", "mod", "BAN", "why", 0));
            int undetailed = kind == ModToolUserRecords.CAUTIONS ? 3 : 0;
            int requested = kind;

            try (MockedStatic<ModToolUserRecords> records = mockStatic(ModToolUserRecords.class)) {
                records.when(() -> ModToolUserRecords.isKind(anyInt())).thenCallRealMethod();
                records.when(() -> ModToolUserRecords.load(eq(42), eq(requested)))
                        .thenReturn(new ModToolUserRecords.Records(undetailed, entries));

                handle(client, 42, kind);
            }

            ArgumentCaptor<MessageComposer> sent = ArgumentCaptor.forClass(MessageComposer.class);
            verify(client).sendResponse(sent.capture());

            ByteBuf packet = sent.getValue().compose().get();
            packet.skipBytes(4);
            assertEquals(9482, packet.readShort());
            assertEquals(42, packet.readInt());
            assertEquals(kind, packet.readInt());
            assertEquals(undetailed, packet.readInt());
            assertEquals(1, packet.readInt());
            assertEquals(1_700_000_000, packet.readInt());
            assertEquals("bob", readString(packet));
            assertEquals("mod", readString(packet));
            assertEquals("BAN", readString(packet));
            assertEquals("why", readString(packet));
            assertEquals(0, packet.readInt());
            assertFalse(packet.isReadable());
        }
    }

    @Test
    void anEmptyListIsStillAnswered() {
        ByteBuf packet = new ModToolUserRecordsComposer(7, 2, 0, null).compose().get();
        packet.skipBytes(6);

        assertEquals(7, packet.readInt());
        assertEquals(2, packet.readInt());
        assertEquals(0, packet.readInt());
        assertEquals(0, packet.readInt());
        assertFalse(packet.isReadable());
    }

    private static GameClient client(boolean staff) {
        UserInfo info = mock(UserInfo.class);
        when(info.getUsername()).thenReturn("alice");
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);
        when(user.hasPermission(Permission.ACC_SUPPORTTOOL)).thenReturn(staff);
        GameClient client = mock(GameClient.class);
        when(client.getUser()).thenReturn(user);
        return client;
    }

    private static void handle(GameClient client, int userId, int kind) throws Exception {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(userId);
        buffer.writeInt(kind);

        ModToolRequestUserRecordsEvent handler = new ModToolRequestUserRecordsEvent();
        handler.client = client;
        handler.packet = new ClientMessage(Incoming.ModToolRequestUserRecordsEvent, buffer);
        handler.handle();
    }

    private static String readString(ByteBuf packet) {
        int length = packet.readUnsignedShort();
        byte[] bytes = new byte[length];
        packet.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
