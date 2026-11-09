package polaris.hotel.users.subscriptions;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import polaris.Emulator;

/**
 * @author Beny
 */
public class Subscription {
    public static final String CLUB = "HABBO_CLUB";
    public static final String BUILDERS_CLUB = "BUILDERS_CLUB";

    private final int id;
    private final int userId;
    private final String subscriptionType;
    private final int timestampStart;
    private int duration;
    private boolean active;

    /**
     * Subscription constructor
     * @param id ID of the subscription
     * @param userId ID of user who has the subscription
     * @param subscriptionType Subscription type name (e.g. CLUB)
     * @param timestampStart Unix timestamp start of subscription
     * @param duration Length of subscription in seconds
     * @param active Boolean indicating if subscription is active
     */
    public Subscription(
            Integer id,
            Integer userId,
            String subscriptionType,
            Integer timestampStart,
            Integer duration,
            Boolean active) {
        this.id = id;
        this.userId = userId;
        this.subscriptionType = subscriptionType;
        this.timestampStart = timestampStart;
        this.duration = duration;
        this.active = active;
    }

    /**
     * @return ID of the subscription
     */
    public int getSubscriptionId() {
        return id;
    }

    /**
     * @return ID of user who has the subscription
     */
    public int getUserId() {
        return userId;
    }

    /**
     * @return Subscription type name (e.g. CLUB)
     */
    public String getSubscriptionType() {
        return subscriptionType;
    }

    /**
     * @return Length of subscription in seconds
     */
    public int getDuration() {
        return duration;
    }

    /** Publishes a duration already committed by a reward transaction. */
    public void applyPersistedDuration(int duration) {
        this.duration = duration;
    }

    /**
     * Updates the Subscription record with new duration.
     * @param amount Length of time to add in seconds
     */
    public void addDuration(int amount) {
        // Long purchases one after another used to wrap the int and turn the subscription negative.
        this.duration = (int) Math.max(0L, Math.min(maxDuration(this.timestampStart), (long) this.duration + amount));

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE `users_subscriptions` SET `duration` = ? WHERE `id` = ? LIMIT 1")) {
                statement.setInt(1, this.duration);
                statement.setInt(2, this.id);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            SubscriptionManager.LOGGER.error("Caught SQL exception", e);
        }
    }

    /**
     * Sets the subscription as active or inactive. If active and remaining time <= 0 the SubscriptionScheduler will inactivate the subscription and call onExpired()
     * @param active Boolean indicating if the subscription is active
     */
    public void setActive(boolean active) {
        this.active = active;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE `users_subscriptions` SET `active` = ? WHERE `id` = ? LIMIT 1")) {
                statement.setInt(1, this.active ? 1 : 0);
                statement.setInt(2, this.id);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            SubscriptionManager.LOGGER.error("Caught SQL exception", e);
        }
    }

    /**
     * @return Remaining duration of subscription in seconds
     */
    public int getRemaining() {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, end() - Emulator.getIntUnixTimestamp()));
    }

    /**
     * The longest duration that still ends inside an int timestamp: the end is stored and sent as one, and a sum past
     * it wrapped negative, so a very long subscription expired on the next scheduler run.
     */
    public static int maxDuration(int timestampStart) {
        return Integer.MAX_VALUE - Math.max(0, timestampStart);
    }

    private long end() {
        return (long) this.timestampStart + this.duration;
    }

    /**
     * @return Unix timestamp start of subscription
     */
    public int getTimestampStart() {
        return this.timestampStart;
    }

    /**
     * @return Unix timestamp end of subscription
     */
    public int getTimestampEnd() {
        return (int) Math.min(Integer.MAX_VALUE, end());
    }

    /**
     * @return Boolean indicating if the subscription is active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * Called when the subscription is first created
     */
    public void onCreated() {}

    /**
     * Called when the subscription is extended or bought again when already exists
     * @param duration Extended duration time in seconds
     */
    public void onExtended(int duration) {}

    /**
     * Called by SubscriptionScheduler when isActive() && getRemaining() < 0
     */
    public void onExpired() {}
}
