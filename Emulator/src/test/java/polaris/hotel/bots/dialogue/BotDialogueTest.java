package polaris.hotel.bots.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.bots.Bot;
import polaris.hotel.users.User;
import polaris.messages.ClientMessage;
import polaris.messages.incoming.Incoming;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.rooms.BotDialogueComposer;

/** Bot dialogues: pages cut to size, the wire both ends read, and a choice that counts only once, for its page. */
class BotDialogueTest {
    /** Bot -7, code 0x01020304, text "Hi", options "A" and "B". The renderer's BotDialogueProtocol test pins the same bytes. */
    private static final String PAGE_HEX =
            "fffffff9" + "01020304" + "0002" + "4869" + "00000002" + "0001" + "41" + "0001" + "42";

    private static final String CHOOSE_HEX = "fffffff9" + "01020304" + "00000001";

    private static final BotDialogue NOBODY = new BotDialogue() {
        @Override
        public boolean handles(Bot bot) {
            return false;
        }

        @Override
        public BotDialoguePage open(User user, Bot bot) {
            return null;
        }

        @Override
        public BotDialoguePage choose(User user, Bot bot, String key) {
            return null;
        }
    };

    private final long[] clock = {1_000};
    private final BotDialogueSessions sessions = new BotDialogueSessions(() -> this.clock[0], () -> 0x01020304, 60_000);
    private final BotDialoguePage page =
            BotDialoguePage.of("Hi", BotDialoguePage.option("buy", "A"), BotDialoguePage.option("bye", "B"));

    @Test
    void writesThePageTheClientReads() {
        ByteBuf frame =
                new BotDialogueComposer(-7, 0x01020304, this.page).compose().get();

        assertEquals(Outgoing.BotDialogueComposer, frame.getShort(4));
        assertEquals(PAGE_HEX, ByteBufUtil.hexDump(frame, 6, frame.readableBytes() - 6));
    }

    @Test
    void readsTheChoiceTheClientWrites() {
        ClientMessage message = new ClientMessage(
                Incoming.BotDialogueChooseEvent,
                Unpooled.wrappedBuffer(HexFormat.of().parseHex(CHOOSE_HEX)));

        assertEquals(-7, message.readInt());
        assertEquals(0x01020304, message.readInt());
        assertEquals(1, message.readInt());
    }

    @Test
    void aClosedPageHasNoCodeAndNoOptions() {
        ByteBuf frame = BotDialogueComposer.closed(-7).compose().get();

        assertEquals(
                "fffffff9" + "00000000" + "0000" + "00000000",
                ByteBufUtil.hexDump(frame, 6, frame.readableBytes() - 6));
    }

    @Test
    void aPageIsCutToSize() {
        BotDialoguePage.Option[] many = new BotDialoguePage.Option[9];

        for (int i = 0; i < many.length; i++) {
            many[i] = BotDialoguePage.option("k" + i, "x".repeat(80) + "\n");
        }

        BotDialoguePage cut = BotDialoguePage.of("line one\nline two\t" + "y".repeat(300), many);

        assertEquals(BotDialoguePage.MAX_OPTIONS, cut.options().size());
        assertEquals(BotDialoguePage.MAX_TEXT, cut.text().length());
        assertTrue(cut.text().startsWith("line one line two "));
        assertEquals(BotDialoguePage.MAX_LABEL, cut.options().get(0).label().length());
        assertThrows(IllegalArgumentException.class, () -> BotDialoguePage.option(" ", "label"));
    }

    @Test
    void aChoiceCountsOnceForItsPage() {
        int nonce = this.sessions.start(1, 7, 30, NOBODY, this.page);

        BotDialogueSessions.Choice choice =
                this.sessions.take(1, 7, 30, nonce, 0).orElseThrow();
        assertSame(NOBODY, choice.dialogue());
        assertEquals("buy", choice.key());

        assertTrue(this.sessions.take(1, 7, 30, nonce, 0).isEmpty());
    }

    @Test
    void aChoiceForAnotherBotRoomCodeOrOptionDoesNothing() {
        int nonce = this.sessions.start(1, 7, 30, NOBODY, this.page);

        assertTrue(this.sessions.take(2, 7, 30, nonce, 0).isEmpty());
        assertTrue(this.sessions.take(1, 8, 30, nonce, 0).isEmpty());
        assertTrue(this.sessions.take(1, 7, 31, nonce, 0).isEmpty());
        assertTrue(this.sessions.take(1, 7, 30, nonce + 1, 0).isEmpty());
        assertTrue(this.sessions.take(1, 7, 30, nonce, 2).isEmpty());
        assertTrue(this.sessions.take(1, 7, 30, nonce, -1).isEmpty());
        // None of those used the page up.
        assertEquals("bye", this.sessions.take(1, 7, 30, nonce, 1).orElseThrow().key());
    }

    @Test
    void anExpiredPageTakesNoChoice() {
        int nonce = this.sessions.start(1, 7, 30, NOBODY, this.page);
        this.clock[0] += 60_001;

        assertTrue(this.sessions.take(1, 7, 30, nonce, 0).isEmpty());
    }

    @Test
    void aNewPageReplacesTheOldOne() {
        int first = this.sessions.start(1, 7, 30, NOBODY, this.page);
        BotDialogueSessions other = new BotDialogueSessions(() -> this.clock[0], () -> 99, 60_000);
        int second = other.start(1, 7, 30, NOBODY, BotDialoguePage.of("Next", BotDialoguePage.option("x", "X")));

        assertTrue(other.take(1, 7, 30, first, 0).isEmpty());
        assertEquals("x", other.take(1, 7, 30, second, 0).orElseThrow().key());
    }

    @Test
    void aDialogueGoingAwayTakesItsPagesWithIt() {
        int nonce = this.sessions.start(1, 7, 30, NOBODY, this.page);
        this.sessions.forget(NOBODY);

        assertTrue(this.sessions.take(1, 7, 30, nonce, 0).isEmpty());
    }

    @Test
    void theHeadersSitInTheirOwnRange() {
        assertEquals(10210, Outgoing.BotDialogueComposer);
        assertEquals(10211, Incoming.BotDialogueOpenEvent);
        assertEquals(10212, Incoming.BotDialogueChooseEvent);
        assertEquals(List.of(), BotDialoguePage.of("").options());
    }
}
