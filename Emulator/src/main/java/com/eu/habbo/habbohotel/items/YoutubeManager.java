package com.eu.habbo.habbohotel.items;

import com.eu.habbo.Emulator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.sql.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class YoutubeManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(YoutubeManager.class);

    public static class YoutubeVideo {
        private final String id;
        private final int duration;

        YoutubeVideo(String id, int duration) {
            this.id = id;
            this.duration = duration;
        }

        public String getId() {
            return id;
        }

        public int getDuration() {
            return duration;
        }
    }

    public static class YoutubePlaylist {
        private final String id;
        private final String name;
        private final String description;
        private final ArrayList<YoutubeVideo> videos;

        YoutubePlaylist(String id, String name, String description, ArrayList<YoutubeVideo> videos) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.videos = videos;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public ArrayList<YoutubeVideo> getVideos() {
            return videos;
        }
    }

    private final ConcurrentHashMap<Integer, ArrayList<YoutubePlaylist>> playlists = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, YoutubePlaylist> playlistCache = new ConcurrentHashMap<>();

    private String getApiKey() {
        String key = Emulator.getConfig().getValue("youtube.apikey");
        return key != null ? key : "";
    }

    public void load() {
        this.playlists.clear();
        this.playlistCache.clear();

        long millis = System.currentTimeMillis();

        Emulator.getThreading().run(() -> {
            ExecutorService youtubeDataLoaderPool = Executors.newFixedThreadPool(10);

            LOGGER.info("YouTube Manager -> Loading...");

            if (getApiKey().isEmpty()) {
                LOGGER.warn("YouTube Manager -> No API key configured (youtube.apikey). YouTube TVs will not work!");
            }

            int dbEntryCount = 0;
            try (Connection connection = Emulator.getDatabase().getDataSource().getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM youtube_playlists")) {
                try (ResultSet set = statement.executeQuery()) {
                    while (set.next()) {
                        final int itemId = set.getInt("item_id");
                        final String playlistId = set.getString("playlist_id");
                        dbEntryCount++;

                        LOGGER.info("YouTube Manager -> Loading playlist {} for base item #{}", playlistId, itemId);

                        youtubeDataLoaderPool.submit(() -> {
                            YoutubePlaylist playlist;
                            try {
                                playlist = this.getPlaylistDataById(playlistId);
                                if (playlist != null) {
                                    this.addPlaylistToItem(itemId, playlist);
                                    LOGGER.info("YouTube Manager -> Successfully loaded playlist {} for base item #{}", playlistId, itemId);
                                } else {
                                    LOGGER.error("YouTube Manager -> Failed to load playlist {} for base item #{} (returned null - check API key and playlist ID)", playlistId, itemId);
                                }
                            } catch (IOException e) {
                                LOGGER.error("Failed to load YouTube playlist {} ERROR: {}", playlistId, e);
                            }
                        });
                    }
                }
            } catch (SQLException e) {
                LOGGER.error("Caught SQL exception", e);
            }

            if (dbEntryCount == 0) {
                LOGGER.warn("YouTube Manager -> No entries found in youtube_playlists table!");
            }

            youtubeDataLoaderPool.shutdown();
            try {
                youtubeDataLoaderPool.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                LOGGER.error("Interrupted while waiting for YouTube playlist loading to finish.", e);
                Thread.currentThread().interrupt();
            }

            LOGGER.info("YouTube Manager -> Loaded! ({} MS)", System.currentTimeMillis() - millis);
        });
    }

    public YoutubePlaylist getPlaylistDataById(String playlistId) throws IOException {
        if (this.playlistCache.containsKey(playlistId)) return this.playlistCache.get(playlistId);

        String apiKey = getApiKey();
        if(apiKey.isEmpty()) {
            LOGGER.error("YouTube API key is not configured! Set 'youtube.apikey' in emulator_settings to enable YouTube TV.");
            return null;
        }

        YoutubePlaylist playlist;
        URL playlistInfo = URI.create("https://youtube.googleapis.com/youtube/v3/playlists?part=snippet&id=" + playlistId + "&maxResults=1&key=" + apiKey).toURL();
        HttpsURLConnection playlistCon = (HttpsURLConnection) playlistInfo.openConnection();
        if (playlistCon.getResponseCode() != 200) {
            InputStream errorInputStream = playlistCon.getErrorStream();
            InputStreamReader playlistISR = new InputStreamReader(errorInputStream);
            BufferedReader playlistBR = new BufferedReader(playlistISR);
            JsonObject errorObj = JsonParser.parseReader(playlistBR).getAsJsonObject();
            String message = errorObj.get("error").getAsJsonObject().get("message").getAsString();
            LOGGER.error("Failed to load YouTube playlist {} ERROR: {}", playlistId, message);
            return null;
        }
        InputStream playlistInputStream = playlistCon.getInputStream();
        InputStreamReader playlistISR = new InputStreamReader(playlistInputStream);
        BufferedReader playlistBR = new BufferedReader(playlistISR);

        JsonObject playlistData = JsonParser.parseReader(playlistBR).getAsJsonObject();

        JsonArray playlists = playlistData.get("items").getAsJsonArray();
        if (playlists.size() == 0) {
            LOGGER.error("Playlist {} not found!", playlistId);
            return null;
        }
        JsonObject playlistItem = playlists.get(0).getAsJsonObject().get("snippet").getAsJsonObject();

        String name = playlistItem.get("title").getAsString();
        String description = playlistItem.get("description").getAsString();

        ArrayList < YoutubeVideo > videos = new ArrayList < > ();
        String nextPageToken = "";
        do {
            ArrayList < String > videoIds = new ArrayList < > ();
            URL playlistItems;

            if (nextPageToken.isEmpty()) {
                playlistItems = URI.create("https://youtube.googleapis.com/youtube/v3/playlistItems?part=snippet%2Cstatus&playlistId=" + playlistId + "&maxResults=50&key=" + apiKey).toURL();
            } else {
                playlistItems = URI.create("https://youtube.googleapis.com/youtube/v3/playlistItems?part=snippet%2Cstatus&playlistId=" + playlistId + "&pageToken=" + nextPageToken + "&maxResults=50&key=" + apiKey).toURL();
            }

            HttpsURLConnection con = (HttpsURLConnection) playlistItems.openConnection();

            InputStream is = con.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            JsonObject object = JsonParser.parseReader(br).getAsJsonObject();

            JsonArray rawV = object.get("items").getAsJsonArray();
            for (JsonElement rawVideo: rawV) {
                JsonObject videoData = rawVideo.getAsJsonObject().get("snippet").getAsJsonObject();
                JsonObject videoStatus = rawVideo.getAsJsonObject().get("status").getAsJsonObject();
                if (!videoStatus.get("privacyStatus").getAsString().equals("public"))
                    continue; // removed videos
                videoIds.add(videoData.get("resourceId").getAsJsonObject().get("videoId").getAsString());
            }

            if (!videoIds.isEmpty()) {
                URL VideoItems;

                String commaSeparatedVideos = String.join(",", videoIds);

                VideoItems = URI.create("https://youtube.googleapis.com/youtube/v3/videos?part=contentDetails&id=" + commaSeparatedVideos + "&maxResults=50&key=" + apiKey).toURL();
                HttpsURLConnection con1 = (HttpsURLConnection) VideoItems.openConnection();
                InputStream is1 = con1.getInputStream();
                InputStreamReader isr1 = new InputStreamReader(is1);
                BufferedReader br1 = new BufferedReader(isr1);
                JsonObject object1 = JsonParser.parseReader(br1).getAsJsonObject();
                JsonArray Vds = object1.get("items").getAsJsonArray();
                for (JsonElement rawVideo: Vds) {
                    JsonObject contentDetails = rawVideo.getAsJsonObject().get("contentDetails").getAsJsonObject();
                    int duration = (int) Duration.parse(contentDetails.get("duration").getAsString()).getSeconds();
                    if (duration < 1) continue;
                    videos.add(new YoutubeVideo(rawVideo.getAsJsonObject().get("id").getAsString(), duration));
                }
            }
            if (object.has("nextPageToken")) {
                nextPageToken = object.get("nextPageToken").getAsString();
            } else {
                nextPageToken = null;
            }
        } while (nextPageToken != null);

        if (videos.isEmpty()) {
            LOGGER.warn("Playlist {} has no videos!", playlistId);
            return null;
        }
        playlist = new YoutubePlaylist(playlistId, name, description, videos);

        this.playlistCache.put(playlistId, playlist);

        return playlist;

    }

    public ArrayList<YoutubePlaylist> getPlaylistsForItemId(int itemId) {
        return this.playlists.get(itemId);
    }

    public void addPlaylistToItem(int itemId, YoutubePlaylist playlist) {
        ArrayList<YoutubePlaylist> itemPlaylists = this.playlists.computeIfAbsent(itemId, k -> new ArrayList<>());
        synchronized (itemPlaylists) {
            itemPlaylists.add(playlist);
        }
    }
}
