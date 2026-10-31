package com.eu.habbo.habbohotel.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.eu.habbo.Emulator;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.core.TextsManager;
import com.eu.habbo.habbohotel.GameEnvironment;
import com.eu.habbo.habbohotel.achievements.Achievement;
import com.eu.habbo.habbohotel.achievements.AchievementManager;
import com.eu.habbo.habbohotel.bots.Bot;
import com.eu.habbo.habbohotel.bots.BotManager;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import com.eu.habbo.habbohotel.guilds.Guild;
import com.eu.habbo.habbohotel.guilds.GuildManager;
import com.eu.habbo.habbohotel.guilds.GuildMember;
import com.eu.habbo.habbohotel.items.FurnitureType;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.ItemInteraction;
import com.eu.habbo.habbohotel.items.ItemManager;
import com.eu.habbo.habbohotel.items.SoundTrack;
import com.eu.habbo.habbohotel.items.interactions.InteractionBadgeDisplay;
import com.eu.habbo.habbohotel.items.interactions.InteractionDefault;
import com.eu.habbo.habbohotel.items.interactions.InteractionGuildFurni;
import com.eu.habbo.habbohotel.items.interactions.InteractionHopper;
import com.eu.habbo.habbohotel.items.interactions.InteractionMusicDisc;
import com.eu.habbo.habbohotel.items.interactions.InteractionTeleport;
import com.eu.habbo.habbohotel.items.interactions.InteractionTrophy;
import com.eu.habbo.habbohotel.items.interactions.wired.extra.WiredWebApiOwnership;
import com.eu.habbo.habbohotel.items.rentable.RentableFurnitureManager;
import com.eu.habbo.habbohotel.modtool.ScripterManager;
import com.eu.habbo.habbohotel.modtool.WordFilter;
import com.eu.habbo.habbohotel.permissions.Rank;
import com.eu.habbo.habbohotel.pets.Pet;
import com.eu.habbo.habbohotel.pets.PetManager;
import com.eu.habbo.habbohotel.quests.QuestProgressEvents;
import com.eu.habbo.habbohotel.users.ChatStyleRepository;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.habbohotel.users.HabboInventory;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.habbohotel.users.HabboStats;
import com.eu.habbo.habbohotel.users.inventory.BadgesComponent;
import com.eu.habbo.habbohotel.users.inventory.BotsComponent;
import com.eu.habbo.habbohotel.users.inventory.EffectsComponent;
import com.eu.habbo.habbohotel.users.inventory.ItemsComponent;
import com.eu.habbo.habbohotel.users.inventory.PetsComponent;
import com.eu.habbo.messages.ServerMessage;
import com.eu.habbo.messages.outgoing.MessageComposer;
import com.eu.habbo.messages.outgoing.catalog.AlertLimitedSoldOutComposer;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import com.eu.habbo.messages.outgoing.catalog.AlertPurchaseUnavailableComposer;
import com.eu.habbo.messages.outgoing.catalog.PetBoughtNotificationComposer;
import com.eu.habbo.messages.outgoing.catalog.PurchaseOKComposer;
import com.eu.habbo.messages.outgoing.generic.alerts.BubbleAlertComposer;
import com.eu.habbo.messages.outgoing.inventory.AddBotComposer;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.AddPetComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.users.AddUserBadgeComposer;
import com.eu.habbo.plugin.Event;
import com.eu.habbo.plugin.PluginManager;
import com.eu.habbo.plugin.events.users.catalog.UserCatalogItemPurchasedEvent;
import com.eu.habbo.threading.ThreadPooling;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
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
 * Records what {@link CatalogManager#purchaseItem} does with one purchase: the gates it stops at, which atomic
 * path it hands over to, and for an offer no atomic path covers (mixed bundles, offers without base items) every
 * item, bot, pet, badge and effect it creates or deletes, the payment, the packets the buyer receives and the
 * achievements, quests and logs it touches. The traces live in {@code catalog/purchase-item-traces.txt};
 * regenerate them with {@code -Dpolaris.purchaseItemTraces.update=true}.
 */
class CatalogPurchaseItemBehaviourTest {
    private final List<String> trace = new ArrayList<>();
    private final Map<Object, String> labels = new HashMap<>();
    private final Map<String, Object> settings = new HashMap<>();
    private final Set<String> ownedBadges = new LinkedHashSet<>();
    private final Set<Integer> permanentEffects = new LinkedHashSet<>();
    private final Set<String> permissions = new LinkedHashSet<>();
    private final List<MockedStatic<?>> statics = new ArrayList<>();
    private final List<MockedConstruction<?>> constructions = new ArrayList<>();
    private boolean recording;
    private int nextItemId = 500;
    private int nextCompanionId = 900;

    private String ownershipRefusal;
    private int soldChatStyle;
    private boolean ownsChatStyle;
    private boolean paymentAccepted = true;
    private boolean hasClub;
    private int ltdsToday;
    private int extraPluginCredits;
    private boolean createItemFails;
    private boolean createPetFails;
    private boolean guildMember = true;
    private SoundTrack track;

    private CatalogManager manager;
    private Habbo habbo;
    private HabboInfo info;
    private HabboStats stats;

    @BeforeEach
    void setUp() throws Exception {
        this.manager = new CatalogManager(false);
        this.settings.put("hotel.trophies.length.max", 300);

        ConfigurationManager config = mock(ConfigurationManager.class);
        when(config.getBoolean(anyString()))
                .thenAnswer(i -> (Boolean) this.settings.getOrDefault(i.getArgument(0), false));
        when(config.getBoolean(anyString(), anyBoolean()))
                .thenAnswer(i -> (Boolean) this.settings.getOrDefault(i.getArgument(0), i.getArgument(1)));
        when(config.getInt(anyString())).thenAnswer(i -> (Integer) this.settings.getOrDefault(i.getArgument(0), 0));
        when(config.getInt(anyString(), anyInt()))
                .thenAnswer(i -> (Integer) this.settings.getOrDefault(i.getArgument(0), i.getArgument(1)));
        TextsManager texts = mock(TextsManager.class);
        when(texts.getValue(anyString()))
                .thenAnswer(i -> "<" + i.getArgument(0) + " %username% %itemname% %limit% %badge% %pagename%>");
        PluginManager plugins = mock(PluginManager.class);
        when(plugins.fireEvent(any())).thenAnswer(i -> {
            Event event = i.getArgument(0);
            if (event instanceof UserCatalogItemPurchasedEvent purchased) {
                purchased.totalCredits += this.extraPluginCredits;
                this.record("plugin event UserCatalogItemPurchasedEvent(credits " + purchased.totalCredits + ", points "
                        + purchased.totalPoints + ", items " + purchased.itemsList.size() + ", badges "
                        + purchased.badges + ")");
            } else {
                this.record("plugin event " + event.getClass().getSimpleName());
            }
            return event;
        });
        ThreadPooling threading = mock(ThreadPooling.class);
        when(threading.run(any(Runnable.class)))
                .thenAnswer(i -> this.record("threading.run(" + this.label(i.getArgument(0)) + ")"));

        GameEnvironment environment = mock(GameEnvironment.class);
        ItemManager items = mock(ItemManager.class);
        when(items.createItem(anyInt(), any(Item.class), anyInt(), anyInt(), anyString()))
                .thenAnswer(i -> {
                    Item base = i.getArgument(1);
                    if (this.createItemFails) {
                        this.record("items.createItem(" + this.label(base) + ") -> null");
                        return null;
                    }
                    HabboItem created = this.createdItem(base);
                    this.record("items.createItem(" + this.label(base) + ", stack " + i.getArgument(2) + ", number "
                            + i.getArgument(3) + ", \"" + printable(i.getArgument(4)) + "\") -> "
                            + this.label(created));
                    return created;
                });
        doAnswer(i -> this.record("items.insertTeleportPair(" + i.getArgument(0) + ", " + i.getArgument(1) + ")"))
                .when(items)
                .insertTeleportPair(anyInt(), anyInt());
        doAnswer(i -> this.record("items.insertHopper(" + this.label(i.getArgument(0)) + ")"))
                .when(items)
                .insertHopper(any(HabboItem.class));
        doAnswer(i -> this.record("items.deleteItem(" + this.label(i.getArgument(0)) + ")"))
                .when(items)
                .deleteItem(any(HabboItem.class));
        when(items.getSoundTrack(anyString())).thenAnswer(i -> this.track);
        BotManager bots = mock(BotManager.class);
        when(bots.createBot(any(), anyString(), anyInt())).thenAnswer(i -> {
            Bot bot = mock(Bot.class, withSettings().name("bot#" + this.nextCompanionId));
            when(bot.getId()).thenReturn(this.nextCompanionId++);
            this.labels.put(bot, "bot(" + i.getArgument(1) + ")");
            this.record("bots.createBot(" + new TreeMap<>((Map<String, String>) i.getArgument(0)) + ", "
                    + i.getArgument(1) + ")");
            return bot;
        });
        doAnswer(i -> this.record("bots.deleteBot(" + this.label(i.getArgument(0)) + ")"))
                .when(bots)
                .deleteBot(any());
        PetManager pets = mock(PetManager.class);
        when(pets.createPet(any(Item.class), anyString(), anyString(), anyString(), any()))
                .thenAnswer(i -> {
                    this.record("pets.createPet(" + this.label(i.getArgument(0)) + ", " + i.getArgument(1) + ", "
                            + i.getArgument(2) + ", " + i.getArgument(3) + ")");
                    if (this.createPetFails) throw new IllegalStateException("pet creation failed");
                    Pet pet = mock(Pet.class, withSettings().name("pet#" + this.nextCompanionId));
                    when(pet.getId()).thenReturn(this.nextCompanionId++);
                    this.labels.put(pet, "pet(" + i.getArgument(1) + ")");
                    return pet;
                });
        doAnswer(i -> this.record("pets.deletePet(" + this.label(i.getArgument(0)) + ")"))
                .when(pets)
                .deletePet(any());
        GuildManager guilds = mock(GuildManager.class);
        Guild guild = mock(Guild.class, withSettings().name("guild#33"));
        this.labels.put(guild, "guild#33");
        when(guild.getOwnerId()).thenReturn(7);
        doAnswer(i -> this.record("guild.setForum(" + i.getArgument(0) + ")"))
                .when(guild)
                .setForum(anyBoolean());
        doAnswer(i -> this.record("guild.run()")).when(guild).run();
        when(guilds.getGuild(33)).thenReturn(guild);
        when(guilds.getGuildMember(any(Guild.class), any(Habbo.class)))
                .thenAnswer(i -> this.guildMember ? mock(GuildMember.class) : null);
        doAnswer(i -> this.record("guilds.setGuild(" + this.label(i.getArgument(0)) + ", " + i.getArgument(1) + ")"))
                .when(guilds)
                .setGuild(any(), anyInt());
        AchievementManager achievements = mock(AchievementManager.class);
        when(achievements.getAchievement(anyString())).thenAnswer(i -> {
            Achievement achievement = mock(Achievement.class);
            this.labels.put(achievement, "achievement " + i.getArgument(0));
            return achievement;
        });
        WordFilter filter = mock(WordFilter.class);
        when(filter.filter(anyString(), any())).thenAnswer(i -> "filtered[" + i.getArgument(0) + "]");
        when(environment.getItemManager()).thenReturn(items);
        when(environment.getBotManager()).thenReturn(bots);
        when(environment.getPetManager()).thenReturn(pets);
        when(environment.getGuildManager()).thenReturn(guilds);
        when(environment.getAchievementManager()).thenReturn(achievements);
        when(environment.getWordFilter()).thenReturn(filter);

        MockedStatic<Emulator> emulator = mockStatic(Emulator.class);
        emulator.when(Emulator::getConfig).thenReturn(config);
        emulator.when(Emulator::getTexts).thenReturn(texts);
        emulator.when(Emulator::getPluginManager).thenReturn(plugins);
        emulator.when(Emulator::getThreading).thenReturn(threading);
        emulator.when(Emulator::getGameEnvironment).thenReturn(environment);
        emulator.when(Emulator::getIntUnixTimestamp).thenReturn(1000);
        this.statics.add(emulator);

        MockedStatic<WiredWebApiOwnership> ownership = mockStatic(WiredWebApiOwnership.class);
        ownership
                .when(() -> WiredWebApiOwnership.purchaseRefusal(any(), anyInt(), anyInt()))
                .thenAnswer(i -> this.ownershipRefusal);
        this.statics.add(ownership);
        MockedStatic<ChatStyleRepository> chatStyles = mockStatic(ChatStyleRepository.class);
        chatStyles.when(() -> ChatStyleRepository.styleForCatalogItem(anyInt())).thenAnswer(i -> this.soldChatStyle);
        chatStyles.when(() -> ChatStyleRepository.owns(anyInt(), anyInt())).thenAnswer(i -> this.ownsChatStyle);
        chatStyles
                .when(() -> ChatStyleRepository.grantForPurchase(any(), any()))
                .thenAnswer(i -> this.record("ChatStyleRepository.grantForPurchase"));
        this.statics.add(chatStyles);
        MockedStatic<ScripterManager> scripter = mockStatic(ScripterManager.class);
        scripter.when(() -> ScripterManager.scripterDetected(any(), anyString()))
                .thenAnswer(
                        i -> this.record("ScripterManager.scripterDetected(\"" + printable(i.getArgument(1)) + "\")"));
        this.statics.add(scripter);
        MockedStatic<AchievementManager> achievementStatic = mockStatic(AchievementManager.class);
        achievementStatic
                .when(() -> AchievementManager.progressAchievement(any(Habbo.class), any(Achievement.class)))
                .thenAnswer(i ->
                        this.record("AchievementManager.progressAchievement(" + this.label(i.getArgument(1)) + ")"));
        this.statics.add(achievementStatic);
        MockedStatic<CatalogPaymentService> payment = mockStatic(CatalogPaymentService.class);
        payment.when(() -> CatalogPaymentService.tryTake(any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(i -> {
                    this.record("CatalogPaymentService.tryTake(credits " + i.getArgument(1) + ", type "
                            + i.getArgument(2) + ", points " + i.getArgument(3) + ") -> " + this.paymentAccepted);
                    return this.paymentAccepted;
                });
        payment.when(() -> CatalogPaymentService.refund(any(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(i -> this.record("CatalogPaymentService.refund(credits " + i.getArgument(1) + ", type "
                        + i.getArgument(2) + ", points " + i.getArgument(3) + ")"));
        this.statics.add(payment);
        MockedStatic<RentableFurnitureManager> rent = mockStatic(RentableFurnitureManager.class);
        rent.when(() -> RentableFurnitureManager.track(any()))
                .thenAnswer(i -> this.record("RentableFurnitureManager.track(" + this.label(i.getArgument(0)) + ")"));
        this.statics.add(rent);
        MockedStatic<QuestProgressEvents> quests = mockStatic(QuestProgressEvents.class);
        quests.when(() -> QuestProgressEvents.progress(any(Habbo.class), any(), anyInt()))
                .thenAnswer(i -> this.record("QuestProgressEvents.progress(" + i.getArgument(1) + ")"));
        this.statics.add(quests);
        MockedStatic<CatalogPurchaseTransaction> transaction = mockStatic(CatalogPurchaseTransaction.class);
        transaction
                .when(() -> CatalogPurchaseTransaction.execute(any(), anyString(), any()))
                .thenAnswer(i -> {
                    this.record("CatalogPurchaseTransaction.execute("
                            + ((String) i.getArgument(1)).replaceAll(":[^:]*$", ":<op>") + ") -> stubbed failure");
                    throw new SQLException("stubbed atomic transaction");
                });
        this.statics.add(transaction);

        this.composer(AlertPurchaseFailedComposer.class, "AlertPurchaseFailed");
        this.composer(AlertPurchaseUnavailableComposer.class, "AlertPurchaseUnavailable");
        this.composer(AlertLimitedSoldOutComposer.class, "AlertLimitedSoldOut");
        this.composer(BubbleAlertComposer.class, "BubbleAlert");
        this.composer(AddBotComposer.class, "AddBot");
        this.composer(AddPetComposer.class, "AddPet");
        this.composer(PetBoughtNotificationComposer.class, "PetBoughtNotification");
        this.composer(AddUserBadgeComposer.class, "AddUserBadge");
        this.composer(AddHabboItemComposer.class, "AddHabboItem");
        this.composer(PurchaseOKComposer.class, "PurchaseOK");
        this.composer(InventoryRefreshComposer.class, "InventoryRefresh");
        this.composer(com.eu.habbo.messages.outgoing.catalog.NotEnoughPointsTypeComposer.class, "NotEnoughPointsType");

        this.habbo = this.buyer();
    }

    @AfterEach
    void tearDown() {
        this.constructions.forEach(MockedConstruction::close);
        for (int i = this.statics.size() - 1; i >= 0; i--) this.statics.get(i).close();
    }

    // ---------------------------------------------------------------- gates

    @Test
    void gates() throws Exception {
        this.buy("gate / no offer", null, 1, "", false);

        this.stats.isPurchasingFurniture = true;
        this.buy(
                "gate / a purchase already running",
                this.offer(10, "chair", 5, 0, base(1, "chair", FurnitureType.FLOOR)),
                1,
                "",
                false);
        this.stats.isPurchasingFurniture = false;

        CatalogItem club = this.offer(11, "club_chair", 5, 0, base(1, "chair", FurnitureType.FLOOR));
        when(club.isClubOnly()).thenReturn(true);
        this.buy("gate / club only without club", club, 1, "", false);

        this.buy(
                "gate / amount zero",
                this.offer(12, "chair", 5, 0, base(1, "chair", FurnitureType.FLOOR)),
                0,
                "",
                false);

        this.ownershipRefusal = "web api says no";
        this.buy(
                "gate / web api ownership refusal",
                this.offer(13, "chair", 5, 0, base(1, "chair", FurnitureType.FLOOR)),
                1,
                "",
                false);
        this.ownershipRefusal = null;

        this.soldChatStyle = 4;
        this.ownsChatStyle = true;
        this.buy("gate / chat style already owned", this.offer(14, "bubble_style", 5, 0), 1, "", false);
        this.ownsChatStyle = false;
        this.soldChatStyle = 0;
    }

    @Test
    void limitedGates() throws Exception {
        CatalogItem limited = this.offer(20, "ltd_throne", 50, 0, base(2, "throne", FurnitureType.FLOOR));
        when(limited.isLimited()).thenReturn(true);
        CatalogLimitedConfiguration config = this.limitedConfig(20, 0);
        this.buy("limited / sold out", limited, 1, "", false);

        when(config.available()).thenReturn(3);
        this.settings.put("hotel.catalog.ltd.limit.enabled", true);
        this.settings.put("hotel.purchase.ltd.limit.daily.total", 2);
        this.settings.put("hotel.purchase.ltd.limit.daily.item", 1);
        this.ltdsToday = 2;
        this.buy("limited / daily total reached", limited, 1, "", false);
        this.ltdsToday = 1;
        this.buy("limited / daily item limit reached", limited, 1, "", false);
        this.settings.remove("hotel.catalog.ltd.limit.enabled");
        this.ltdsToday = 0;

        when(config.pollNumber()).thenReturn(OptionalInt.empty());
        this.buy("limited / no number left after the price check", limited, 3, "", false);

        when(config.pollNumber()).thenReturn(OptionalInt.of(17));
        this.buy("limited / furniture goes atomic and gives its number back", limited, 1, "", false);
    }

    @Test
    void amountsAndPrices() throws Exception {
        CatalogItem pack = this.offer(30, "chair_pack", 5, 0, base(1, "chair", FurnitureType.FLOOR));
        when(pack.getAmount()).thenReturn(3);
        this.buy("amount / equal to the pack size counts as one", pack, 3, "", false);

        CatalogItem chair = this.offer(31, "chair", 5, 0, base(1, "chair", FurnitureType.FLOOR));
        when(chair.getAmount()).thenReturn(20);
        this.buy("amount / more than 100 items", chair, 6, "", false);

        CatalogItem single = this.offer(32, "chair", 5, 0, base(1, "chair", FurnitureType.FLOOR));
        this.buy("amount / several without a bulk offer", single, 2, "", false);

        when(this.info.getCredits()).thenReturn(4);
        this.buy(
                "price / not enough credits",
                this.offer(33, "chair", 5, 0, base(1, "chair", FurnitureType.FLOOR)),
                1,
                "",
                false);
        when(this.info.getCredits()).thenReturn(1000);
        this.buy(
                "price / not enough points",
                this.offer(34, "chair", 0, 2000, base(1, "chair", FurnitureType.FLOOR)),
                1,
                "",
                false);
    }

    // ---------------------------------------------------------------- atomic paths

    @Test
    void atomicPaths() throws Exception {
        CatalogItem habbicon = this.offer(40, "habbicon_star", 5, 0, base(3, "habbicon", FurnitureType.FLOOR));
        when(habbicon.getHabbiconId()).thenReturn(8);
        this.buy("atomic / habbicon", habbicon, 1, "", false);
        this.buy("atomic / habbicon bought twice at once", habbicon, 2, "", false);

        this.buy(
                "atomic / badges and effects",
                this.offer(41, "badge_pack", 5, 0, base(4, "ACH_1", FurnitureType.BADGE)),
                1,
                "",
                false);
        this.buy(
                "atomic / a bot",
                this.offer(42, "bot_generic", 5, 0, base(5, "bot_generic", FurnitureType.FLOOR)),
                1,
                "",
                false);
        this.buy(
                "atomic / a pet",
                this.offer(43, "a0 pet1", 5, 0, base(6, "a0 pet1", FurnitureType.FLOOR)),
                1,
                "Rex\n1\nFFFFFF",
                false);
        this.buy(
                "atomic / furniture",
                this.offer(44, "chair", 5, 0, base(1, "chair", FurnitureType.FLOOR)),
                1,
                "",
                false);
    }

    // ---------------------------------------------------------------- the legacy path: mixed offers

    @Test
    void furnitureWithABadge() throws Exception {
        CatalogItem bundle = this.offer(
                50, "chair_badge", 5, 2, base(1, "chair", FurnitureType.FLOOR), base(4, "ACH_1", FurnitureType.BADGE));
        this.buy("mixed / furniture and a new badge", bundle, 1, "", false);
        this.ownedBadges.add("ACH_1");
        this.buy("mixed / furniture and a badge already owned", bundle, 1, "", false);
    }

    @Test
    void anOfferWithoutBaseItems() throws Exception {
        this.soldChatStyle = 4;
        this.buy("mixed / chat style offer without base items", this.offer(51, "bubble_style", 5, 0), 1, "", false);
    }

    @Test
    void effectsInABundle() throws Exception {
        Item effect = base(7, "fx_wings", FurnitureType.EFFECT);
        when(effect.getEffectM()).thenReturn(201);
        when(effect.getEffectF()).thenReturn(202);
        CatalogItem bundle = this.offer(52, "chair_wings", 5, 0, base(1, "chair", FurnitureType.FLOOR), effect);
        when(bundle.getEffectDuration()).thenReturn(3600);
        this.buy("mixed / furniture and an effect", bundle, 1, "", false);
        this.permanentEffects.add(201);
        this.buy("mixed / an effect owned for good", bundle, 1, "", false);
    }

    @Test
    void specialFurnitureInABundle() throws Exception {
        Item badge = base(4, "ACH_1", FurnitureType.BADGE);
        this.buy(
                "mixed / teleport pair",
                this.offer(
                        53,
                        "tele_badge",
                        5,
                        0,
                        base(8, "teleport", FurnitureType.FLOOR, InteractionTeleport.class),
                        badge),
                1,
                "",
                false);
        this.buy(
                "mixed / hopper",
                this.offer(
                        54,
                        "hopper_badge",
                        5,
                        0,
                        base(9, "hopper", FurnitureType.FLOOR, InteractionHopper.class),
                        badge),
                1,
                "",
                false);

        Item guildGate = base(10, "guild_forum", FurnitureType.FLOOR, InteractionGuildFurni.class);
        CatalogItem guildBundle = this.offer(55, "guild_badge", 5, 0, guildGate, badge);
        this.buy("mixed / guild furniture for a member", guildBundle, 1, "33", false);
        this.guildMember = false;
        this.buy("mixed / guild furniture for an outsider", guildBundle, 1, "33", false);
        this.guildMember = true;
        this.buy("mixed / guild furniture with a bad id", guildBundle, 1, "x", false);

        CatalogItem disc = this.offer(
                56, "disc_badge", 5, 0, base(11, "song_disk", FurnitureType.FLOOR, InteractionMusicDisc.class), badge);
        when(disc.getExtradata()).thenReturn("song1");
        this.buy("mixed / music disc without its track", disc, 1, "", false);
        this.track = mock(SoundTrack.class);
        when(this.track.getLength()).thenReturn(120);
        when(this.track.getName()).thenReturn("Song One");
        when(this.track.getId()).thenReturn(77);
        this.buy("mixed / music disc", disc, 1, "", false);
    }

    @Test
    void engravedFurnitureInABundle() throws Exception {
        Item badge = base(4, "ACH_1", FurnitureType.BADGE);
        CatalogItem trophies = this.offer(
                57, "trophy_badge", 5, 0, base(12, "trophy", FurnitureType.FLOOR, InteractionTrophy.class), badge);
        when(trophies.getItemAmount(12)).thenReturn(2);
        this.buy("mixed / two trophies", trophies, 1, "Well done", false);

        CatalogItem displays = this.offer(
                58,
                "display_badge",
                5,
                0,
                base(13, "badge_display", FurnitureType.FLOOR, InteractionBadgeDisplay.class),
                badge);
        when(displays.getItemAmount(13)).thenReturn(2);
        this.ownedBadges.add("GOLD");
        this.buy("mixed / two badge displays of an owned badge", displays, 1, "GOLD", false);
        this.buy("mixed / a badge display of a badge not owned", displays, 1, "SILVER", false);
    }

    @Test
    void companionsInABundle() throws Exception {
        Item chair = base(1, "chair", FurnitureType.FLOOR);
        CatalogItem botBundle = this.offer(59, "bot_generic", 5, 0, base(5, "bot_generic", FurnitureType.FLOOR), chair);
        when(botBundle.getExtradata()).thenReturn("name:Robbie;motto:Hi");
        this.buy("mixed / a bot and furniture", botBundle, 1, "", false);
        this.paymentAccepted = false;
        this.buy("mixed / a bot and furniture, payment refused", botBundle, 1, "", false);
        this.paymentAccepted = true;

        CatalogItem frank = this.offer(60, "bot_frank", 5, 0, base(14, "bot_frank", FurnitureType.FLOOR), chair);
        this.buy("mixed / frank without permission", frank, 1, "", false);

        CatalogItem petBundle = this.offer(61, "pet_chair", 5, 0, base(6, "a0 pet1", FurnitureType.FLOOR), chair);
        this.buy("mixed / a pet and furniture", petBundle, 1, "Rex\n1\nFFFFFF", false);
        this.buy("mixed / a pet without its name, race and colour", petBundle, 1, "Rex", false);
        this.createPetFails = true;
        this.buy("mixed / a pet that cannot be created", petBundle, 1, "Rex\n1\nFFFFFF", false);
    }

    @Test
    void paymentAndDelivery() throws Exception {
        Item badge = base(4, "ACH_1", FurnitureType.BADGE);
        CatalogItem bundle = this.offer(62, "chair_badge", 5, 2, base(1, "chair", FurnitureType.FLOOR), badge);
        this.paymentAccepted = false;
        this.buy("payment / refused", bundle, 1, "", false);
        this.paymentAccepted = true;

        this.extraPluginCredits = 5000;
        this.buy("payment / a plugin raises the price above the balance", bundle, 1, "", false);
        this.extraPluginCredits = 0;

        this.permissions.add("acc_infinite_credits");
        this.buy("payment / infinite credits", bundle, 1, "", false);
        this.permissions.clear();

        this.buy("payment / free", bundle, 1, "", true);

        CatalogItem rent = this.offer(63, "rent_chair", 5, 0, base(1, "chair", FurnitureType.FLOOR), badge);
        when(rent.isRentOffer()).thenReturn(true);
        when(rent.getRentDays()).thenReturn(3);
        this.buy("delivery / a rent offer", rent, 1, "", false);

        CatalogItem rentFurni = this.offer(64, "rent_chair_only", 5, 0, base(1, "chair", FurnitureType.FLOOR));
        when(rentFurni.isRentOffer()).thenReturn(true);
        when(rentFurni.getRentDays()).thenReturn(3);
        this.buy("delivery / a rent offer of furniture only", rentFurni, 1, "", false);

        this.createItemFails = true;
        this.buy("delivery / furniture that cannot be created", bundle, 1, "", false);
    }

    // ---------------------------------------------------------------- the driver

    private void buy(String label, CatalogItem item, int amount, String extradata, boolean free) {
        this.trace.clear();
        this.recording = true;
        try {
            this.manager.purchaseItem(this.page(), item, this.habbo, amount, extradata, free);
            this.trace.add("returned");
        } catch (Exception e) {
            this.trace.add("threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            this.recording = false;
        }
        this.trace.add("purchasing flag " + this.stats.isPurchasingFurniture);
        this.check(label, String.join("\n", this.trace));
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
        if (value instanceof Map<?, ?> map) {
            return new TreeMap<>(map.entrySet().stream()
                            .collect(Collectors.toMap(e -> String.valueOf(e.getKey()), e -> this.label(e.getValue()))))
                    .toString();
        }
        if (value instanceof List<?> list) {
            // Lists built from hash sets have no stable order: compare them sorted.
            return list.stream()
                    .map(this::label)
                    .sorted()
                    .collect(Collectors.toList())
                    .toString();
        }
        if (org.mockito.Mockito.mockingDetails(value).isMock()) {
            String name = org.mockito.Mockito.mockingDetails(value)
                    .getMockCreationSettings()
                    .getMockName()
                    .toString();
            if (!name.startsWith("Mock for")) return name;
        }
        if (value instanceof Enum<?> e) return e.name();
        if (value instanceof com.eu.habbo.habbohotel.users.HabboBadge badge) return "badge " + badge.getCode();
        if (value instanceof Number || value instanceof String || value instanceof Boolean)
            return String.valueOf(value);
        return value.getClass().getSimpleName().replaceAll("\\$MockitoMock\\$.*", "");
    }

    /** Escapes tabs and line breaks, and puts {@code <date>} where today's date was engraved. */
    private static String printable(String text) {
        if (text == null) return "null";
        java.util.Calendar now = java.util.Calendar.getInstance();
        int day = now.get(java.util.Calendar.DAY_OF_MONTH);
        int month = now.get(java.util.Calendar.MONTH) + 1;
        int year = now.get(java.util.Calendar.YEAR);
        return text.replace(day + "-" + month + "-" + year, "<date>")
                .replace("\n" + day + "\n" + month + "\n" + year + "\n", "\n<date>\n")
                .replace("\t", "\\t")
                .replace("\n", "\\n");
    }

    private HabboItem createdItem(Item base) {
        Class<?> type = base.getInteractionType().getType();
        HabboItem item;
        if (type == InteractionGuildFurni.class) {
            item = mock(InteractionGuildFurni.class);
        } else if (type == InteractionMusicDisc.class) {
            item = mock(InteractionMusicDisc.class);
        } else {
            item = mock(HabboItem.class);
        }
        int id = this.nextItemId++;
        when(item.getId()).thenReturn(id);
        when(item.getBaseItem()).thenReturn(base);
        this.labels.put(item, "item#" + id);
        doAnswer(i -> this.record("item#" + id + ".setExtradata(\"" + printable(i.getArgument(0)) + "\")"))
                .when(item)
                .setExtradata(anyString());
        doAnswer(i -> this.record("item#" + id + ".setExpiresTimestamp(" + i.getArgument(0) + ")"))
                .when(item)
                .setExpiresTimestamp(anyInt());
        return item;
    }

    private CatalogPage page() {
        CatalogPage page = mock(CatalogPage.class);
        when(page.getCaption()).thenReturn("Shop");
        return page;
    }

    private CatalogItem offer(int id, String name, int credits, int points, Item... baseItems) {
        CatalogItem item = mock(CatalogItem.class);
        when(item.getId()).thenReturn(id);
        when(item.getName()).thenReturn(name);
        when(item.getCredits()).thenReturn(credits);
        when(item.getPoints()).thenReturn(points);
        when(item.getPointsType()).thenReturn(5);
        when(item.getAmount()).thenReturn(1);
        when(item.getExtradata()).thenReturn("");
        when(item.getBaseItems()).thenReturn(new java.util.LinkedHashSet<>(Arrays.asList(baseItems)));
        for (Item base : baseItems) when(item.getItemAmount(base.getId())).thenReturn(1);
        this.labels.put(item, "offer " + name);
        return item;
    }

    private static Item base(int id, String name, FurnitureType type) {
        return base(id, name, type, InteractionDefault.class);
    }

    private static Item base(int id, String name, FurnitureType type, Class<? extends HabboItem> interaction) {
        Item item = mock(Item.class, withSettings().name(name));
        ItemInteraction itemInteraction = mock(ItemInteraction.class);
        when(itemInteraction.getType()).thenAnswer(i -> interaction);
        when(item.getId()).thenReturn(id);
        when(item.getName()).thenReturn(name);
        when(item.getDisplayName()).thenReturn(name + " display");
        when(item.getType()).thenReturn(type);
        when(item.getInteractionType()).thenReturn(itemInteraction);
        return item;
    }

    private CatalogLimitedConfiguration limitedConfig(int itemId, int available) {
        CatalogLimitedConfiguration config = mock(CatalogLimitedConfiguration.class);
        when(config.available()).thenReturn(available);
        when(config.getTotalSet()).thenReturn(100);
        doAnswer(i -> this.record("limited.restoreNumber(" + i.getArgument(0) + ", " + i.getArgument(1) + ")"))
                .when(config)
                .restoreNumber(anyInt(), anyInt());
        doAnswer(i -> this.record("limited.restoreNumber(" + i.getArgument(0) + ")"))
                .when(config)
                .restoreNumber(anyInt());
        this.manager.limitedNumbers.put(itemId, config);
        return config;
    }

    private Habbo buyer() {
        Habbo buyer = mock(Habbo.class);
        this.info = mock(HabboInfo.class);
        this.stats = mock(HabboStats.class);
        Rank rank = mock(Rank.class);
        when(rank.getLevel()).thenReturn(1);
        when(this.info.getId()).thenReturn(7);
        when(this.info.getUsername()).thenReturn("buyer");
        when(this.info.getCredits()).thenReturn(1000);
        when(this.info.getCurrencyAmount(anyInt())).thenReturn(100);
        when(this.info.getGender()).thenReturn(HabboGender.M);
        when(this.info.getRank()).thenReturn(rank);
        when(this.stats.hasActiveClub()).thenAnswer(i -> this.hasClub);
        when(this.stats.totalLtds()).thenAnswer(i -> this.ltdsToday);
        when(this.stats.totalLtds(anyInt())).thenAnswer(i -> this.ltdsToday);
        doAnswer(i -> this.record("stats.addLtdLog(" + i.getArgument(0) + ", " + i.getArgument(1) + ")"))
                .when(this.stats)
                .addLtdLog(anyInt(), anyInt());
        doAnswer(i -> this.record("stats.addPurchase(" + this.label(i.getArgument(0)) + ")"))
                .when(this.stats)
                .addPurchase(any());

        GameClient client = mock(GameClient.class);
        doAnswer(i -> this.record("buyer <- " + this.label(i.getArgument(0))))
                .when(client)
                .sendResponse(any(ServerMessage.class));
        doAnswer(i -> this.record("buyer <- " + this.label(i.getArgument(0))))
                .when(client)
                .sendResponse(any(MessageComposer.class));

        HabboInventory inventory = mock(HabboInventory.class);
        BotsComponent botsComponent = mock(BotsComponent.class);
        doAnswer(i -> this.record("inventory.addBot(" + this.label(i.getArgument(0)) + ")"))
                .when(botsComponent)
                .addBot(any());
        doAnswer(i -> this.record("inventory.removeBot(" + this.label(i.getArgument(0)) + ")"))
                .when(botsComponent)
                .removeBot(any());
        PetsComponent petsComponent = mock(PetsComponent.class);
        doAnswer(i -> this.record("inventory.addPet(" + this.label(i.getArgument(0)) + ")"))
                .when(petsComponent)
                .addPet(any());
        doAnswer(i -> this.record("inventory.removePet(" + this.label(i.getArgument(0)) + ")"))
                .when(petsComponent)
                .removePet(any());
        BadgesComponent badgesComponent = mock(BadgesComponent.class);
        when(badgesComponent.hasBadge(anyString()))
                .thenAnswer(i -> this.ownedBadges.contains(i.<String>getArgument(0)));
        doAnswer(i -> this.record("inventory.addBadge(" + this.label(i.getArgument(0)) + ")"))
                .when(badgesComponent)
                .addBadge(any());
        EffectsComponent effectsComponent = mock(EffectsComponent.class);
        when(effectsComponent.ownsPermanently(anyInt()))
                .thenAnswer(i -> this.permanentEffects.contains(i.<Integer>getArgument(0)));
        doAnswer(i -> this.record("inventory.createEffect(" + i.getArgument(0) + ", " + i.getArgument(1) + ")"))
                .when(effectsComponent)
                .createEffect(anyInt(), anyInt());
        ItemsComponent itemsComponent = mock(ItemsComponent.class);
        doAnswer(i -> this.record("inventory.addItems("
                        + ((java.util.Collection<?>) i.getArgument(0))
                                .stream().map(this::label).sorted().collect(Collectors.toList())
                        + ")"))
                .when(itemsComponent)
                .addItems(any());
        when(inventory.getBotsComponent()).thenReturn(botsComponent);
        when(inventory.getPetsComponent()).thenReturn(petsComponent);
        when(inventory.getBadgesComponent()).thenReturn(badgesComponent);
        when(inventory.getEffectsComponent()).thenReturn(effectsComponent);
        when(inventory.getItemsComponent()).thenReturn(itemsComponent);

        when(buyer.getClient()).thenReturn(client);
        when(client.getHabbo()).thenReturn(buyer);
        when(buyer.getHabboInfo()).thenReturn(this.info);
        when(buyer.getHabboStats()).thenReturn(this.stats);
        when(buyer.getInventory()).thenReturn(inventory);
        when(buyer.hasPermission(anyString())).thenAnswer(i -> this.permissions.contains(i.<String>getArgument(0)));
        doAnswer(i -> this.record("buyer alerted \"" + i.getArgument(0) + "\""))
                .when(buyer)
                .alert(anyString());
        return buyer;
    }

    // ---------------------------------------------------------------- the expected traces

    private static final String UPDATE_PROPERTY = "polaris.purchaseItemTraces.update";
    private static final Path TRACES = Path.of("src/test/resources/catalog/purchase-item-traces.txt");
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
