package polaris.messages.rcon;

import com.google.gson.Gson;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import polaris.WiredPlatform;
import polaris.hotel.GameEnvironment;
import polaris.hotel.commands.UpdateAchievementsCommand;
import polaris.hotel.commands.UpdateBotsCommand;
import polaris.hotel.commands.UpdateCalendarCommand;
import polaris.hotel.commands.UpdateCatalogCommand;
import polaris.hotel.commands.UpdateChatBubblesCommand;
import polaris.hotel.commands.UpdateConfigCommand;
import polaris.hotel.commands.UpdateGuildPartsCommand;
import polaris.hotel.commands.UpdateHotelViewCommand;
import polaris.hotel.commands.UpdateItemsCommand;
import polaris.hotel.commands.UpdateNavigatorCommand;
import polaris.hotel.commands.UpdatePermissionsCommand;
import polaris.hotel.commands.UpdatePetDataCommand;
import polaris.hotel.commands.UpdatePollsCommand;
import polaris.hotel.commands.UpdateRewardTracksCommand;
import polaris.hotel.commands.UpdateTextsCommand;
import polaris.hotel.commands.UpdateWordFilterCommand;
import polaris.hotel.commands.UpdateYoutubePlaylistsCommand;
import polaris.hotel.modtool.HousekeepingAuditLog;
import polaris.messages.incoming.housekeeping.HousekeepingOperator;

/**
 * RCON {@code reloadcontent}: reloads one kind of hotel content the way its in-game
 * {@code :update_*} command does (each command's static {@code reload}), without a staff member
 * to whisper to. Quests and daily tasks, which have no command, reload through their managers.
 * Audited as {@code hotel.reload.<target>} by the All-in-1 Manager.
 */
public class ReloadContent extends RCONMessage<ReloadContent.JSON> {
    public static final String ACHIEVEMENTS = "achievements";
    public static final String BOTS = "bots";
    public static final String CALENDAR = "calendar";
    public static final String CATALOG = "catalog";
    public static final String CHAT_BUBBLES = "chatbubbles";
    public static final String CONFIG = "config";
    public static final String DAILY_TASKS = "dailytasks";
    public static final String DISCOUNTS = "discounts";
    public static final String GUILD_PARTS = "guildparts";
    public static final String HOTEL_VIEW = "hotelview";
    public static final String ITEMS = "items";
    public static final String NAVIGATOR = "navigator";
    public static final String PERMISSIONS = "permissions";
    public static final String PET_DATA = "petdata";
    public static final String POLLS = "polls";
    public static final String QUESTS = "quests";
    public static final String REWARD_TRACKS = "rewardtracks";
    public static final String TEXTS = "texts";
    public static final String WORD_FILTER = "wordfilter";
    public static final String YOUTUBE = "youtube";

    /** Every target, in the order the error message lists them. */
    public static final List<String> TARGETS = List.of(
            ACHIEVEMENTS,
            BOTS,
            CALENDAR,
            CATALOG,
            CHAT_BUBBLES,
            CONFIG,
            DAILY_TASKS,
            DISCOUNTS,
            GUILD_PARTS,
            HOTEL_VIEW,
            ITEMS,
            NAVIGATOR,
            PERMISSIONS,
            PET_DATA,
            POLLS,
            QUESTS,
            REWARD_TRACKS,
            TEXTS,
            WORD_FILTER,
            YOUTUBE);

    static final String CATALOG_COOLDOWN = "The catalog was reloaded less than 5 seconds ago; try again shortly.";

    private final Supplier<GameEnvironment> environment;

    public ReloadContent() {
        this(WiredPlatform::gameEnvironment);
    }

    ReloadContent(Supplier<GameEnvironment> environment) {
        super(JSON.class);
        this.environment = environment;
    }

    @Override
    public void handle(Gson gson, JSON json) {
        String target = normalize(json.target);

        if (!TARGETS.contains(target)) {
            this.status = STATUS_ERROR;
            this.message = unknownTarget(TARGETS);
            return;
        }

        String error;

        try {
            error = reload(target, this.environment);
        } catch (Exception e) {
            this.status = SYSTEM_ERROR;
            this.message = "The " + target + " could not be reloaded.";
            return;
        }

        if (error != null) {
            this.status = STATUS_ERROR;
            this.message = error;
        }
    }

    /** "Pet_Data" and "pet-data" are "petdata". */
    static String normalize(String target) {
        return target == null
                ? ""
                : target.trim().toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    static String unknownTarget(List<String> targets) {
        return "Unknown target. Use one of: " + String.join(", ", targets);
    }

    /**
     * Reloads one of {@link #TARGETS} and audits it. Returns null when it went through, or why it did
     * not. Shared with the housekeeping command's hotel.reload action.
     */
    static String reload(String target, Supplier<GameEnvironment> environment) throws Exception {
        String error = run(target, environment);

        if (error == null) {
            HousekeepingAuditLog.log(
                    HousekeepingOperator.SYSTEM_ID,
                    HousekeepingOperator.SYSTEM_NAME,
                    "hotel.reload." + target,
                    HousekeepingAuditLog.TARGET_HOTEL,
                    0,
                    "",
                    "target=" + target,
                    HousekeepingOperator.SYSTEM_IP);
        }

        return error;
    }

    private static String run(String target, Supplier<GameEnvironment> environment) throws Exception {
        switch (target) {
            case ACHIEVEMENTS -> UpdateAchievementsCommand.reload();
            case BOTS -> {
                if (!UpdateBotsCommand.reload()) return "The bots could not be reloaded.";
            }
            case CALENDAR -> UpdateCalendarCommand.reload();
            case CATALOG -> {
                if (!UpdateCatalogCommand.reload()) return CATALOG_COOLDOWN;
            }
            case CHAT_BUBBLES -> UpdateChatBubblesCommand.reload();
            case CONFIG -> UpdateConfigCommand.reload();
            case DAILY_TASKS -> environment.get().getDailyTaskManager().reload();
            case DISCOUNTS -> UpdateCatalogCommand.reloadDiscounts();
            case GUILD_PARTS -> UpdateGuildPartsCommand.reload();
            case HOTEL_VIEW -> UpdateHotelViewCommand.reload();
            case ITEMS -> UpdateItemsCommand.reload();
            case NAVIGATOR -> UpdateNavigatorCommand.reload();
            case PERMISSIONS ->
                UpdatePermissionsCommand.reload(HousekeepingOperator.SYSTEM_ID, HousekeepingOperator.SYSTEM_NAME);
            case PET_DATA -> UpdatePetDataCommand.reload();
            case POLLS -> UpdatePollsCommand.reload();
            case QUESTS -> environment.get().getQuestManager().reload();
            case REWARD_TRACKS -> UpdateRewardTracksCommand.reloadAndBroadcast();
            case TEXTS -> UpdateTextsCommand.reload();
            case WORD_FILTER -> UpdateWordFilterCommand.reload();
            case YOUTUBE -> UpdateYoutubePlaylistsCommand.reload();
            default -> {
                return unknownTarget(TARGETS);
            }
        }

        return null;
    }

    static class JSON {
        @NotBlank(message = "target is required")
        public String target;
    }
}
