package polaris.messages.incoming.collectibles;

import java.util.Optional;
import java.util.OptionalLong;
import polaris.Emulator;
import polaris.hotel.collectibles.CollectibleAvatars;
import polaris.hotel.collectibles.CollectibleData.WardrobeOutfit;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.hotel.users.UserGender;
import polaris.hotel.users.UserInfo;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;
import polaris.messages.outgoing.users.UpdateUserLookComposer;
import polaris.plugin.events.users.UserSavedLookEvent;

/**
 * AIR 15 save of an avatar collectible look (133, one string: the wardrobe item id), sent by
 * {@code HabboAvatarEditor} when the user saves with a look of the NFT tab picked; the editor asks the worn one
 * (3810) right after. The user must own the token and the token must be a look; the figure applied is the one
 * stored for the product, never one from the client. Like a normal look save it goes through
 * {@link UserSavedLookEvent}, so a plugin can cancel it.
 */
public class SaveUserNftWardrobeEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 1000;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        OptionalLong assetId = CollectibleAvatars.parseAssetId(this.packet.readString());

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null || !settings.avatarsActive() || assetId.isEmpty()) return;

        UserInfo info = user.getUserInfo();
        Optional<WardrobeOutfit> owned = service.avatars().owned(info.getId(), assetId.getAsLong());
        if (owned.isEmpty() || owned.get().outfit().figure().isBlank()) return;

        String figure = owned.get().outfit().figure();
        UserGender gender = UserGender.valueOf(owned.get().outfit().gender());
        UserSavedLookEvent lookEvent = new UserSavedLookEvent(user, gender, figure);
        Emulator.getPluginManager().fireEvent(lookEvent);
        if (lookEvent.isCancelled()) return;

        String previousLook = info.getLook();
        String previousGender =
                info.getGender() == null ? "M" : info.getGender().name();
        if (service.avatars()
                .wear(info.getId(), assetId.getAsLong(), previousLook, previousGender, CollectiblesSupport.now())
                .isEmpty()) {
            return;
        }

        info.setLook(figure);
        info.setGender(gender);
        Emulator.getThreading().run(info);
        this.client.sendResponse(new UpdateUserLookComposer(user));
        if (info.getCurrentRoom() != null) {
            info.getCurrentRoom().sendComposer(new RoomUserDataComposer(user).compose());
        }
        user.getMessenger().connectionChanged(user, user.isOnline(), info.getCurrentRoom() != null);
    }
}
