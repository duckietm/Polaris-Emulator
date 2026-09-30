package com.eu.habbo.habbohotel.permissions;

import com.eu.habbo.WiredPlatform;
import com.eu.habbo.core.ConfigurationManager;
import com.eu.habbo.habbohotel.messenger.Messenger;
import com.eu.habbo.habbohotel.rooms.RoomManager;
import com.eu.habbo.habbohotel.users.Habbo;

/**
 * How many rooms, friends and favourite rooms a user may have. The hotel settings stay the default
 * (with the Habbo Club values for members); a rank can raise them through permission_ranks
 * (max_rooms, max_friends, max_favourite_rooms, 0 = the hotel setting), never lower them.
 */
public final class RankLimits {
    public static final String FAVOURITES_SETTING = "hotel.rooms.max.favorite";
    public static final int DEFAULT_FAVOURITES = 30;

    private RankLimits() {}

    public static int rooms(Habbo habbo) {
        boolean club = habbo.getHabboStats() != null && habbo.getHabboStats().hasActiveClub();
        return raise(club ? RoomManager.MAXIMUM_ROOMS_HC : RoomManager.MAXIMUM_ROOMS_USER, rankOf(habbo), Limit.ROOMS);
    }

    /** The user's own friend limit (set by Habbo Club), raised by the rank. */
    public static int friends(Habbo habbo) {
        int base = habbo.getHabboStats() != null ? habbo.getHabboStats().maxFriends : Messenger.MAXIMUM_FRIENDS;
        return raise(base, rankOf(habbo), Limit.FRIENDS);
    }

    /** The friend limits the messenger shows: without and with Habbo Club. */
    public static int friendsShown(Habbo habbo, boolean club) {
        return raise(club ? Messenger.MAXIMUM_FRIENDS_HC : Messenger.MAXIMUM_FRIENDS, rankOf(habbo), Limit.FRIENDS);
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

    private static Rank rankOf(Habbo habbo) {
        return habbo == null || habbo.getHabboInfo() == null
                ? null
                : habbo.getHabboInfo().getRank();
    }
}
