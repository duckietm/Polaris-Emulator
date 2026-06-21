package com.eu.habbo.habbohotel.users.subscriptions;

import com.eu.habbo.Emulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Beny
 */
public class SubscriptionManager {

    public static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionManager.class);

    public Map<String, Class<? extends Subscription>> types;

    public SubscriptionManager() {
        this.types = new HashMap<>();
    }

    public void init() {
        this.types.put(Subscription.HABBO_CLUB, SubscriptionHabboClub.class);
        this.types.put(Subscription.BUILDERS_CLUB, SubscriptionBuildersClub.class);
    }

    public void addSubscriptionType(String type, Class<? extends Subscription> clazz) {
        if(this.types.containsKey(type) || this.types.containsValue(clazz)) {
            throw new RuntimeException("Subscription Type must be unique. An class with type: " + clazz.getName() + " was already added OR the key: " + type + " is already in use.");
        }

        this.types.put(type, clazz);
    }

    public void removeSubscriptionType(String type) {
        this.types.remove(type);
    }

    public Class<? extends Subscription> getSubscriptionClass(String type) {
        if(!this.types.containsKey(type)) {
            LOGGER.debug("Can't find subscription class: {}", type);
            return Subscription.class;
        }

        return this.types.get(type);
    }

    public void dispose() {
        this.types.clear();
    }

    public Set<Subscription> getSubscriptionsForUser(int userId) {
        Set<Subscription> subscriptions = new HashSet<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM users_subscriptions WHERE user_id = ?")) {
            statement.setInt(1, userId);

            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    Class<? extends Subscription> subClazz = Emulator.getGameEnvironment().getSubscriptionManager().getSubscriptionClass(set.getString("subscription_type"));
                    Constructor<? extends Subscription> c = subClazz.getConstructor(Integer.class, Integer.class, String.class, Integer.class, Integer.class, Boolean.class);
                    c.setAccessible(true);
                    Subscription subscription = c.newInstance(set.getInt("id"), set.getInt("user_id"), set.getString("subscription_type"), set.getInt("timestamp_start"), set.getInt("duration"), set.getInt("active") == 1);
                    subscriptions.add(subscription);
                }
            } catch (IllegalAccessException e) {
                LOGGER.error("IllegalAccessException", e);
            } catch (InstantiationException e) {
                LOGGER.error("InstantiationException", e);
            } catch (InvocationTargetException e) {
                LOGGER.error("InvocationTargetException", e);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        catch (NoSuchMethodException e) {
            LOGGER.error("Caught NoSuchMethodException", e);
        }

        return subscriptions;
    }
}
