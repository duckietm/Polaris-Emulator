package polaris.hotel.users;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import polaris.WiredPlatform;
import polaris.core.ConfigurationManager;
import polaris.hotel.GameEnvironment;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.quests.EpicPopupComposer;

/**
 * The official epic popup ({@code EpicPopupMessageEvent}, AIR {@code HabboEpicPopupView}): a small framed window
 * that shows one 200x200 image with a close button, for announcements that are a picture (a new campaign, a
 * finished event, a hotel milestone). The client resolves {@code ${image.library.url}} itself.
 *
 * <p>An image is a full http(s) URL, an asset URI with configuration keys, or a plain name looked up in the
 * {@code hotel.epic_popup.folder} folder of the image library ({@code .png} when it has no extension).
 */
public final class EpicPopups {
    public static final String FOLDER = "hotel.epic_popup.folder";
    public static final String DEFAULT_FOLDER = "notifications/";
    public static final int MAX_LENGTH = 512;

    private static final Pattern URL = Pattern.compile("https?://[^\\s\"'<>]+");
    private static final Pattern ASSET_URI = Pattern.compile("\\$\\{[A-Za-z0-9_.]+}[A-Za-z0-9_./%-]*");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_./-]*");

    private EpicPopups() {}

    /** The asset URI the client loads for {@code image}, or null when it is not a usable image reference. */
    public static String assetUri(String image) {
        return assetUri(image, folder());
    }

    static String assetUri(String image, String folder) {
        if (image == null) return null;
        String value = image.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH) return null;
        if (URL.matcher(value).matches() || ASSET_URI.matcher(value).matches()) return value;
        if (!NAME.matcher(value).matches() || value.contains("..")) return null;

        String path = folder == null ? "" : folder.trim();
        if (!path.isEmpty() && !path.endsWith("/")) path += "/";
        String fileName = value.substring(value.lastIndexOf('/') + 1);
        String extension = fileName.contains(".") ? "" : ".png";
        return EpicPopupComposer.LIBRARY_URL + path + value + extension;
    }

    /** Everyone online, or one online user by name (null when that user is not online). */
    public static Collection<User> recipients(String username) {
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (environment == null) return username == null ? List.of() : null;
        if (username == null)
            return environment.getUserManager().getOnlineUsers().values();
        User user = environment.getUserManager().getUser(username);
        return user == null ? null : List.of(user);
    }

    /** Everyone online for 0, else that online user (null when not online). */
    public static Collection<User> recipients(int userId) {
        GameEnvironment environment = WiredPlatform.gameEnvironment();
        if (userId <= 0)
            return environment == null
                    ? List.of()
                    : environment.getUserManager().getOnlineUsers().values();
        User user = environment == null ? null : environment.getUserManager().getUser(userId);
        return user == null ? null : List.of(user);
    }

    /** Shows the popup to every user given that has not switched staff alerts off; returns how many got it. */
    public static int show(Collection<User> recipients, String assetUri) {
        if (recipients == null || assetUri == null) return 0;

        ServerMessage message = new EpicPopupComposer(assetUri).compose();
        int reached = 0;
        for (User user : recipients) {
            if (user == null || user.getClient() == null) continue;
            if (user.getUserStats() != null && user.getUserStats().blockStaffAlerts) continue;
            user.getClient().sendResponse(message);
            reached++;
        }
        return reached;
    }

    private static String folder() {
        ConfigurationManager config = WiredPlatform.configuration();
        return config == null ? DEFAULT_FOLDER : config.getValue(FOLDER, DEFAULT_FOLDER);
    }
}
