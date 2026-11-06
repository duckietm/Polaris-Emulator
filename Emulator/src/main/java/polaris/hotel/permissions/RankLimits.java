package polaris.hotel.permissions;

import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.messenger.Messenger;
import polaris.hotel.rooms.RoomManager;
import polaris.hotel.users.User;

/**
 * How many rooms, friends and favourite rooms a user may have. The hotel settings stay the default
 * (with the Habbo Club values for members); a rank can raise them through permission_ranks
 * (max_rooms, max_friends, max_favourite_rooms, 0 = the hotel setting), never lower them.
 */
public final class RankLimits {
    public static final String FAVOURITES_SETTING = "hotel.rooms.max.favorite";
    public static final int DEFAULT_FAVOURITES = 30;

    private RankLimits() {}

    public static int rooms(User user) {
        boolean club = user.getUserStats() != null && user.getUserStats().hasActiveClub();
        return raise(club ? RoomManager.MAXIMUM_ROOMS_HC : RoomManager.MAXIMUM_ROOMS_USER, rankOf(user), Limit.ROOMS);
    }

    /** The user's own friend limit (set by Habbo Club), raised by the rank. */
    public static int friends(User user) {
        int base = user.getUserStats() != null ? user.getUserStats().maxFriends : Messenger.MAXIMUM_FRIENDS;
        return raise(base, rankOf(user), Limit.FRIENDS);
    }

    /** The friend limit of a user who is not loaded: their stored max_friends, raised by their rank. */
    public static int friends(int maxFriends, Rank rank) {
        return raise(maxFriends > 0 ? maxFriends : Messenger.MAXIMUM_FRIENDS, rank, Limit.FRIENDS);
    }

    /** The friend limits the messenger shows: without and with Habbo Club. */
    public static int friendsShown(User user, boolean club) {
        return raise(club ? Messenger.MAXIMUM_FRIENDS_HC : Messenger.MAXIMUM_FRIENDS, rankOf(user), Limit.FRIENDS);
    }

    public static int favouriteRooms(Rank rank) {
        ConfigurationManager config = WiredPlatform.configuration();
        int base = config != null ? config.getInt(FAVOURITES_SETTING, DEFAULT_FAVOURITES) : DEFAULT_FAVOURITES;
        return raise(base, rank, Limit.FAVOURITE_ROOMS);
    }

    enum Limit {
        ROOMS,
        FRIENDS,
        FAVOURITE_ROOMS
    }

    static int raise(int hotelDefault, Rank rank, Limit limit) {
        if (rank == null) {
            return hotelDefault;
        }

        int rankValue =
                switch (limit) {
                    case ROOMS -> rank.getMaxRooms();
                    case FRIENDS -> rank.getMaxFriends();
                    case FAVOURITE_ROOMS -> rank.getMaxFavouriteRooms();
                };

        return Math.max(hotelDefault, rankValue);
    }

    private static Rank rankOf(User user) {
        return user == null || user.getUserInfo() == null
                ? null
                : user.getUserInfo().getRank();
    }
}
