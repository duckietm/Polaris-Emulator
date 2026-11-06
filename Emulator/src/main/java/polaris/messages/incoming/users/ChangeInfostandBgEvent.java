package polaris.messages.incoming.users;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.UserInfo;
import polaris.hotel.users.UserStats;
import polaris.hotel.users.infostand.InfostandBackgroundManager;
import polaris.hotel.users.infostand.InfostandBackgroundManager.Category;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;

public class ChangeInfostandBgEvent extends MessageHandler {
    private static final String COOLDOWN_KEY = "infostand_bg_cooldown";
    private static final long COOLDOWN_MS = 500L;
    private static final int MIN_ID = 0;
    private static final int MAX_ID = 9999;

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        UserInfo info = user.getUserInfo();
        if (info == null) return;

        UserStats stats = user.getUserStats();
        if (stats != null) {
            long now = System.currentTimeMillis();
            Object last = stats.cache.get(COOLDOWN_KEY);
            if (last instanceof Long && (now - (Long) last) < COOLDOWN_MS) {
                return;
            }
            stats.cache.put(COOLDOWN_KEY, now);
        }

        int requestedBg = sanitize(this.packet.readInt());
        int requestedStand = sanitize(this.packet.readInt());
        int requestedOverlay = sanitize(this.packet.readInt());
        int requestedCard = this.packet.bytesAvailable() >= 4 ? sanitize(this.packet.readInt()) : 0;
        int requestedBorder = this.packet.bytesAvailable() >= 4 ? sanitize(this.packet.readInt()) : 0;

        InfostandBackgroundManager manager = Emulator.getGameEnvironment() != null
                ? Emulator.getGameEnvironment().getInfostandBackgroundManager()
                : null;

        int backgroundImage = resolve(manager, user, Category.BACKGROUND, requestedBg, info.getInfostandBg());
        int backgroundStand = resolve(manager, user, Category.STAND, requestedStand, info.getInfostandStand());
        int backgroundOverlay = resolve(manager, user, Category.OVERLAY, requestedOverlay, info.getInfostandOverlay());
        int backgroundCard = resolve(manager, user, Category.CARD, requestedCard, info.getInfostandCardBg());
        int backgroundBorder = resolve(manager, user, Category.BORDER, requestedBorder, info.getInfostandBorder());

        if (info.getInfostandBg() == backgroundImage
                && info.getInfostandStand() == backgroundStand
                && info.getInfostandOverlay() == backgroundOverlay
                && info.getInfostandCardBg() == backgroundCard
                && info.getInfostandBorder() == backgroundBorder) {
            return;
        }

        info.setInfostandBg(backgroundImage);
        info.setInfostandStand(backgroundStand);
        info.setInfostandOverlay(backgroundOverlay);
        info.setInfostandCardBg(backgroundCard);
        info.setInfostandBorder(backgroundBorder);
        info.run();

        if (info.getCurrentRoom() != null) {
            info.getCurrentRoom().sendComposer(new RoomUserDataComposer(user).compose());
        } else {
            this.client.sendResponse(new RoomUserDataComposer(user));
        }
    }

    private static int sanitize(int value) {
        if (value < MIN_ID || value > MAX_ID) return 0;
        return value;
    }

    private static int resolve(
            InfostandBackgroundManager manager, User user, Category category, int requested, int current) {
        if (manager == null) return requested;
        return manager.canUse(user, category, requested) ? requested : current;
    }
}
