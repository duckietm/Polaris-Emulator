package polaris.hotel.quests;

import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.bots.Bot;
import polaris.hotel.items.FurnitureType;
import polaris.hotel.items.Item;
import polaris.hotel.items.ItemManager;
import polaris.hotel.pets.Pet;
import polaris.hotel.pets.PetPackage;
import polaris.hotel.users.User;
import polaris.hotel.users.UserBadge;
import polaris.hotel.users.UserItem;
import polaris.hotel.users.inventory.BadgesComponent;
import polaris.messages.outgoing.inventory.AddBotComposer;
import polaris.messages.outgoing.inventory.AddPetComposer;
import polaris.messages.outgoing.inventory.AddUserItemComposer;
import polaris.messages.outgoing.inventory.InventoryRefreshComposer;
import polaris.messages.outgoing.quests.ActiveDailyTasksComposer;
import polaris.messages.outgoing.users.AddUserBadgeComposer;

/** Hands out the currency, badge and furni rewards of quests, daily tasks and reward-track prizes. */
public final class QuestRewards {
    private static final Logger LOGGER = LoggerFactory.getLogger(QuestRewards.class);

    public static final String TYPE_CREDITS = "credits";
    public static final String TYPE_DUCKETS = "duckets";
    public static final String TYPE_DIAMONDS = "diamonds";
    public static final String TYPE_BADGE = "badge";

    /** A furni: the extra parameter is the items_base name, the amount is how many. */
    public static final String TYPE_FURNI = "furni";
    /** A Habbicon; the extra parameter is its id. */
    public static final String TYPE_HABBICON = "habbicon";

    /** A bot or a pet, described by the extra parameters; see {@link CompanionPrize}. */
    public static final String TYPE_BOT = "bot";

    public static final String TYPE_PET = "pet";

    /** How many copies one prize may hand out at once. */
    public static final int MAX_FURNI_PER_PRIZE = 10;

    public static final int DIAMONDS_POINT_TYPE = 5;

    private static final String REASON = "quests.reward";

    private QuestRewards() {}

    /** The official rule: HC members earn double duckets from quests. */
    public static int ducketAmount(int amount, boolean hasClub) {
        return hasClub ? amount * 2 : amount;
    }

    /** activityPointType style rewards: -1 credits, otherwise the points type (0 duckets, 5 diamonds). */
    public static void grantActivityPoints(User user, int activityPointType, int amount) {
        grantActivityPoints(user, activityPointType, amount, null);
    }

    /** With an operation id the ledger applies the grant at most once; null uses a fresh id. */
    public static void grantActivityPoints(User user, int activityPointType, int amount, String operationId) {
        if (user == null || amount < 1) {
            return;
        }
        if (activityPointType == Quest.REWARD_CREDITS) {
            if (operationId == null) {
                user.giveCredits(amount, REASON);
            } else {
                user.giveCredits(amount, REASON, operationId, user.getUserInfo().getId());
            }
            return;
        }
        int granted = activityPointType == Quest.REWARD_DUCKETS
                ? ducketAmount(amount, user.getUserStats().hasActiveClub())
                : amount;
        if (operationId == null) {
            user.givePoints(activityPointType, granted, REASON);
        } else {
            user.givePoints(
                    activityPointType,
                    granted,
                    REASON,
                    operationId,
                    user.getUserInfo().getId());
        }
    }

    /** String reward types used by the daily tasks and the reward track. */
    public static void grantTyped(User user, String rewardType, String extra, int amount) {
        grantTyped(user, rewardType, extra, amount, null);
    }

    /** As above; the operation id makes a currency grant idempotent in the ledger. */
    public static void grantTyped(User user, String rewardType, String extra, int amount, String operationId) {
        if (user == null || rewardType == null) {
            return;
        }
        switch (rewardType.trim().toLowerCase()) {
            case TYPE_CREDITS -> grantActivityPoints(user, Quest.REWARD_CREDITS, amount, operationId);
            case TYPE_DUCKETS -> grantActivityPoints(user, Quest.REWARD_DUCKETS, amount, operationId);
            case TYPE_DIAMONDS -> grantActivityPoints(user, DIAMONDS_POINT_TYPE, amount, operationId);
            case TYPE_BADGE -> grantBadge(user, extra);
            case TYPE_FURNI -> grantFurni(user, extra, amount);
            case TYPE_HABBICON -> grantHabbicon(user, extra);
            case TYPE_BOT -> grantBot(user, extra);
            case TYPE_PET -> grantPet(user, extra);
            default -> LOGGER.warn("Unknown quest reward type {}", rewardType);
        }
    }

    /** The Habbicon id a prize names in its extra parameter, or 0. */
    public static int habbiconIdOf(String extra) {
        try {
            return extra == null ? 0 : Math.max(0, Integer.parseInt(extra.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Gives the Habbicon a prize names; one the user already owns is left as it is. */
    public static void grantHabbicon(User user, String extra) {
        int id = habbiconIdOf(extra);
        if (user == null || id <= 0 || user.getHabbiconService() == null) {
            return;
        }
        try {
            user.getHabbiconService().grantPrize(user, id);
        } catch (Exception e) {
            LOGGER.warn("Quest reward Habbicon {} was not given: {}", id, e.getMessage());
        }
    }

    /** Creates the bot a prize describes and puts it in the user's bot inventory, as a bought bot arrives. */
    public static void grantBot(User user, String extra) {
        CompanionPrize.Bot prize = CompanionPrize.bot(extra);
        if (user == null || user.getClient() == null || prize == null) {
            if (prize == null) LOGGER.warn("Quest reward bot has no figure: {}", extra);
            return;
        }

        Bot bot = Emulator.getGameEnvironment()
                .getBotManager()
                .createBot(prize.data(), prize.type(), user.getUserInfo().getId());
        if (bot == null) {
            LOGGER.warn("Quest reward bot of type {} was not created", prize.type());
            return;
        }

        bot.setOwnerId(user.getUserInfo().getId());
        bot.setOwnerName(user.getUserInfo().getUsername());
        user.getInventory().getBotsComponent().addBot(bot);
        user.getClient().sendResponse(new AddBotComposer(bot));
        user.getClient()
                .sendResponse(new AddUserItemComposer(bot.getId(), AddUserItemComposer.AddUserItemCategory.BOT));
    }

    /** Creates the pet a prize describes and puts it in the user's pet inventory, as a bought pet arrives. */
    public static void grantPet(User user, String extra) {
        CompanionPrize.Pet prize = CompanionPrize.pet(extra);
        if (user == null || user.getClient() == null || prize == null) {
            if (prize == null) LOGGER.warn("Quest reward pet is not \"<type> <race> <colour> [name]\": {}", extra);
            return;
        }

        // A pet without a name comes as its package, holding the pet: the winner places it and names the pet.
        if (prize.name() == null && grantPetPackage(user, prize)) {
            return;
        }

        Pet pet = Emulator.getGameEnvironment()
                .getPetManager()
                .createPet(prize.type(), prize.race(), prize.nameOrDefault(), user.getClient());
        if (pet == null) {
            LOGGER.warn("Quest reward pet of type {} does not exist", prize.type());
            return;
        }

        pet.setColor(prize.color());
        pet.needsUpdate = true;
        Emulator.getThreading().run(pet);
        user.getInventory().getPetsComponent().addPet(pet);
        user.getClient().sendResponse(new AddPetComposer(pet));
        user.getClient()
                .sendResponse(new AddUserItemComposer(pet.getId(), AddUserItemComposer.AddUserItemCategory.PET));
    }

    /** Gives the official package of the pet's type with the pet in its state; false when the hotel has none. */
    private static boolean grantPetPackage(User user, CompanionPrize.Pet prize) {
        String packageName = PetPackage.packageNameFor(prize.type());
        Item definition = packageName == null ? null : items().getItem(packageName);
        if (definition == null || PetPackage.of(definition) == null) {
            return false;
        }

        UserItem box = items().createItem(user.getUserInfo().getId(), definition, 0, 0, prize.figure());
        if (box == null) {
            return false;
        }

        user.getInventory().getItemsComponent().addItem(box);
        user.getClient().sendResponse(new AddUserItemComposer(box));
        user.getClient().sendResponse(new InventoryRefreshComposer());
        return true;
    }

    private static ItemManager items() {
        return Emulator.getGameEnvironment().getItemManager();
    }

    /** Puts {@code amount} copies of the furni named {@code itemName} in the user's hand. */
    public static void grantFurni(User user, String itemName, int amount) {
        if (user == null || user.getClient() == null) {
            return;
        }
        Item item = Emulator.getGameEnvironment().getItemManager().getItem(itemName);
        if (item == null) {
            LOGGER.warn("Quest reward furni {} does not exist", itemName);
            return;
        }
        int copies = Math.max(1, Math.min(amount, MAX_FURNI_PER_PRIZE));
        for (int i = 0; i < copies; i++) {
            UserItem userItem = Emulator.getGameEnvironment()
                    .getItemManager()
                    .createItem(user.getUserInfo().getId(), item, 0, 0, "");
            if (userItem == null) {
                continue;
            }
            user.getInventory().getItemsComponent().addItem(userItem);
            user.getClient().sendResponse(new AddUserItemComposer(userItem));
        }
        user.getClient().sendResponse(new InventoryRefreshComposer());
    }

    /** AIR ProductIconWidget product types a daily task reward is drawn with. */
    public static final int PRODUCT_WALL = 0;

    public static final int PRODUCT_FLOOR = 1;
    public static final int PRODUCT_BADGE = 4;
    public static final int PRODUCT_BOT = 6;
    public static final int PRODUCT_CURRENCY = 8;
    public static final int PRODUCT_PET = 10;
    public static final int PRODUCT_HABBICON = 12;
    public static final int PRODUCT_UNKNOWN = -1;

    /**
     * A daily task reward as AIR's DailyTaskRewardView reads it: the product type, then the item type (a currency
     * type, a badge code or a furni class id). A badge keeps its code in the extra params too, for older clients.
     * A furni that does not exist, or an unknown reward type, is sent as the unknown product.
     */
    public static ActiveDailyTasksComposer.Reward dailyTaskRewardWire(
            String rewardType, String extra, int amount, Function<String, Item> itemLookup) {
        String type = rewardType == null ? "" : rewardType.trim().toLowerCase();
        int currency = activityPointTypeOf(type);

        if (currency != -2) {
            return new ActiveDailyTasksComposer.Reward(PRODUCT_CURRENCY, String.valueOf(currency), "", amount);
        }

        if (TYPE_BADGE.equals(type)) {
            String code = extra == null ? "" : extra;
            return new ActiveDailyTasksComposer.Reward(PRODUCT_BADGE, code, code, amount);
        }

        if (TYPE_HABBICON.equals(type) && habbiconIdOf(extra) > 0) {
            return new ActiveDailyTasksComposer.Reward(
                    PRODUCT_HABBICON, String.valueOf(habbiconIdOf(extra)), extra.trim(), amount);
        }

        // A bot and a pet are drawn from their figure, which travels as the extra parameter.
        CompanionPrize.Bot bot = TYPE_BOT.equals(type) ? CompanionPrize.bot(extra) : null;
        if (bot != null) {
            return new ActiveDailyTasksComposer.Reward(PRODUCT_BOT, "", bot.figure(), amount);
        }

        CompanionPrize.Pet pet = TYPE_PET.equals(type) ? CompanionPrize.pet(extra) : null;
        if (pet != null) {
            return new ActiveDailyTasksComposer.Reward(PRODUCT_PET, String.valueOf(pet.type()), pet.figure(), amount);
        }

        if (TYPE_FURNI.equals(type)) {
            Item item = (itemLookup == null || extra == null) ? null : itemLookup.apply(extra);
            if (item != null) {
                int product = item.getType() == FurnitureType.WALL ? PRODUCT_WALL : PRODUCT_FLOOR;
                return new ActiveDailyTasksComposer.Reward(product, String.valueOf(item.getSpriteId()), extra, amount);
            }
            LOGGER.warn("Daily task reward furni {} does not exist", extra);
        }

        return new ActiveDailyTasksComposer.Reward(PRODUCT_UNKNOWN, rewardType == null ? "" : rewardType, "", amount);
    }

    /** Maps the string reward type to the client activityPointType, -2 when it is not a currency. */
    public static int activityPointTypeOf(String rewardType) {
        if (rewardType == null) {
            return -2;
        }
        return switch (rewardType.trim().toLowerCase()) {
            case TYPE_CREDITS -> Quest.REWARD_CREDITS;
            case TYPE_DUCKETS -> Quest.REWARD_DUCKETS;
            case TYPE_DIAMONDS -> DIAMONDS_POINT_TYPE;
            default -> -2;
        };
    }

    public static void grantBadge(User user, String badgeCode) {
        if (user == null || badgeCode == null || badgeCode.isBlank()) {
            return;
        }
        if (user.getInventory().getBadgesComponent().hasBadge(badgeCode)) {
            return;
        }
        UserBadge badge = BadgesComponent.createBadge(badgeCode, user);
        if (badge != null && user.getClient() != null) {
            user.getClient().sendResponse(new AddUserBadgeComposer(badge));
        }
    }
}
