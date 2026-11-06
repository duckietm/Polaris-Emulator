package polaris.hotel.users.inventory;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.messages.outgoing.inventory.EffectsListAddComposer;
import polaris.messages.outgoing.inventory.EffectsListEffectEnableComposer;
import polaris.messages.outgoing.inventory.EffectsListRemoveComposer;

public class EffectsComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(EffectsComponent.class);

    public final Int2ObjectMap<UserEffect> effects = new Int2ObjectOpenHashMap<>();
    public final User user;
    public int activatedEffect = 0;

    public EffectsComponent(User user) {
        this.user = user;
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT * FROM users_effects WHERE user_id = ?")) {
            statement.setInt(1, user.getUserInfo().getId());
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    this.effects.put(set.getInt("effect"), new UserEffect(set));
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
        if (user.getUserInfo().getRank().getRoomEffect() > 0)
            this.createRankEffect(user.getUserInfo().getRank().getRoomEffect());
    }

    public UserEffect createEffect(int effectId) {
        return createEffect(effectId, 86400);
    }

    public UserEffect createEffect(int effectId, int duration) {
        UserEffect effect;
        synchronized (this.effects) {
            if (this.effects.containsKey(effectId)) {
                effect = this.effects.get(effectId);

                if (duration <= 0) {
                    // A permanent copy replaces the timed ones.
                    effect.duration = 0;
                } else if (effect.total <= 99) {
                    effect.total++;
                }
            } else {
                effect = new UserEffect(effectId, this.user.getUserInfo().getId());
                effect.duration = duration;
                effect.insert();
            }

            this.addEffect(effect);
        }

        return effect;
    }

    public static UserEffect persistEffect(Connection connection, int userId, int effectId, int duration)
            throws SQLException {
        // duration 0 means permanent: a timed copy bought afterwards must add to the
        // stack without turning the permanent effect into a 24-hour one, which is what
        // a plain VALUES(duration) did.
        String upsert = "INSERT INTO users_effects (user_id, effect, total, duration) VALUES (?, ?, 1, ?) "
                + "ON DUPLICATE KEY UPDATE total = LEAST(total + 1, 100), "
                + "duration = IF(duration = 0, 0, VALUES(duration))";
        try (PreparedStatement statement = connection.prepareStatement(upsert)) {
            statement.setInt(1, userId);
            statement.setInt(2, effectId);
            statement.setInt(3, duration);
            statement.executeUpdate();
        }
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT * FROM users_effects WHERE user_id = ? AND effect = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setInt(2, effectId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new SQLException("Unable to load persisted effect " + effectId);
                return new UserEffect(result);
            }
        }
    }

    public void publishEffect(UserEffect effect) {
        synchronized (this.effects) {
            this.addEffect(effect);
        }
    }

    public UserEffect createRankEffect(int effectId) {
        UserEffect rankEffect = new UserEffect(effectId, user.getUserInfo().getId());
        rankEffect.duration = 0;
        rankEffect.isRankEnable = true;
        rankEffect.activationTimestamp = Emulator.getIntUnixTimestamp();
        rankEffect.enabled = true;
        this.effects.put(effectId, rankEffect);
        this.activatedEffect = effectId; // enabled by default
        return rankEffect;
    }

    /** Owned as a permanent effect (rank effects excluded). */
    public boolean ownsPermanently(int effectId) {
        synchronized (this.effects) {
            UserEffect effect = this.effects.get(effectId);

            return effect != null && !effect.isRankEnable && effect.duration <= 0;
        }
    }

    public void addEffect(UserEffect effect) {
        this.effects.put(effect.effect, effect);

        this.user.getClient().sendResponse(new EffectsListAddComposer(effect));
    }

    public void dispose() {
        synchronized (this.effects) {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "UPDATE users_effects SET duration = ?, activation_timestamp = ?, total = ? WHERE user_id = ? AND effect = ?")) {
                for (UserEffect effect : this.effects.values()) {
                    if (!effect.isRankEnable) {
                        try {
                            statement.setInt(1, effect.duration);
                            statement.setInt(2, effect.activationTimestamp);
                            statement.setInt(3, effect.total);
                            statement.setInt(4, effect.userId);
                            statement.setInt(5, effect.effect);
                            statement.addBatch();
                        } catch (SQLException e) {
                            LOGGER.error("Caught SQL exception", e);
                        }
                    }
                }

                statement.executeBatch();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }

            this.effects.clear();
        }
    }

    public boolean ownsEffect(int effectId) {
        return this.effects.containsKey(effectId);
    }

    public void activateEffect(int effectId) {
        UserEffect effect = this.effects.get(effectId);

        if (effect != null) {
            if (effect.isRemaining()) {
                effect.activationTimestamp = Emulator.getIntUnixTimestamp();
            } else {
                this.user.getClient().sendResponse(new EffectsListRemoveComposer(effect));
            }
        }
    }

    public void enableEffect(int effectId) {
        UserEffect effect = this.effects.get(effectId);

        if (effect != null) {
            if (!effect.isActivated()) {
                this.activateEffect(effect.effect);
            }

            this.activatedEffect = effectId;

            if (this.user.getUserInfo().getCurrentRoom() != null) {
                this.user.getUserInfo().getCurrentRoom().giveEffect(this.user, effectId, effect.remainingTime());
            }

            this.user.getClient().sendResponse(new EffectsListEffectEnableComposer(effect));
        }
    }

    public boolean hasActivatedEffect(int effectId) {
        UserEffect effect = this.effects.get(effectId);

        if (effect != null) {
            return effect.isActivated();
        }

        return false;
    }

    public static class UserEffect {
        public int effect;
        public int userId;
        public int duration = 86400;
        public int activationTimestamp = -1;
        public int total = 1;
        public boolean enabled = false;
        public boolean isRankEnable = false;

        public UserEffect(ResultSet set) throws SQLException {
            this.effect = set.getInt("effect");
            this.userId = set.getInt("user_id");
            this.duration = set.getInt("duration");
            this.activationTimestamp = set.getInt("activation_timestamp");
            this.total = set.getInt("total");
        }

        public UserEffect(int effect, int userId) {
            this.effect = effect;
            this.userId = userId;
        }

        public boolean isActivated() {
            return this.activationTimestamp >= 0;
        }

        public boolean isRemaining() {
            if (this.duration <= 0) return true;

            if (this.total > 0) {
                if (this.activationTimestamp >= 0) {
                    if (Emulator.getIntUnixTimestamp() - this.activationTimestamp >= this.duration) {
                        this.activationTimestamp = -1;
                        this.total--;
                    }
                }
            }

            return this.total > 0;
        }

        public int remainingTime() {
            if (this.duration <= 0) // permanant
            return Integer.MAX_VALUE;

            return Emulator.getIntUnixTimestamp() - this.activationTimestamp + this.duration;
        }

        public void insert() {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO users_effects (user_id, effect, total, duration) VALUES (?, ?, ?, ?)")) {
                statement.setInt(1, this.userId);
                statement.setInt(2, this.effect);
                statement.setInt(3, this.total);
                statement.setInt(4, this.duration);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }

        public void delete() {
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                    PreparedStatement statement =
                            connection.prepareStatement("DELETE FROM users_effects WHERE user_id = ? AND effect = ?")) {
                statement.setInt(1, this.userId);
                statement.setInt(2, this.effect);
                statement.execute();
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }
        }
    }
}
