package polaris.messages.incoming.rooms.items;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import polaris.Emulator;
import polaris.hotel.economy.EconomyMutationResult;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.interactions.wired.chest.InteractionWiredChest;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.users.LedgerWalletMutation;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserItem;
import polaris.messages.ClientMessage;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.Incoming;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.messages.outgoing.users.UserCreditsComposer;
import polaris.messages.outgoing.users.UserCurrencyComposer;
import polaris.messages.outgoing.users.UserPointsComposer;
import polaris.plugin.PluginManager;
import polaris.plugin.events.furniture.FurnitureRedeemedEvent;
import polaris.plugin.events.users.UserCreditsEvent;
import polaris.plugin.events.users.UserPointsEvent;

/**
 * Records what {@link RedeemItemEvent#handle} does with one redeem packet: the gates, how the furni name becomes a
 * currency and an amount, the plugin events that may cancel or change it, the atomic transaction and what the room and
 * the owner are told. The traces live in {@code rooms/items/redeem-item-traces.txt}; regenerate them with
 * {@code -Dpolaris.redeemItemTraces.update=true}.
 */
class RedeemItemEventBehaviourTest {
    private static final int OWNER_ID = 7;
    private static final int ITEM_ID = 501;

    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private boolean recording;

    // the world of one scenario, reset by fresh()
    private boolean inRoom;
    private boolean itemThere;
    private boolean goneAfterEvents;
    private boolean tileThere;
    private int itemOwner;
    private boolean redeemListener;
    private boolean cancelRedeem;
    private boolean cancelCurrency;
    private Integer changedAmount;
    private boolean transactionCommits;

    private GameClient client;
    private User user;
    private UserInfo info;
    private Room room;
    private UserItem item;

    @BeforeEach
    void setUp() {
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.isRegistered(eq(FurnitureRedeemedEvent.class), anyBoolean()))
                .thenAnswer(i -> this.redeemListener);
        when(plugins.fireEvent(any())).thenAnswer(i -> {
            Object event = i.getArgument(0);
            if (event instanceof FurnitureRedeemedEvent redeemed) {
                this.record("plugins.fire(FurnitureRedeemed " + redeemed.amount + " of " + redeemed.currencyID + ")");
                if (this.cancelRedeem) redeemed.setCancelled(true);
            } else if (event instanceof UserCreditsEvent credits) {
                this.record("plugins.fire(UserCredits " + credits.credits + ")");
                if (this.changedAmount != null) credits.credits = this.changedAmount;
                if (this.cancelCurrency) credits.setCancelled(true);
            } else if (event instanceof UserPointsEvent points) {
                this.record("plugins.fire(UserPoints " + points.points + " of " + points.type + ")");
                if (this.changedAmount != null) points.points = this.changedAmount;
                if (this.cancelCurrency) points.setCancelled(true);
            }
            return event;
        });

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getPluginManager).thenReturn(plugins);
        this.statics.add(emulator);

        MockedStatic<RedeemItemTransaction> transaction = mockStatic(RedeemItemTransaction.class);
        transaction
                .when(() -> RedeemItemTransaction.commit(anyInt(), anyInt(), anyInt(), anyInt(), anyString()))
                .thenAnswer(i -> {
                    this.record("transaction.commit(item " + i.getArgument(0) + ", user " + i.getArgument(1) + ", "
                            + i.getArgument(3) + " of " + i.getArgument(2) + ", " + i.getArgument(4) + ") -> "
                            + this.transactionCommits);
                    return this.transactionCommits
                            ? new EconomyMutationResult(100, 100 + i.<Integer>getArgument(3), true)
                            : null;
                });
        this.statics.add(transaction);

        MockedStatic<LedgerWalletMutation> ledger = mockStatic(LedgerWalletMutation.class);
        ledger.when(() -> LedgerWalletMutation.coordinated(any(), any()))
                .thenAnswer(i -> i.<LedgerWalletMutation.CoordinatedWork<?, ?>>getArgument(1)
                        .execute());
        ledger.when(() -> LedgerWalletMutation.applyCommitted(any(), anyInt(), anyInt()))
                .thenAnswer(
                        i -> this.record("wallet.applyCommitted(" + i.getArgument(1) + " = " + i.getArgument(2) + ")"));
        this.statics.add(ledger);

        this.composer(RemoveFloorItemComposer.class, "RemoveFloorItem");
        this.composer(UpdateStackHeightComposer.class, "UpdateStackHeight");
        this.composer(UserCreditsComposer.class, "UserCredits");
        this.composer(UserCurrencyComposer.class, "UserCurrency");
        this.composer(UserPointsComposer.class, "UserPoints");
    }

    @AfterEach
    void tearDown() {
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
    }

    // ---------------------------------------------------------------- who may redeem what

    @Test
    void gates() throws Exception {
        this.fresh("CF_50", false);
        this.redeem("gate / a valid credit furni", ITEM_ID);

        this.fresh("CF_50", false);
        this.redeem("gate / a negative id", -4);

        this.fresh("CF_50", false);
        this.inRoom = false;
        this.redeem("gate / not in a room", ITEM_ID);

        this.fresh("CF_50", false);
        this.itemThere = false;
        this.redeem("gate / the furni is not in the room", ITEM_ID);

        this.fresh("CF_50", false);
        this.itemOwner = 99;
        this.redeem("gate / someone else's furni", ITEM_ID);

        this.fresh("CF_50", true);
        this.redeem("gate / a wired chest named like a credit furni", ITEM_ID);

        this.fresh("chair", false);
        this.redeem("gate / a furni that is not redeemable", ITEM_ID);
    }

    // ---------------------------------------------------------------- what a name is worth

    @Test
    void names() throws Exception {
        for (String name : List.of(
                "CFC_200",
                "CF_diamond_5",
                "CFC_diamond_5",
                "PF_30",
                "DF_103_12",
                "CF_x",
                "CF_",
                "PF_y",
                "DF_x_12",
                "DF_103",
                "CF_0",
                "CF_-5")) {
            this.fresh(name, false);
            this.redeem("name / " + name, ITEM_ID);
        }
    }

    // ---------------------------------------------------------------- plugins, transaction and the room

    @Test
    void flow() throws Exception {
        this.fresh("CF_50", false);
        this.redeemListener = true;
        this.redeem("plugins / a listener sees the redeem", ITEM_ID);

        this.fresh("CF_50", false);
        this.redeemListener = true;
        this.cancelRedeem = true;
        this.redeem("plugins / a listener cancels the redeem", ITEM_ID);

        this.fresh("CF_50", false);
        this.cancelCurrency = true;
        this.redeem("plugins / the credit event is cancelled", ITEM_ID);

        this.fresh("CF_50", false);
        this.changedAmount = 80;
        this.redeem("plugins / the credit event changes the amount", ITEM_ID);

        this.fresh("PF_30", false);
        this.changedAmount = 0;
        this.redeem("plugins / the points event takes the amount to 0", ITEM_ID);

        this.fresh("CF_50", false);
        this.goneAfterEvents = true;
        this.redeem("room / the furni left the room during the events", ITEM_ID);

        this.fresh("CF_50", false);
        this.transactionCommits = false;
        this.redeem("transaction / nothing is committed", ITEM_ID);

        this.fresh("CF_50", false);
        this.tileThere = false;
        this.redeem("room / the furni's tile is gone", ITEM_ID);
    }

    // ---------------------------------------------------------------- the harness

    private void fresh(String name, boolean wiredChest) {
        this.trace.clear();
        this.recording = false;
        this.inRoom = true;
        this.itemThere = true;
        this.goneAfterEvents = false;
        this.tileThere = true;
        this.itemOwner = OWNER_ID;
        this.redeemListener = false;
        this.cancelRedeem = false;
        this.cancelCurrency = false;
        this.changedAmount = null;
        this.transactionCommits = true;

        Item base = mock(Item.class);
        when(base.getName()).thenReturn(name);
        this.item = wiredChest
                ? mock(InteractionWiredChest.class, withSettings().name("item"))
                : mock(UserItem.class, withSettings().name("item"));
        when(this.item.getId()).thenReturn(ITEM_ID);
        when(this.item.getUserId()).thenAnswer(i -> this.itemOwner);
        when(this.item.getBaseItem()).thenReturn(base);
        when(this.item.getX()).thenReturn((short) 4);
        when(this.item.getY()).thenReturn((short) 5);

        RoomTile tile = new RoomTile((short) 4, (short) 5, (short) 1, RoomTileState.OPEN, true);
        this.labels.put(tile, "tile 4,5");
        RoomLayout layout = mock(RoomLayout.class);
        when(layout.getTile(anyShort(), anyShort())).thenAnswer(i -> this.tileThere ? tile : null);

        this.room = mock(Room.class, withSettings().name("room"));
        when(this.room.getLayout()).thenReturn(layout);
        int[] lookups = new int[1];
        when(this.room.getUserItem(anyInt())).thenAnswer(i -> {
            boolean gone = this.goneAfterEvents && lookups[0]++ > 0;
            return this.itemThere && !gone && i.<Integer>getArgument(0) == ITEM_ID ? this.item : null;
        });
        when(this.room.getStackHeight(anyShort(), anyShort(), anyBoolean())).thenReturn(2.5);
        doAnswer(i -> this.record("room.removeUserItem(item)")).when(this.room).removeUserItem(any(UserItem.class));
        doAnswer(i -> this.record("room.updateTile(" + this.label(i.getArgument(0)) + ")"))
                .when(this.room)
                .updateTile(any());
        doAnswer(i -> this.record("room <- " + this.label(i.getArgument(0))))
                .when(this.room)
                .sendComposer(any());

        this.user = mock(User.class, withSettings().name("ann"));
        this.info = mock(UserInfo.class);
        when(this.info.getId()).thenReturn(OWNER_ID);
        when(this.info.getCurrentRoom()).thenAnswer(i -> this.inRoom ? this.room : null);
        when(this.info.getCurrencyAmount(anyInt())).thenReturn(140);
        when(this.user.getUserInfo()).thenReturn(this.info);
        this.client = mock(GameClient.class);
        when(this.client.getUser()).thenReturn(this.user);
        doAnswer(i -> this.record("ann <- " + this.label(i.getArgument(0))))
                .when(this.client)
                .sendResponse(any(MessageComposer.class));
    }

    private void redeem(String name, int itemId) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(itemId);
        RedeemItemEvent handler = new RedeemItemEvent();
        handler.client = this.client;
        handler.packet = new ClientMessage(Incoming.RedeemItemEvent, buffer);

        this.recording = true;
        try {
            handler.handle();
        } catch (Exception e) {
            this.record("handle threw " + e.getClass().getSimpleName());
        }
        this.recording = false;

        this.check(name, String.join("\n", this.trace));
    }

    private Object record(String line) {
        if (this.recording) this.trace.add(line);
        return null;
    }

    private <T> void composer(Class<T> type, String name) {
        this.constructions.add(mockConstruction(type, (composer, context) -> {
            String text =
                    name + "(" + context.arguments().stream().map(this::label).collect(Collectors.joining(", ")) + ")";
            this.labels.put(composer, text);
            if (composer instanceof MessageComposer messageComposer) {
                ServerMessage message = mock(ServerMessage.class);
                this.labels.put(message, text);
                when(messageComposer.compose()).thenReturn(message);
            }
        }));
    }

    private String label(Object value) {
        if (value == null) return "null";
        String known = this.labels.get(value);
        if (known != null) return known;
        if (mockingDetails(value).isMock()) {
            return mockingDetails(value).getMockCreationSettings().getMockName().toString();
        }
        return String.valueOf(value);
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.redeemItemTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/rooms/items/redeem-item-traces.txt");
    private static final Map<String, String> RECORDING = new ConcurrentSkipListMap<>();

    private void check(String name, String trace) {
        if (Boolean.getBoolean(UPDATE_PROPERTY)) {
            RECORDING.put(name, trace);
            return;
        }

        assertEquals(expectedTraces().get(name), trace, name);
    }

    @AfterAll
    static void writeRecording() throws Exception {
        if (!Boolean.getBoolean(UPDATE_PROPERTY)) return;

        StringBuilder text = new StringBuilder();
        new TreeMap<>(RECORDING)
                .forEach((name, lines) -> text.append("== ")
                        .append(name)
                        .append('\n')
                        .append(lines)
                        .append("\n\n"));
        Files.createDirectories(TRACES.getParent());
        Files.writeString(TRACES, text.toString());
    }

    private static Map<String, String> expectedTraces() {
        try {
            Map<String, String> traces = new TreeMap<>();
            String name = null;
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(TRACES)) {
                if (line.startsWith("== ")) {
                    if (name != null) traces.put(name, String.join("\n", lines).strip());
                    name = line.substring(3);
                    lines.clear();
                } else {
                    lines.add(line);
                }
            }
            if (name != null) traces.put(name, String.join("\n", lines).strip());
            return traces;
        } catch (Exception e) {
            throw new IllegalStateException("Missing " + TRACES + "; record it with -D" + UPDATE_PROPERTY + "=true", e);
        }
    }
}
