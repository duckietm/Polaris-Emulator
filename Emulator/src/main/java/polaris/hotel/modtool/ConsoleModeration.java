package polaris.hotel.modtool;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;

/**
 * Bans and unbans from outside the hotel (RCON {@code banuser} / {@code unbanuser}, used by the All-in-1-Manager). The
 * caller is the hotel's own console, so there is no moderator and no rank check: the ban is stored with no staff member,
 * which every moderator may lift. An online player sees the ban and is disconnected, as after a modtool ban. Installed
 * at start-up; until then nothing can be done.
 */
public final class ConsoleModeration {
    private static final AtomicReference<ConsoleModeration> INSTALLED = new AtomicReference<>();

    /** The longest ban: 50 years, which still fits a timestamp. */
    public static final int MAX_BAN_SECONDS = 50 * 365 * 24 * 60 * 60;

    public enum BanOutcome {
        BANNED,
        USER_NOT_FOUND,
        FAILED
    }

    /** Stores a ban with no staff member: user, seconds, reason, type. Null when it could not be stored. */
    @FunctionalInterface
    public interface BanWriter {
        ModToolBan write(int userId, int durationSeconds, String reason, ModToolBanType type);
    }

    private final IntFunction<UserInfo> userInfo;
    private final IntFunction<User> onlineUser;
    private final BanWriter writer;
    private final BiConsumer<User, ModToolBan> removeBanned;
    private final IntConsumer evictParkedSession;
    private final IntPredicate unban;

    /**
     * @param userInfo a user's info, online or offline; null when there is no such user
     * @param onlineUser the user when online, else null
     * @param writer stores the ban
     * @param removeBanned shows an online user the ban and disconnects them
     * @param evictParkedSession drops a session kept for a reconnect, so a ban cannot be resumed past
     * @param unban lifts every active ban of a user; false when there was none
     */
    public ConsoleModeration(
            IntFunction<UserInfo> userInfo,
            IntFunction<User> onlineUser,
            BanWriter writer,
            BiConsumer<User, ModToolBan> removeBanned,
            IntConsumer evictParkedSession,
            IntPredicate unban) {
        this.userInfo = Objects.requireNonNull(userInfo, "userInfo");
        this.onlineUser = Objects.requireNonNull(onlineUser, "onlineUser");
        this.writer = Objects.requireNonNull(writer, "writer");
        this.removeBanned = Objects.requireNonNull(removeBanned, "removeBanned");
        this.evictParkedSession = Objects.requireNonNull(evictParkedSession, "evictParkedSession");
        this.unban = Objects.requireNonNull(unban, "unban");
    }

    public static void install(ConsoleModeration moderation) {
        INSTALLED.set(Objects.requireNonNull(moderation, "moderation"));
    }

    /** Removes the installed instance (tests). */
    public static void uninstall() {
        INSTALLED.set(null);
    }

    /** The installed instance, or null before start-up finished. */
    public static ConsoleModeration installed() {
        return INSTALLED.get();
    }

    public BanOutcome ban(int userId, int durationSeconds, String reason, ModToolBanType type) {
        UserInfo info = userId > 0 ? this.userInfo.apply(userId) : null;
        if (info == null) return BanOutcome.USER_NOT_FOUND;

        ModToolBanType stored = storedType(type, info.getIpLogin(), info.getMachineID());
        ModToolBan ban = this.writer.write(userId, durationSeconds, reason, stored);
        if (ban == null) return BanOutcome.FAILED;

        User online = this.onlineUser.apply(userId);
        if (online != null) {
            this.removeBanned.accept(online, ban);
        }
        this.evictParkedSession.accept(userId);
        return BanOutcome.BANNED;
    }

    /** Lifts the user's active bans; false when there was no such user or no active ban. */
    public boolean unban(int userId) {
        return userId > 0 && this.unban.test(userId);
    }

    /**
     * The type that is stored, as a modtool ban does it: a machine ban without a known machine becomes an address ban,
     * an address ban without a known address an account ban. An empty address in the bans table would match every
     * account without one.
     */
    static ModToolBanType storedType(ModToolBanType type, String ip, String machineId) {
        ModToolBanType stored = type == null ? ModToolBanType.ACCOUNT : type;
        if ((stored == ModToolBanType.MACHINE || stored == ModToolBanType.SUPER) && isBlank(machineId)) {
            stored = ModToolBanType.IP;
        }
        if ((stored == ModToolBanType.IP || stored == ModToolBanType.SUPER) && isBlank(ip)) {
            stored = ModToolBanType.ACCOUNT;
        }
        return stored;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
