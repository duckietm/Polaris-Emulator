package polaris.messages.incoming.catalog.recycler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
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
import polaris.core.TextsManager;
import polaris.hotel.GameEnvironment;
import polaris.hotel.gameclients.GameClient;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.items.RecycleBoxOpening;
import polaris.hotel.items.interactions.InteractionGift;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.hotel.rooms.RoomLayout;
import polaris.hotel.rooms.RoomTile;
import polaris.hotel.rooms.RoomTileState;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserInventory;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.inventory.ItemsComponent;
import polaris.messages.ClientMessage;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.Incoming;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.rooms.UpdateStackHeightComposer;
import polaris.messages.outgoing.rooms.items.PresentItemOpenedComposer;
import polaris.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import polaris.messages.outgoing.rooms.users.RoomUserWhisperComposer;
import polaris.threading.ThreadPooling;
import polaris.threading.runnables.OpenGift;

/**
 * Records what {@link OpenRecycleBoxEvent#handle} does with one open packet: who may open what, how a gift is handed
 * to the delayed unwrap, how a recycler box becomes its reward, and what the room and the opener are told. The traces
 * live in {@code catalog/recycler/open-recycle-box-traces.txt}; regenerate them with
 * {@code -Dpolaris.openRecycleBoxTraces.update=true}.
 */
class OpenRecycleBoxEventBehaviourTest {
    private static final int OPENER_ID = 7;
    private static final int ROOM_OWNER_ID = 7;
    private static final int ITEM_ID = 601;

    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private boolean recording;

    // the world of one scenario, reset by fresh()
    private boolean inRoom;
    private int roomOwner;
    private boolean anyRoomOwner;
    private boolean roomRights;
    private boolean itemThere;
    private int itemOwner;
    private boolean alreadyOpening;
    private boolean rewardInside;
    private boolean openingFails;
    private boolean tileThere;

    private GameClient client;
    private User user;
    private Room room;
    private UserItem item;
    private ItemManager itemManager;

    @BeforeEach
    void setUp() {
        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class), anyLong())).thenAnswer(i -> {
            this.record("threading.run(" + this.label(i.getArgument(0)) + ", " + i.getArgument(1) + "ms) explode="
                    + (this.item instanceof InteractionGift gift && gift.explode));
            return null;
        });

        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString())).thenAnswer(i -> "text:" + i.getArgument(0));

        this.itemManager = mock(ItemManager.class);
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getItemManager()).thenReturn(this.itemManager);

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getThreading).thenReturn(threading);
        emulator.when(Emulator::getTexts).thenReturn(texts);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        this.statics.add(emulator);

        this.constructions.add(mockConstruction(OpenGift.class, (gift, context) -> this.labels.put(gift, "OpenGift")));
        this.constructions.add(mockConstruction(
                RoomChatMessage.class,
                (message, context) -> this.labels.put(
                        message,
                        "RoomChatMessage(" + this.label(context.arguments().get(0)) + ")")));
        this.composer(AddUserItemComposer.class, "AddUserItem");
        this.composer(InventoryRefreshComposer.class, "InventoryRefresh");
        this.composer(PresentItemOpenedComposer.class, "PresentItemOpened");
        this.composer(RemoveFloorItemComposer.class, "RemoveFloorItem");
        this.composer(RoomUserWhisperComposer.class, "RoomUserWhisper");
        this.composer(UpdateStackHeightComposer.class, "UpdateStackHeight");
    }

    @AfterEach
    void tearDown() {
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
    }

    // ---------------------------------------------------------------- who may open what

    @Test
    void gates() throws Exception {
        this.fresh(Kind.BOX, "ecotron_box", "24-10-2026");
        this.open("gate / the owner opens their own box", ITEM_ID);

        this.fresh(Kind.BOX, "ecotron_box", "24-10-2026");
        this.inRoom = false;
        this.open("gate / not in a room", ITEM_ID);

        this.fresh(Kind.BOX, "ecotron_box", "24-10-2026");
        this.roomOwner = 99;
        this.open("gate / no rights in the room", ITEM_ID);

        this.fresh(Kind.BOX, "ecotron_box", "24-10-2026");
        this.roomOwner = 99;
        this.roomRights = true;
        this.open("gate / rights in the room, their own box", ITEM_ID);

        this.fresh(Kind.BOX, "ecotron_box", "24-10-2026");
        this.roomOwner = 99;
        this.anyRoomOwner = true;
        this.open("gate / staff in someone's room, their own box", ITEM_ID);

        this.fresh(Kind.BOX, "ecotron_box", "24-10-2026");
        this.itemOwner = 99;
        this.open("gate / someone else's box", ITEM_ID);

        this.fresh(Kind.BOX, "ecotron_box", "24-10-2026");
        this.itemThere = false;
        this.open("gate / the furni is not in the room", ITEM_ID);
    }

    // ---------------------------------------------------------------- gifts

    @Test
    void gifts() throws Exception {
        this.fresh(Kind.GIFT, "present_gen", "");
        this.open("gift / a plain gift is unwrapped at once", ITEM_ID);

        this.fresh(Kind.GIFT, "present_wrap_basic", "");
        this.open("gift / a wrapped gift explodes and waits a second", ITEM_ID);

        this.fresh(Kind.GIFT, "present_gen", "");
        this.alreadyOpening = true;
        this.open("gift / a gift already being opened", ITEM_ID);
    }

    // ---------------------------------------------------------------- recycler boxes and other furni

    @Test
    void boxes() throws Exception {
        this.fresh(Kind.BOX, "ecotron_box", "24-10-2026");
        this.open("box / the reward goes to the inventory", ITEM_ID);

        this.fresh(Kind.BOX, "ecotron_box", "24-10-2026");
        this.rewardInside = false;
        this.open("box / no reward comes out", ITEM_ID);

        this.fresh(Kind.BOX, "ecotron_box", "24-10-2026");
        this.openingFails = true;
        this.open("box / the opening fails", ITEM_ID);

        this.fresh(Kind.BOX, "ecotron_box", "");
        this.open("box / a box without a date", ITEM_ID);

        this.fresh(Kind.BOX, "ecotron_box", "24-10-2026");
        this.tileThere = false;
        this.open("box / the box's tile is gone", ITEM_ID);

        this.fresh(Kind.BOX, "chair", "1");
        this.open("furni / a chair with a state", ITEM_ID);

        this.fresh(Kind.BOX, "chair", "");
        this.open("furni / a chair without a state", ITEM_ID);
    }

    // ---------------------------------------------------------------- the harness

    private enum Kind {
        GIFT,
        BOX
    }

    private void fresh(Kind kind, String name, String extradata) {
        this.trace.clear();
        this.recording = false;
        this.inRoom = true;
        this.roomOwner = ROOM_OWNER_ID;
        this.anyRoomOwner = false;
        this.roomRights = false;
        this.itemThere = true;
        this.itemOwner = OPENER_ID;
        this.alreadyOpening = false;
        this.rewardInside = true;
        this.openingFails = false;
        this.tileThere = true;

        Item base = mock(Item.class);
        when(base.getName()).thenReturn(name);
        if (kind == Kind.GIFT) {
            InteractionGift gift = mock(InteractionGift.class, withSettings().name("gift"));
            when(gift.tryStartOpening()).thenAnswer(i -> !this.alreadyOpening);
            this.item = gift;
        } else {
            this.item = mock(UserItem.class, withSettings().name("box"));
        }
        when(this.item.getId()).thenReturn(ITEM_ID);
        when(this.item.getUserId()).thenAnswer(i -> this.itemOwner);
        when(this.item.getRoomId()).thenReturn(3);
        when(this.item.getBaseItem()).thenReturn(base);
        when(this.item.getExtradata()).thenReturn(extradata);
        when(this.item.getX()).thenReturn((short) 4);
        when(this.item.getY()).thenReturn((short) 5);

        UserItem reward = mock(UserItem.class, withSettings().name("reward"));
        when(this.itemManager.openRecycleBox(any(), any())).thenAnswer(i -> {
            RecycleBoxOpening opening = this.openingFails
                    ? RecycleBoxOpening.FAILED
                    : this.rewardInside
                            ? new RecycleBoxOpening(RecycleBoxOpening.Result.OPENED, reward)
                            : RecycleBoxOpening.EMPTY;
            this.record("items.openRecycleBox(" + this.label(i.getArgument(1)) + ") -> " + opening.result());
            return opening;
        });

        RoomTile tile = new RoomTile((short) 4, (short) 5, (short) 1, RoomTileState.OPEN, true);
        this.labels.put(tile, "tile 4,5");
        RoomLayout layout = mock(RoomLayout.class);
        when(layout.getTile(anyShort(), anyShort())).thenAnswer(i -> this.tileThere ? tile : null);

        this.room = mock(Room.class, withSettings().name("room"));
        when(this.room.getOwnerId()).thenAnswer(i -> this.roomOwner);
        when(this.room.hasRights(any())).thenAnswer(i -> this.roomRights);
        when(this.room.getLayout()).thenReturn(layout);
        when(this.room.getUserItem(anyInt()))
                .thenAnswer(i -> this.itemThere && i.<Integer>getArgument(0) == ITEM_ID ? this.item : null);
        when(this.room.getStackHeight(anyShort(), anyShort(), anyBoolean())).thenReturn(2.5);
        doAnswer(i -> this.record("room.updateItem(" + this.label(i.getArgument(0)) + ")"))
                .when(this.room)
                .updateItem(any(UserItem.class));
        doAnswer(i -> this.record("room.removeUserItem(" + this.label(i.getArgument(0)) + ")"))
                .when(this.room)
                .removeUserItem(any(UserItem.class));
        doAnswer(i -> this.record("room.updateTile(" + this.label(i.getArgument(0)) + ")"))
                .when(this.room)
                .updateTile(any());
        doAnswer(i -> this.record("room <- " + this.label(i.getArgument(0))))
                .when(this.room)
                .sendComposer(any());

        ItemsComponent items = mock(ItemsComponent.class);
        doAnswer(i -> this.record("inventory.addItem(" + this.label(i.getArgument(0)) + ")"))
                .when(items)
                .addItem(any());
        UserInventory inventory = mock(UserInventory.class);
        when(inventory.getItemsComponent()).thenReturn(items);

        this.user = mock(User.class, withSettings().name("ann"));
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(OPENER_ID);
        when(info.getCurrentRoom()).thenAnswer(i -> this.inRoom ? this.room : null);
        when(this.user.getUserInfo()).thenReturn(info);
        when(this.user.getInventory()).thenReturn(inventory);
        when(this.user.hasPermission(anyString())).thenAnswer(i -> this.anyRoomOwner);
        this.client = mock(GameClient.class);
        when(this.client.getUser()).thenReturn(this.user);
        doAnswer(i -> this.record("ann <- " + this.label(i.getArgument(0))))
                .when(this.client)
                .sendResponse(any(MessageComposer.class));
    }

    private void open(String name, int itemId) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(itemId);
        OpenRecycleBoxEvent handler = new OpenRecycleBoxEvent();
        handler.client = this.client;
        handler.packet = new ClientMessage(Incoming.OpenRecycleBoxEvent, buffer);

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

    private static final String UPDATE_PROPERTY = "polaris.openRecycleBoxTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/catalog/recycler/open-recycle-box-traces.txt");
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
