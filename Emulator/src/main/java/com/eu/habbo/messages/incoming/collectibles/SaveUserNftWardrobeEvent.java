package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.Emulator;
import com.eu.habbo.habbohotel.collectibles.CollectibleAvatars;
import com.eu.habbo.habbohotel.collectibles.CollectibleData.WardrobeOutfit;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.habbohotel.users.HabboGender;
import com.eu.habbo.habbohotel.users.HabboInfo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.rooms.users.RoomUserDataComposer;
import com.eu.habbo.messages.outgoing.users.UpdateUserLookComposer;
import com.eu.habbo.plugin.events.users.UserSavedLookEvent;
import java.util.Optional;
import java.util.OptionalLong;

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
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        OptionalLong assetId = CollectibleAvatars.parseAssetId(this.packet.readString());

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null || !settings.avatarsActive() || assetId.isEmpty()) return;

        HabboInfo info = habbo.getHabboInfo();
        Optional<WardrobeOutfit> owned = service.avatars().owned(info.getId(), assetId.getAsLong());
        if (owned.isEmpty() || owned.get().outfit().figure().isBlank()) return;

        String figure = owned.get().outfit().figure();
        HabboGender gender = HabboGender.valueOf(owned.get().outfit().gender());
        UserSavedLookEvent lookEvent = new UserSavedLookEvent(habbo, gender, figure);
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
        this.client.sendResponse(new UpdateUserLookComposer(habbo));
        if (info.getCurrentRoom() != null) {
            info.getCurrentRoom().sendComposer(new RoomUserDataComposer(habbo).compose());
        }
        habbo.getMessenger().connectionChanged(habbo, habbo.isOnline(), info.getCurrentRoom() != null);
    }
}
