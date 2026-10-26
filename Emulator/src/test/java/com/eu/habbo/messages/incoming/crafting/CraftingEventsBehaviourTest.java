package com.eu.habbo.messages.incoming.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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
import com.eu.habbo.habbohotel.crafting.CraftingAltar;
import com.eu.habbo.habbohotel.crafting.CraftingManager;
import com.eu.habbo.habbohotel.crafting.CraftingRecipe;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.inventory.ItemsComponent;
import com.eu.habbo.messages.ClientMessage;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.incoming.Incoming;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.catalog.AlertLimitedSoldOutComposer;
import com.eu.habbo.messages.outgoing.crafting.CraftingResultComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.threading.ThreadPooling;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItems;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

/**
 * Records what {@link CraftingCraftItemEvent#handle} (craft a recipe by name) and {@link CraftingCraftSecretEvent#handle}
 * (craft from the furni laid on the altar) do: the gates, which furni are taken, how a limited recipe is counted, the
 * reward and what the crafter is told. Each trace ends with the inventory and the recipe's remaining count. The traces
 * live in {@code crafting/crafting-traces.txt}; regenerate them with {@code -Dpolaris.craftingTraces.update=true}.
 */
class CraftingEventsBehaviourTest {
    private static final int USER_ID = 7;
    private static final int ALTAR_ID = 300;

    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private boolean recording;

    private final Item altarBase = this.base("altar");
    private final Item wood = this.base("wood");
    private final Item stone = this.base("stone");
    private final Item rare = this.base("rare");
    private final Item throne = this.base("throne");
    private final Item chair = this.base("chair");

    // the world of one scenario, reset by fresh()
    private final Map<Integer, HabboItem> inventory = new LinkedHashMap<>();
    private final Set<Integer> knownRecipes = new HashSet<>();
    private boolean inRoom;
    private boolean altarThere;
    private boolean isAltar;
    private boolean rewardCreated;
    private CraftingRecipe recipe;
    private CraftingRecipe secret;
    private CraftingAltar altar;

    private GameClient client;
    private ItemManager itemManager;
    private CraftingManager craftingManager;

    @BeforeEach
    void setUp() {
        this.itemManager = mock(ItemManager.class);
        when(this.itemManager.getItem(anyInt())).thenAnswer(i -> switch (i.<Integer>getArgument(0)) {
            case 1 -> this.throne;
            case 2 -> this.chair;
            default -> null;
        });
        when(this.itemManager.createItem(anyInt(), any(), anyInt(), anyInt(), anyString()))
                .thenAnswer(i -> {
                    Item reward = i.getArgument(1);
                    this.record("items.createItem(" + this.label(reward) + ") -> " + this.rewardCreated);
                    return this.rewardCreated ? this.habboItem(900, reward) : null;
                });

        this.craftingManager = mock(CraftingManager.class);
        when(this.craftingManager.getAltar(any())).thenAnswer(i -> this.isAltar ? this.altar : null);

        AchievementManager achievements = mock(AchievementManager.class);
        when(achievements.getAchievement(anyString())).thenAnswer(i -> {
            Achievement achievement = mock(Achievement.class);
            this.labels.put(achievement, i.getArgument(0));
            return achievement;
        });

        GameEnvironment environment = mock(GameEnvironment.class);
        when(environment.getItemManager()).thenReturn(this.itemManager);
        when(environment.getCraftingManager()).thenReturn(this.craftingManager);
        when(environment.getAchievementManager()).thenReturn(achievements);

        ThreadPooling threading = mock(ThreadPooling.class);
        doAnswer(i -> this.record("threading.runPersistence(" + this.label(i.getArgument(0)) + ")"))
                .when(threading)
                .runPersistence(any());

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        emulator.when(Emulator::getThreading).thenReturn(threading);
        this.statics.add(emulator);

        MockedStatic<AchievementManager> progress = mockStatic(AchievementManager.class);
        progress.when(() -> AchievementManager.progressAchievement(any(Habbo.class), any(Achievement.class)))
                .thenAnswer(i -> this.record("achievement(" + this.label(i.getArgument(1)) + ")"));
        this.statics.add(progress);

        this.constructions.add(mockConstruction(
                QueryDeleteHabboItem.class,
                (query, context) -> this.labels.put(
                        query, "DeleteItem(" + context.arguments().get(0) + ")")));
        this.constructions.add(mockConstruction(
                QueryDeleteHabboItems.class,
                (query, context) -> this.labels.put(
                        query,
                        "DeleteItems("
                                + ((Collection<?>) context.arguments().get(0))
                                        .stream().map(this::label).sorted().collect(Collectors.joining(", "))
                                + ")")));
        this.composer(AlertLimitedSoldOutComposer.class, "AlertLimitedSoldOut");
        this.composer(CraftingResultComposer.class, "CraftingResult");
        this.composer(AddHabboItemComposer.class, "AddHabboItem");
        this.composer(RemoveHabboItemComposer.class, "RemoveHabboItem");
        this.composer(InventoryRefreshComposer.class, "InventoryRefresh");
    }

    @AfterEach
    void tearDown() {
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
    }

    // ---------------------------------------------------------------- crafting a recipe by name

    @Test
    void byName() throws Exception {
        this.fresh(false, 0);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.byName("name / a recipe with its ingredients", "throne");

        this.fresh(false, 0);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.inRoom = false;
        this.byName("name / not in a room", "throne");

        this.fresh(false, 0);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.altarThere = false;
        this.byName("name / the altar is not in the room", "throne");

        this.fresh(false, 0);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.isAltar = false;
        this.byName("name / the furni is not an altar", "throne");

        this.fresh(false, 0);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.byName("name / an unknown recipe", "sofa");

        this.fresh(false, 0);
        this.give(11, this.wood).give(13, this.stone);
        this.byName("name / one ingredient short", "throne");

        this.fresh(false, 0);
        this.byName("name / no ingredients at all", "throne");

        this.fresh(false, 0);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.rewardCreated = false;
        this.byName("name / the reward is not created", "throne");

        this.fresh(true, 0);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.byName("name / a limited recipe that is sold out", "throne");

        this.fresh(true, 1);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.byName("name / the last unit of a limited recipe", "throne");

        this.fresh(true, 1);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.rewardCreated = false;
        this.byName("name / the last unit, and the reward is not created", "throne");

        this.fresh(false, 0);
        this.give(21, this.rare);
        this.byName("name / a secret recipe never discovered", "chair");

        this.fresh(false, 0);
        this.give(21, this.rare);
        this.knownRecipes.add(2);
        this.byName("name / a secret recipe already discovered", "chair");
    }

    // ---------------------------------------------------------------- crafting from the furni on the altar

    @Test
    void secret() throws Exception {
        this.fresh(false, 0);
        this.give(21, this.rare);
        this.secret("secret / its ingredients, first time", 21);

        this.fresh(false, 0);
        this.give(21, this.rare);
        this.knownRecipes.add(2);
        this.secret("secret / its ingredients, already discovered", 21);

        this.fresh(false, 0);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.secret("secret / a public recipe laid on the altar", 11, 12, 13);

        this.fresh(false, 0);
        this.give(21, this.rare).give(22, this.throne);
        this.secret("secret / its ingredients and one furni more", 21, 22);

        this.fresh(false, 0);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone).give(14, this.stone);
        this.secret("secret / a public recipe with one ingredient too many", 11, 12, 13, 14);

        this.fresh(false, 0);
        this.give(21, this.rare);
        this.secret("secret / the same furni twice", 21, 21);

        this.fresh(false, 0);
        this.give(21, this.rare);
        this.secret("secret / a furni not in the inventory", 21, 55);

        this.fresh(false, 0);
        this.give(22, this.throne);
        this.secret("secret / furni that match no recipe", 22);

        this.fresh(false, 0);
        this.secret("secret / no furni", new int[0]);

        this.fresh(false, 0);
        this.give(21, this.rare);
        this.inRoom = false;
        this.secret("secret / not in a room", 21);

        this.fresh(false, 0);
        this.give(21, this.rare);
        this.isAltar = false;
        this.secret("secret / the furni is not an altar", 21);

        this.fresh(false, 0);
        this.give(21, this.rare);
        this.rewardCreated = false;
        this.secret("secret / the reward is not created", 21);

        this.fresh(true, 0);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.secret("secret / a limited recipe that is sold out", 11, 12, 13);

        this.fresh(true, 1);
        this.give(11, this.wood).give(12, this.wood).give(13, this.stone);
        this.rewardCreated = false;
        this.secret("secret / the last unit, and the reward is not created", 11, 12, 13);
    }

    // ---------------------------------------------------------------- the harness

    private void fresh(boolean limited, int remaining) throws Exception {
        this.trace.clear();
        this.recording = false;
        this.inventory.clear();
        this.knownRecipes.clear();
        this.inRoom = true;
        this.altarThere = true;
        this.isAltar = true;
        this.rewardCreated = true;

        this.altar = new CraftingAltar(this.altarBase);
        this.recipe = this.recipe(1, "throne", 1, false, limited, remaining, "CraftThrone");
        this.recipe.addIngredient(this.wood, 2);
        this.recipe.addIngredient(this.stone, 1);
        this.secret = this.recipe(2, "chair", 2, true, false, 0, "");
        this.secret.addIngredient(this.rare, 1);
        this.altar.addRecipe(this.recipe);
        this.altar.addRecipe(this.secret);

        ItemsComponent items = mock(ItemsComponent.class);
        when(items.getHabboItem(anyInt())).thenAnswer(i -> this.inventory.get(i.<Integer>getArgument(0)));
        when(items.getAndRemoveHabboItem(any())).thenAnswer(i -> {
            HabboItem found = this.inventory.values().stream()
                    .filter(x -> x.getBaseItem() == i.getArgument(0))
                    .findFirst()
                    .orElse(null);
            if (found != null) this.inventory.remove(found.getId());
            this.record("inventory.getAndRemove(" + this.label(i.getArgument(0)) + ") -> " + this.label(found));
            return found;
        });
        doAnswer(i -> {
                    HabboItem item = i.getArgument(0);
                    this.inventory.put(item.getId(), item);
                    return this.record("inventory.addItem(" + this.label(item) + ")");
                })
                .when(items)
                .addItem(any());
        doAnswer(i -> {
                    HabboItem item = i.getArgument(0);
                    this.inventory.remove(item.getId());
                    return this.record("inventory.remove(" + this.label(item) + ")");
                })
                .when(items)
                .removeHabboItem(any(HabboItem.class));
        HabboInventory inventory = mock(HabboInventory.class);
        when(inventory.getItemsComponent()).thenReturn(items);

        HabboItem altarItem = this.habboItem(ALTAR_ID, this.altarBase);
        Room room = mock(Room.class);
        when(room.getHabboItem(anyInt()))
                .thenAnswer(i -> this.altarThere && i.<Integer>getArgument(0) == ALTAR_ID ? altarItem : null);

        HabboStats stats = mock(HabboStats.class);
        when(stats.hasRecipe(anyInt())).thenAnswer(i -> this.knownRecipes.contains(i.<Integer>getArgument(0)));
        when(stats.addRecipe(anyInt())).thenAnswer(i -> {
            this.record("stats.addRecipe(" + i.getArgument(0) + ")");
            return this.knownRecipes.add(i.getArgument(0));
        });

        HabboInfo info = mock(HabboInfo.class);
        when(info.getId()).thenReturn(USER_ID);
        when(info.getCurrentRoom()).thenAnswer(i -> this.inRoom ? room : null);

        Habbo habbo = mock(Habbo.class, withSettings().name("ann"));
        when(habbo.getHabboInfo()).thenReturn(info);
        when(habbo.getHabboStats()).thenReturn(stats);
        when(habbo.getInventory()).thenReturn(inventory);

        this.client = mock(GameClient.class);
        when(this.client.getHabbo()).thenReturn(habbo);
        doAnswer(i -> this.record("ann <- " + this.label(i.getArgument(0))))
                .when(this.client)
                .sendResponse(any(MessageComposer.class));
        doAnswer(i -> this.record("ann <- " + this.label(i.getArgument(0))))
                .when(this.client)
                .sendResponse(any(ServerMessage.class));
    }

    private CraftingEventsBehaviourTest give(int id, Item base) {
        this.inventory.put(id, this.habboItem(id, base));
        return this;
    }

    private HabboItem habboItem(int id, Item base) {
        HabboItem item = mock(HabboItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getGiftAdjustedId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        this.labels.put(item, this.label(base) + "#" + id);
        return item;
    }

    private Item base(String name) {
        Item item = mock(Item.class);
        this.labels.put(item, name);
        return item;
    }

    private CraftingRecipe recipe(
            int id, String name, int reward, boolean secret, boolean limited, int remaining, String achievement)
            throws Exception {
        ResultSet set = mock(ResultSet.class);
        when(set.getInt("id")).thenReturn(id);
        when(set.getString("product_name")).thenReturn(name);
        when(set.getInt("reward")).thenReturn(reward);
        when(set.getString("secret")).thenReturn(secret ? "1" : "0");
        when(set.getString("achievement")).thenReturn(achievement);
        when(set.getString("limited")).thenReturn(limited ? "1" : "0");
        when(set.getInt("remaining")).thenReturn(remaining);
        CraftingRecipe recipe = new CraftingRecipe(set);
        // Ingredients in the order they are added, so the trace does not follow the mocks' identity hashes.
        Field ingredients = CraftingRecipe.class.getDeclaredField("ingredients");
        ingredients.setAccessible(true);
        ingredients.set(recipe, new LinkedHashMap<Item, Integer>());
        this.labels.put(recipe, "recipe " + name);
        return recipe;
    }

    private void byName(String name, String recipeName) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(ALTAR_ID);
        byte[] text = recipeName.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(text.length);
        buffer.writeBytes(text);
        CraftingCraftItemEvent handler = new CraftingCraftItemEvent();
        this.run(name, handler, new ClientMessage(Incoming.CraftingCraftItemEvent, buffer));
    }

    private void secret(String name, int... itemIds) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(ALTAR_ID);
        buffer.writeInt(itemIds.length);
        for (int id : itemIds) buffer.writeInt(id);
        CraftingCraftSecretEvent handler = new CraftingCraftSecretEvent();
        this.run(name, handler, new ClientMessage(Incoming.CraftingCraftSecretEvent, buffer));
    }

    private void run(String name, MessageHandler handler, ClientMessage packet) {
        handler.client = this.client;
        handler.packet = packet;

        this.recording = true;
        try {
            handler.handle();
        } catch (Exception e) {
            this.record("handle threw " + e.getClass().getSimpleName());
        }
        this.recording = false;

        this.trace.add("after: inventory="
                + this.inventory.values().stream().map(this::label).collect(Collectors.joining(", ", "[", "]"))
                + " remaining=" + this.recipe.getRemaining());
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

    private static final String UPDATE_PROPERTY = "polaris.craftingTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/crafting/crafting-traces.txt");
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
