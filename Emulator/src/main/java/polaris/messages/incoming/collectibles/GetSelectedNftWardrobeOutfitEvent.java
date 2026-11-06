package polaris.messages.incoming.collectibles;

import java.util.Optional;
import polaris.hotel.collectibles.CollectibleData.WornOutfit;
import polaris.hotel.collectibles.CollectiblesService;
import polaris.hotel.collectibles.CollectiblesSettings;
import polaris.hotel.users.User;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.collectibles.SelectedNftWardrobeOutfitComposer;

/**
 * AIR 15 worn avatar collectible request (3810, no fields), sent by {@code HabboAvatarEditor} when it opens and
 * after it saves a collectible look. Answers {@code 350} only while the user wears one: the official editor takes
 * any answer, even an empty token, as "a collectible is worn".
 */
public class GetSelectedNftWardrobeOutfitEvent extends MessageHandler {
    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public void handle() throws Exception {
        User user = this.client.getUser();
        if (user == null) return;

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null || !settings.avatarsActive()) return;

        Optional<WornOutfit> worn = service.avatars()
                .worn(user.getUserInfo().getId(), user.getUserInfo().getLook());
        if (worn.isEmpty()) return;

        this.client.sendResponse(new SelectedNftWardrobeOutfitComposer(
                Long.toString(worn.get().assetId()),
                worn.get().fallbackFigure(),
                worn.get().fallbackGender()));
    }
}
