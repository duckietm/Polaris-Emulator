package com.eu.habbo.habbohotel.navigation;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.rooms.Room;
import com.eu.habbo.habbohotel.users.Habbo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NavigatorManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NavigatorManager.class);

    public static int MAXIMUM_RESULTS_PER_PAGE = 10;
    public static boolean CATEGORY_SORT_USING_ORDER_NUM = false;

    public final Map<Integer, NavigatorPublicCategory> publicCategories = new HashMap<>();
    public final ConcurrentHashMap<String, NavigatorFilterField> filterSettings = new ConcurrentHashMap<>();
    public final Map<String, NavigatorFilter> filters = new HashMap<>();

    public NavigatorManager() {
        long millis = System.currentTimeMillis();

        this.filters.put(NavigatorPublicFilter.name, new NavigatorPublicFilter());
        this.filters.put(NavigatorHotelFilter.name, new NavigatorHotelFilter());
        this.filters.put(NavigatorRoomAdsFilter.name, new NavigatorRoomAdsFilter());
        this.filters.put(NavigatorUserFilter.name, new NavigatorUserFilter());
        this.filters.put(NavigatorFavoriteFilter.name, new NavigatorFavoriteFilter());

        LOGGER.info("Navigator Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
    }

    public void loadNavigator() {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            synchronized (this.publicCategories) {
                this.publicCategories.clear();

                try (Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM navigator_publiccats WHERE visible = '1' ORDER BY order_num DESC")) {
                    while (set.next()) {
                        this.publicCategories.put(set.getInt("id"), new NavigatorPublicCategory(set));
                    }
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                }

                try (Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM navigator_publics WHERE visible = '1'")) {
                    while (set.next()) {
                        NavigatorPublicCategory category = this.publicCategories.get(set.getInt("public_cat_id"));

                        if (category != null) {
                            Room room = Emulator.getGameEnvironment().getRoomManager().loadRoom(set.getInt("room_id"));

                            if (room != null) {
                                category.addRoom(room);
                            } else {
                                LOGGER.error("Public room (ID: {} defined in navigator_publics does not exist!", set.getInt("room_id"));
                            }
                        }
                    }
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                }
            }

            synchronized (this.filterSettings) {
                try (Statement statement = connection.createStatement(); ResultSet set = statement.executeQuery("SELECT * FROM navigator_filter")) {
                    while (set.next()) {
                        Method field = null;
                        Class<?> clazz = Room.class;

                        if (set.getString("field").contains(".")) {
                            for (String s : (set.getString("field")).split("\\.")) {
                                try {
                                    field = clazz.getDeclaredMethod(s);
                                    clazz = field.getReturnType();
                                } catch (Exception e) {
                                    LOGGER.error("Caught exception", e);
                                    break;
                                }
                            }
                        } else {
                            try {
                                field = clazz.getDeclaredMethod(set.getString("field"));
                            } catch (Exception e) {
                                LOGGER.error("Caught exception", e);
                                continue;
                            }
                        }

                        if (field != null) {
                            this.filterSettings.put(set.getString("key"), new NavigatorFilterField(set.getString("key"), field, set.getString("database_query"), NavigatorFilterComparator.valueOf(set.getString("compare").toUpperCase())));
                        }
                    }
                } catch (SQLException e) {
                    LOGGER.error("Caught SQL exception", e);
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        List<Room> staffPromotedRooms = Emulator.getGameEnvironment().getRoomManager().getRoomsStaffPromoted();

        for (Room room : staffPromotedRooms) {
            this.publicCategories.get(Emulator.getConfig().getInt("hotel.navigator.staffpicks.categoryid")).addRoom(room);
        }
    }

    public NavigatorFilterComparator comperatorForField(Method field) {
        for (Map.Entry<String, NavigatorFilterField> set : this.filterSettings.entrySet()) {
            if (set.getValue().field == field) {
                return set.getValue().comparator;
            }
        }

        return null;
    }

    public List<Room> getRoomsForCategory(String category, Habbo habbo) {
        List<Room> rooms = new ArrayList<>();

        switch (category) {
            case "my":
                rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsForHabbo(habbo);
                break;
            case "favorites":
                rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsFavourite(habbo);
                break;
            case "history_freq":
                rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsVisited(habbo, false, 10);
                break;
            case "my_groups":
                rooms = Emulator.getGameEnvironment().getRoomManager().getGroupRooms(habbo, 25);
                break;
            case "with_rights":
                rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsWithRights(habbo);
                break;
            case "official-root":
                rooms = Emulator.getGameEnvironment().getRoomManager().getPublicRooms();
                break;
            case "popular":
                rooms = Emulator.getGameEnvironment().getRoomManager().getPopularRooms(Emulator.getConfig().getInt("hotel.navigator.popular.amount"));
                break;
            case "categories":
                rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsPromoted();
                break;
            case "with_friends":
                rooms = Emulator.getGameEnvironment().getRoomManager().getRoomsWithFriendsIn(habbo, 25);
                break;
            case "highest_score":
                rooms = Emulator.getGameEnvironment().getRoomManager().getTopRatedRooms(25);
                break;
            default:
                return null;
        }

        Collections.sort(rooms);

        return rooms;
    }
}
