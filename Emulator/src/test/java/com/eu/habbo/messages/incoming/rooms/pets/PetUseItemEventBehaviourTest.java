package com.eu.habbo.messages.incoming.rooms.pets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.pets.HorsePet;
import com.eu.habbo.habbohotel.pets.MonsterplantPet;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.rooms.RoomLayout;
import com.eu.habbo.habbohotel.rooms.RoomTile;
import com.eu.habbo.habbohotel.rooms.RoomUnit;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.rooms.items.RemoveFloorItemComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.PetStatusUpdateComposer;
import com.eu.habbo.messages.outgoing.rooms.pets.RoomPetHorseFigureComposer;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserStatusComposer;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.invocation.Invocation;
import org.mockito.listeners.InvocationListener;

/**
 * Records what {@link PetUseItemEvent#handle} does when an owner uses a furni on a pet: the gates, the horse dyes,
 * hair styles and saddles, the monster plant revival, fertiliser and rebreed potions, and how the used furni leaves
 * the room. The traces live in {@code rooms/pets/pet-use-item-traces.txt}; regenerate them with
 * {@code -Dpolaris.petUseItemTraces.update=true}.
 */
class PetUseItemEventBehaviourTest {
    private static final int OWNER_ID = 7;
    private static final int ITEM_ID = 501;
    private static final int PET_ID = 31;
    private static final int NOW = 1_700_000_000;

    private static final Set<String> RECORDED = Set.of(
            "removeHabboItem",
            "sendComposer",
            "updateTiles",
            "setRace",
            "setHairColor",
            "setHairStyle",
            "hasSaddle",
            "setSaddleItemId",
            "revive",
            "cycle",
            "setCreated",
            "setCanBreed",
            "clearStatus",
            "setStatus",
            "removeStatus",
            "run",
            "runPersistence",
            "deleteItem",
            "setRoomId");

    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private final InvocationListener listener = report -> this.recordCall((Invocation) report.getInvocation());
    private boolean recording;

    // the world of one scenario, reset by fresh()
    private boolean inRoom;
    private boolean itemThere;
    private boolean petThere;
    private int itemOwner;
    private int petOwner;

    private GameClient client;
    private Room room;
    private HabboItem item;
    private Pet pet;
    private HorsePet horse;
    private MonsterplantPet plant;

    @BeforeEach
    void setUp() {
        ThreadPooling threading = this.traced(ThreadPooling.class, "threading");
        ItemManager itemManager = this.traced(ItemManager.class, "items");
        AchievementManager achievementManager = mock(AchievementManager.class);
        when(achievementManager.getAchievement(anyString()))
                .thenAnswer(i -> mock(Achievement.class, withSettings().name("achievement " + i.getArgument(0))));
        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getItemManager()).thenReturn(itemManager);
        when(environment.getAchievementManager()).thenReturn(achievementManager);

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        emulator.when(Emulator::getThreading).thenReturn(threading);
        emulator.when(Emulator::getIntUnixTimestamp).thenReturn(NOW);
        this.statics.add(emulator);

        MockedStatic<AchievementManager> achievements = mockStatic(AchievementManager.class);
        achievements
                .when(() -> AchievementManager.progressAchievement(any(Habbo.class), any(Achievement.class)))
                .thenAnswer(i -> this.record("progressAchievement(" + this.label(i.getArgument(1)) + ")"));
        this.statics.add(achievements);

        this.composer(RemoveFloorItemComposer.class, "RemoveFloorItem");
        this.composer(PetStatusUpdateComposer.class, "PetStatusUpdate");
        this.composer(RoomPetHorseFigureComposer.class, "RoomPetHorseFigure");
        this.composer(RoomUserStatusComposer.class, "RoomUserStatus");
        this.composer(QueryDeleteHabboItem.class, "QueryDeleteHabboItem");
    }

    @AfterEach
    void tearDown() {
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
    }

    // ---------------------------------------------------------------- who may use what

    @Test
    void gates() throws Exception {
        this.fresh("horse_dye_3", this.horse());
        this.inRoom = false;
        this.use("gate / not in a room");

        this.fresh("horse_dye_3", this.horse());
        this.itemThere = false;
        this.use("gate / the furni is not in the room");

        this.fresh("horse_dye_3", this.horse());
        this.petThere = false;
        this.use("gate / the pet is not in the room");

        this.fresh("horse_dye_3", this.horse());
        this.itemOwner = 99;
        this.use("gate / someone else's furni");

        this.fresh("horse_dye_3", this.horse());
        this.petOwner = 99;
        this.use("gate / someone else's pet");

        this.fresh("horse_dye_3", this.otherPet());
        this.use("gate / a horse dye on a pet that is not a horse");
    }

    // ---------------------------------------------------------------- horses

    @Test
    void horses() throws Exception {
        for (String name : List.of(
                "horse_dye_3",
                "horse_dye_0",
                "horse_dye_15",
                "horse_hairdye_0",
                "horse_hairdye_1",
                "horse_hairdye_14",
                "horse_hairdye_5",
                "horse_hairstyle_0",
                "horse_hairstyle_7",
                "horse_saddle1")) {
            this.fresh(name, this.horse());
            this.use("horse / " + name);
        }

        this.fresh("chair", this.horse());
        this.use("horse / any other furni");

        this.fresh("chair", this.horse());
        this.horse.needsUpdate = true;
        this.use("horse / any other furni while the horse waits to be saved");

        this.fresh("horse_dye_x", this.horse());
        this.use("horse / a dye whose name has no number");
    }

    // ---------------------------------------------------------------- monster plants

    @Test
    void monsterPlants() throws Exception {
        this.fresh("mnstr_revival", this.plant(true, true, false, 3));
        this.use("plant / revival on a dead plant");

        this.fresh("mnstr_revival", this.plant(false, true, false, 3));
        this.use("plant / revival on a living plant");

        this.fresh("mnstr_fert", this.plant(false, false, false, 3));
        this.use("plant / fertiliser on a growing plant");

        this.fresh("mnstr_fert", this.plant(false, true, false, 3));
        this.use("plant / fertiliser on a grown plant");

        this.fresh("mnstr_rebreed", this.plant(false, true, false, 3));
        this.use("plant / rebreed on a common grown plant");

        this.fresh("mnstr_rebreed_2", this.plant(false, true, false, 7));
        this.use("plant / rebreed 2 on a rare grown plant");

        this.fresh("mnstr_rebreed_3", this.plant(false, true, false, 10));
        this.use("plant / rebreed 3 on an epic grown plant");

        this.fresh("mnstr_rebreed", this.plant(false, true, false, 7));
        this.use("plant / rebreed of the wrong rarity");

        this.fresh("mnstr_rebreed", this.plant(false, true, true, 3));
        this.use("plant / rebreed on a plant that can already breed");

        this.fresh("mnstr_rebreed", this.plant(true, true, false, 3));
        this.use("plant / rebreed on a dead plant");

        this.fresh("horse_dye_3", this.plant(false, true, false, 3));
        this.use("plant / a horse dye on a plant");
    }

    // ---------------------------------------------------------------- the harness

    private void fresh(String itemName, Pet target) {
        this.trace.clear();
        this.recording = false;
        this.inRoom = true;
        this.itemThere = true;
        this.petThere = true;
        this.itemOwner = OWNER_ID;
        this.petOwner = OWNER_ID;
        this.pet = target;
        when(this.pet.getUserId()).thenAnswer(i -> this.petOwner);

        Item base = mock(Item.class);
        when(base.getName()).thenReturn(itemName);
        when(base.getId()).thenReturn(4000);
        when(base.getWidth()).thenReturn(1);
        when(base.getLength()).thenReturn(1);
        this.item = this.traced(HabboItem.class, "item");
        when(this.item.getId()).thenReturn(ITEM_ID);
        when(this.item.getUserId()).thenAnswer(i -> this.itemOwner);
        when(this.item.getBaseItem()).thenReturn(base);
        when(this.item.getX()).thenReturn((short) 4);
        when(this.item.getY()).thenReturn((short) 5);
        when(this.item.getRotation()).thenReturn(2);

        RoomTile tile = mock(RoomTile.class, withSettings().name("tile 4,5"));
        RoomLayout layout = mock(RoomLayout.class);
        when(layout.getTile(anyShort(), anyShort())).thenReturn(tile);
        when(layout.getTilesAt(any(RoomTile.class), anyInt(), anyInt(), anyInt()))
                .thenReturn(Set.of(tile));
        this.room = this.traced(Room.class, "room");
        when(this.room.getLayout()).thenReturn(layout);
        when(this.room.getHabboItem(anyInt()))
                .thenAnswer(i -> this.itemThere && i.<Integer>getArgument(0) == ITEM_ID ? this.item : null);
        when(this.room.getPet(anyInt()))
                .thenAnswer(i -> this.petThere && i.<Integer>getArgument(0) == PET_ID ? this.pet : null);

        Habbo habbo = mock(Habbo.class, withSettings().name("ann"));
        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(OWNER_ID);
        when(info.getCurrentRoom()).thenAnswer(i -> this.inRoom ? this.room : null);
        when(habbo.getHabboInfo()).thenReturn(info);
        this.client = mock(GameClient.class);
        when(this.client.getHabbo()).thenReturn(habbo);
    }

    private HorsePet horse() {
        this.horse = this.traced(HorsePet.class, "horse");
        this.plant = null;
        when(this.horse.getRoomUnit()).thenReturn(this.unit());
        return this.horse;
    }

    private MonsterplantPet plant(boolean dead, boolean grown, boolean canBreed, int rarity) {
        this.plant = this.traced(MonsterplantPet.class, "plant");
        this.horse = null;
        when(this.plant.getRoomUnit()).thenReturn(this.unit());
        when(this.plant.isDead()).thenReturn(dead);
        when(this.plant.isFullyGrown()).thenReturn(grown);
        when(this.plant.canBreed()).thenReturn(canBreed);
        when(this.plant.getRarity()).thenReturn(rarity);
        when(this.plant.getGrowthStage()).thenReturn(4);
        when(this.plant.getCreated()).thenReturn(NOW - 3600);
        return this.plant;
    }

    private Pet otherPet() {
        Pet dog = this.traced(Pet.class, "dog");
        this.horse = null;
        this.plant = null;
        when(dog.getRoomUnit()).thenReturn(this.unit());
        return dog;
    }

    private RoomUnit unit() {
        return this.traced(RoomUnit.class, "unit");
    }

    private void use(String name) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(ITEM_ID);
        buffer.writeInt(PET_ID);
        PetUseItemEvent handler = new PetUseItemEvent();
        handler.client = this.client;
        handler.packet = new ClientMessage(Incoming.PetUseItemEvent, buffer);

        this.recording = true;
        try {
            handler.handle();
        } catch (Exception e) {
            this.record("handle threw " + e.getClass().getSimpleName());
        }
        this.recording = false;

        if (this.horse != null && this.horse.needsUpdate) this.trace.add("horse waits to be saved");
        if (this.plant != null && this.plant.packetUpdate) this.trace.add("plant sends a packet update");

        this.check(name, String.join("\n", this.trace));
    }

    private <T> T traced(Class<T> type, String name) {
        T mock = mock(type, withSettings().name(name).invocationListeners(this.listener));
        this.labels.put(mock, name);
        return mock;
    }

    private void recordCall(Invocation invocation) {
        String method = invocation.getMethod().getName();
        if (!RECORDED.contains(method) || invocation.getArguments().length == 0 && method.equals("hasSaddle")) return;

        String target = mockingDetails(invocation.getMock())
                .getMockCreationSettings()
                .getMockName()
                .toString();
        String arguments =
                Arrays.stream(invocation.getArguments()).map(this::label).collect(Collectors.joining(", "));
        this.record(target + "." + method + "(" + arguments + ")");
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
        if (value instanceof java.util.Collection<?> collection) {
            return collection.stream().map(this::label).collect(Collectors.joining(", ", "[", "]"));
        }
        if (value instanceof Enum<?> constant) return constant.name();
        return String.valueOf(value);
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.petUseItemTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/rooms/pets/pet-use-item-traces.txt");
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
