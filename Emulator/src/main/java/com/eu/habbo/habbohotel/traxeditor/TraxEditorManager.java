package com.eu.habbo.habbohotel.traxeditor;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.items.Item;
import com.eu.habbo.habbohotel.items.SoundTrack;
import com.eu.habbo.habbohotel.items.interactions.InteractionMusicDisc;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboItem;
import com.eu.habbo.messages.outgoing.inventory.AddHabboItemComposer;
import com.eu.habbo.messages.outgoing.inventory.InventoryRefreshComposer;
import com.eu.habbo.messages.outgoing.inventory.RemoveHabboItemComposer;
import com.eu.habbo.threading.runnables.QueryDeleteHabboItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TraxEditorManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(TraxEditorManager.class);
    private final Object purchaseLock = new Object();
    public static final String EMPTY_SONG_DATA = "1:0,1:2:0,1:3:0,1:4:0,1:";
    public static final int ERROR_DISABLED = 1;
    public static final int ERROR_LIMIT_REACHED = 2;
    public static final int ERROR_NOT_ENOUGH_CURRENCY = 3;
    public static final int ERROR_INVALID_DATA = 4;
    public static final int ERROR_NOT_FOUND = 5;

    public boolean isEnabled() {
        return Emulator.getConfig().getBoolean("trax.editor.enabled", true);
    }

    public int getMaxSongs() {
        return Emulator.getConfig().getInt("trax.editor.max_songs", 5);
    }

    public int getCostCurrency() {
        return Emulator.getConfig().getInt("trax.editor.song.cost.currency", 5);
    }

    public int getCostAmount() {
        return Emulator.getConfig().getInt("trax.editor.song.cost.amount", 25);
    }

    public List<SoundTrack> getSongs(int userId) {
        List<SoundTrack> songs = new ArrayList<>();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT soundtrack_id FROM users_soundtracks WHERE user_id = ? ORDER BY id")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                while (set.next()) {
                    SoundTrack track =
                            Emulator.getGameEnvironment().getItemManager().getSoundTrack(set.getInt("soundtrack_id"));
                    if (track != null) {
                        songs.add(track);
                    }
                }
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return songs;
    }

    public boolean ownsSong(int userId, int soundTrackId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM users_soundtracks WHERE user_id = ? AND soundtrack_id = ? LIMIT 1")) {
            statement.setInt(1, userId);
            statement.setInt(2, soundTrackId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next();
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return false;
    }

    private int countSongs(int userId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("SELECT COUNT(*) FROM users_soundtracks WHERE user_id = ?")) {
            statement.setInt(1, userId);
            try (ResultSet set = statement.executeQuery()) {
                return set.next() ? set.getInt(1) : 0;
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return Integer.MAX_VALUE;
    }

    public int buySong(Habbo habbo, String name) {
        if (!this.isEnabled()) return ERROR_DISABLED;

        String songName = sanitizeName(name, habbo);
        if (songName == null) return ERROR_INVALID_DATA;

        synchronized (this.purchaseLock) {
            if (this.countSongs(habbo.getHabboInfo().getId()) >= this.getMaxSongs()) {
                return ERROR_LIMIT_REACHED;
            }

            if (!this.chargeSong(habbo)) {
                return ERROR_NOT_ENOUGH_CURRENCY;
            }

            SoundTrack track = this.insertSong(habbo, songName);

            if (track == null) {
                this.refundSong(habbo);
                return ERROR_INVALID_DATA;
            }

            Emulator.getGameEnvironment().getItemManager().addSoundTrack(track);
            this.deliverDisc(habbo, track);
            return 0;
        }
    }

    /** Saves name + note data of a song the user owns. @return 0 or an ERROR_* code. */
    public int saveSong(Habbo habbo, int soundTrackId, String name, String data) {
        if (!this.isEnabled()) return ERROR_DISABLED;

        String songName = sanitizeName(name, habbo);
        if (songName == null) return ERROR_INVALID_DATA;

        int length = TraxSongDataValidator.validatedLength(data);
        if (length < 0) return ERROR_INVALID_DATA;

        if (!this.ownsSong(habbo.getHabboInfo().getId(), soundTrackId)) {
            return ERROR_NOT_FOUND;
        }

        SoundTrack track = Emulator.getGameEnvironment().getItemManager().getSoundTrack(soundTrackId);
        if (track == null) return ERROR_NOT_FOUND;

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE soundtracks SET name = ?, track = ?, length = ? WHERE id = ?")) {
            statement.setString(1, songName);
            statement.setString(2, data);
            statement.setInt(3, length);
            statement.setInt(4, soundTrackId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return ERROR_INVALID_DATA;
        }

        this.touchSong(soundTrackId);
        track.update(songName, data, length);
        return 0;
    }

    /**
     * Deletes an owned song, freeing its slot. The owner's inventory discs for
     * the song are removed too; discs elsewhere (other users, jukeboxes) are
     * pruned when their playlist reloads.
     */
    public int deleteSong(Habbo habbo, int soundTrackId) {
        if (!this.isEnabled()) return ERROR_DISABLED;

        if (!this.ownsSong(habbo.getHabboInfo().getId(), soundTrackId)) {
            return ERROR_NOT_FOUND;
        }

        SoundTrack track = Emulator.getGameEnvironment().getItemManager().getSoundTrack(soundTrackId);

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "DELETE FROM users_soundtracks WHERE user_id = ? AND soundtrack_id = ?")) {
                statement.setInt(1, habbo.getHabboInfo().getId());
                statement.setInt(2, soundTrackId);
                statement.execute();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM soundtracks WHERE id = ?")) {
                statement.setInt(1, soundTrackId);
                statement.execute();
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
            return ERROR_NOT_FOUND;
        }

        if (track != null) {
            Emulator.getGameEnvironment().getItemManager().removeSoundTrack(track.getCode());
        }

        this.removeOwnedDiscs(habbo, soundTrackId);

        return 0;
    }

    /**
     * Removes the owner's inventory discs pointing at a deleted song so dead
     * references don't linger in the jukebox "My Music" list.
     */
    private void removeOwnedDiscs(Habbo habbo, int soundTrackId) {
        List<HabboItem> discs = new ArrayList<>();

        for (HabboItem item : habbo.getInventory().getItemsComponent().getItemsAsValueCollection()) {
            if (item instanceof InteractionMusicDisc
                    && ((InteractionMusicDisc) item).getSongId() == soundTrackId
                    && item.getRoomId() == 0) {
                discs.add(item);
            }
        }

        if (discs.isEmpty()) return;

        for (HabboItem disc : discs) {
            habbo.getInventory().getItemsComponent().removeHabboItem(disc);
            habbo.getClient().sendResponse(new RemoveHabboItemComposer(disc.getId()));
            Emulator.getThreading().runPersistence(new QueryDeleteHabboItem(disc.getId()));
        }

        habbo.getClient().sendResponse(new InventoryRefreshComposer());
    }

    private boolean chargeSong(Habbo habbo) {
        int amount = this.getCostAmount();
        if (amount <= 0) return true;

        int currency = this.getCostCurrency();
        if (currency == -1) {
            return habbo.tryTakeCredits(amount);
        }

        return habbo.tryTakePoints(currency, amount);
    }

    private void refundSong(Habbo habbo) {
        int amount = this.getCostAmount();
        if (amount <= 0) return;

        int currency = this.getCostCurrency();
        if (currency == -1) {
            habbo.giveCredits(amount);
        } else {
            habbo.givePoints(currency, amount);
        }
    }

    private SoundTrack insertSong(Habbo habbo, String name) {
        int userId = habbo.getHabboInfo().getId();
        int now = Emulator.getIntUnixTimestamp();

        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);

            try {
                int songId;

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO soundtracks (code, name, author, track, length) VALUES (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    statement.setString(1, "usr_pending");
                    statement.setString(2, name);
                    statement.setString(3, habbo.getHabboInfo().getUsername());
                    statement.setString(4, EMPTY_SONG_DATA);
                    statement.setInt(5, 2);
                    statement.execute();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            connection.rollback();
                            return null;
                        }
                        songId = keys.getInt(1);
                    }
                }

                String code = "usr_" + songId;

                try (PreparedStatement statement =
                        connection.prepareStatement("UPDATE soundtracks SET code = ? WHERE id = ?")) {
                    statement.setString(1, code);
                    statement.setInt(2, songId);
                    statement.execute();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO users_soundtracks (user_id, soundtrack_id, created_at, updated_at) VALUES (?, ?, ?, ?)")) {
                    statement.setInt(1, userId);
                    statement.setInt(2, songId);
                    statement.setInt(3, now);
                    statement.setInt(4, now);
                    statement.execute();
                }

                connection.commit();
                return new SoundTrack(songId, code, name, habbo.getHabboInfo().getUsername(), EMPTY_SONG_DATA, 2);
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }

        return null;
    }

    private void touchSong(int soundTrackId) {
        try (Connection connection = Emulator.getDatabase().getDataSource().getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE users_soundtracks SET updated_at = ? WHERE soundtrack_id = ?")) {
            statement.setInt(1, Emulator.getIntUnixTimestamp());
            statement.setInt(2, soundTrackId);
            statement.execute();
        } catch (SQLException e) {
            LOGGER.error("Caught SQL exception", e);
        }
    }

    private void deliverDisc(Habbo habbo, SoundTrack track) {
        Item baseItem = this.resolveDiscBaseItem();

        if (baseItem == null) {
            LOGGER.error(
                    "Trax editor: no items_base with interaction_type musicdisc found; song {} was created without a disc.",
                    track.getId());
            return;
        }

        HabboItem disc = Emulator.getGameEnvironment()
                .getItemManager()
                .createItem(habbo.getHabboInfo().getId(), baseItem, 0, 0, createDiscExtraData(habbo, track));

        if (disc == null) return;

        habbo.getInventory().getItemsComponent().addItem(disc);
        habbo.getClient().sendResponse(new AddHabboItemComposer(disc));
        habbo.getClient().sendResponse(new InventoryRefreshComposer());
    }

    private Item resolveDiscBaseItem() {
        int configured = Emulator.getConfig().getInt("trax.editor.disk.base_item", 0);

        if (configured > 0) {
            Item item = Emulator.getGameEnvironment().getItemManager().getItem(configured);
            if (item != null
                    && item.getInteractionType() != null
                    && item.getInteractionType().getType() == InteractionMusicDisc.class) {
                return item;
            }
            LOGGER.warn(
                    "Trax editor: trax.editor.disk.base_item={} is not a musicdisc base item; falling back to auto-detection.",
                    configured);
        }

        return Emulator.getGameEnvironment().getItemManager().getFirstItemByInteraction(InteractionMusicDisc.class);
    }

    /** Same shape CatalogManager writes for bought song disks: user, date, length, name, song id. */
    private static String createDiscExtraData(Habbo habbo, SoundTrack track) {
        Calendar calendar = Calendar.getInstance();
        return habbo.getHabboInfo().getUsername() + "\n"
                + calendar.get(Calendar.DAY_OF_MONTH) + "\n"
                + (calendar.get(Calendar.MONTH) + 1) + "\n"
                + calendar.get(Calendar.YEAR) + "\n"
                + track.getLength() + "\n" + track.getName() + "\n" + track.getId();
    }

    private static String sanitizeName(String name, Habbo habbo) {
        if (name == null) return null;

        String cleaned = Emulator.getGameEnvironment()
                .getWordFilter()
                .filter(name.replaceAll("\\p{Cntrl}", " ").trim(), habbo);

        if (cleaned.isEmpty() || cleaned.length() > 64) return null;

        return cleaned;
    }
}
