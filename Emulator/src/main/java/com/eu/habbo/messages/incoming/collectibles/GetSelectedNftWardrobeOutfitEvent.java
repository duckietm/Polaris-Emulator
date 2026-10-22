package com.eu.habbo.messages.incoming.collectibles;

import com.eu.habbo.habbohotel.collectibles.CollectibleData.WornOutfit;
import com.eu.habbo.habbohotel.collectibles.CollectiblesService;
import com.eu.habbo.habbohotel.collectibles.CollectiblesSettings;
import com.eu.habbo.habbohotel.users.Habbo;
import com.eu.habbo.messages.incoming.MessageHandler;
import com.eu.habbo.messages.outgoing.collectibles.SelectedNftWardrobeOutfitComposer;
import java.util.Optional;

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
        Habbo habbo = this.client.getHabbo();
        if (habbo == null) return;

        CollectiblesSettings settings = CollectiblesSupport.settings();
        CollectiblesService service = CollectiblesSupport.service(settings);
        if (service == null || !settings.avatarsActive()) return;

        Optional<WornOutfit> worn = service.avatars()
                .worn(habbo.getHabboInfo().getId(), habbo.getHabboInfo().getLook());
        if (worn.isEmpty()) return;

        this.client.sendResponse(new SelectedNftWardrobeOutfitComposer(
                Long.toString(worn.get().assetId()),
                worn.get().fallbackFigure(),
                worn.get().fallbackGender()));
    }
}
