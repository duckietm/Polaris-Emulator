package polaris.hotel.bots.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import polaris.hotel.bots.Bot;
import polaris.hotel.users.User;
import polaris.messages.ClientMessage;
import polaris.messages.incoming.Incoming;
import polaris.messages.outgoing.Outgoing;
import polaris.messages.outgoing.rooms.BotDialogueWindowComposer;

/** Bot dialogue windows: cut to size, the wire both ends read, and an item taken once, within its amount. */
class BotDialogueWindowTest {
    /**
     * Bot -7, code 0x01020304, title "T", text "Hi", action "Buy", tab "F", item "A" with icon "x_y", price 2, detail
     * "+1", tab 0, up to 5, footer "C". The renderer's BotDialogueWindowProtocol test pins the same bytes.
     */
    private static final String WINDOW_HEX = "fffffff9" + "01020304" + "000154" + "00024869" + "0003427579"
            + "00000001" + "000146"
            + "00000001" + "000141" + "0003785f79" + "00000002" + "00022b31" + "00000000" + "00000005"
            + "00000001" + "000143";

    private static final String CHOOSE_HEX = "fffffff9" + "01020304" + "00000000" + "00000003";

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
    private final BotDialogueWindow window = new BotDialogueWindow(
            "T",
            "Hi",
            List.of("F"),
            List.of(
                    BotDialogueWindow.upTo(
                            BotDialogueWindow.inTab(BotDialogueWindow.item("apple", "A", "x_y", 2, "+1"), 0), 5),
                    BotDialogueWindow.item("bye", "B", "", 0, "")),
            List.of("C"),
            "Buy");

    @Test
    void writesTheWindowTheClientReads() {
        BotDialogueWindow one = new BotDialogueWindow(
                "T", "Hi", List.of("F"), List.of(this.window.items().get(0)), List.of("C"), "Buy");
        ByteBuf frame =
                new BotDialogueWindowComposer(-7, 0x01020304, one).compose().get();

        assertEquals(Outgoing.BotDialogueWindowComposer, frame.getShort(4));
        assertEquals(WINDOW_HEX, ByteBufUtil.hexDump(frame, 6, frame.readableBytes() - 6));
    }

    @Test
    void readsTheChoiceTheClientWrites() {
        ClientMessage message = new ClientMessage(
                Incoming.BotDialogueWindowChooseEvent,
                Unpooled.wrappedBuffer(HexFormat.of().parseHex(CHOOSE_HEX)));

        assertEquals(-7, message.readInt());
        assertEquals(0x01020304, message.readInt());
        assertEquals(0, message.readInt());
        assertEquals(3, message.readInt());
    }

    @Test
    void aClosedWindowHasNoCodeAndNothingInIt() {
        ByteBuf frame = BotDialogueWindowComposer.closed(-7).compose().get();

        assertEquals(
                "fffffff9" + "00000000" + "0000" + "0000" + "0000" + "00000000" + "00000000" + "00000000",
                ByteBufUtil.hexDump(frame, 6, frame.readableBytes() - 6));
    }

    @Test
    void aWindowIsCutToSize() {
        List<BotDialogueWindow.Item> many = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            many.add(BotDialogueWindow.upTo(
                    BotDialogueWindow.inTab(BotDialogueWindow.item("k" + i, "x", "bad icon<", -5, "d".repeat(90)), 7),
                    500));
        }

        BotDialogueWindow cut = new BotDialogueWindow(
                "t".repeat(90), "x", List.of("1", "2", "3", "4", "5"), many, List.of("a", "b", "c", "d"), "go");
        BotDialogueWindow.Item item = cut.items().get(0);

        assertEquals(BotDialogueWindow.MAX_TITLE, cut.title().length());
        assertEquals(BotDialogueWindow.MAX_TABS, cut.tabs().size());
        assertEquals(BotDialogueWindow.MAX_ITEMS, cut.items().size());
        assertEquals(BotDialogueWindow.MAX_FOOTER, cut.footer().size());
        assertEquals("", item.icon());
        assertEquals(0, item.price());
        assertEquals(BotDialogueWindow.MAX_DETAIL, item.detail().length());
        assertEquals(-1, item.tab());
        assertEquals(BotDialogueWindow.MAX_AMOUNT, item.maxAmount());
    }

    @Test
    void anItemIsTakenOnceAndWithinItsAmount() {
        int nonce = this.sessions.start(1, 7, 30, NOBODY, this.window);

        assertTrue(this.sessions.takeFromWindow(1, 7, 30, nonce, 0, 6).isEmpty());
        assertTrue(this.sessions.takeFromWindow(1, 7, 30, nonce, 0, 0).isEmpty());
        assertTrue(this.sessions.takeFromWindow(1, 7, 30, nonce, 1, 2).isEmpty());
        // A menu choice does not count against a window.
        assertTrue(this.sessions.take(1, 7, 30, nonce, 0).isEmpty());

        BotDialogueSessions.Choice choice =
                this.sessions.takeFromWindow(1, 7, 30, nonce, 0, 5).orElseThrow();
        assertEquals("apple", choice.key());
        assertEquals(5, choice.amount());
        assertTrue(this.sessions.takeFromWindow(1, 7, 30, nonce, 0, 1).isEmpty());
    }

    @Test
    void aWindowChoiceDoesNotCountAgainstAMenuPage() {
        int nonce = this.sessions.start(1, 7, 30, NOBODY, BotDialoguePage.of("Hi", BotDialoguePage.option("a", "A")));

        assertTrue(this.sessions.takeFromWindow(1, 7, 30, nonce, 0, 1).isEmpty());
        assertEquals(1, this.sessions.take(1, 7, 30, nonce, 0).orElseThrow().amount());
    }

    @Test
    void aDialogueWithoutAWindowKeepsTheMenu() {
        assertNull(NOBODY.openWindow(null, null));
        assertNull(NOBODY.chooseInWindow(null, null, "x", 1));
    }

    @Test
    void theHeadersSitInTheDialogueRange() {
        assertEquals(10213, Outgoing.BotDialogueWindowComposer);
        assertEquals(10214, Incoming.BotDialogueWindowChooseEvent);
    }
}
