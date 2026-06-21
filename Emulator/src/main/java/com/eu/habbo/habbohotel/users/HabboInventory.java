package com.eu.habbo.habbohotel.users;

import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlace;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlaceOffer;
import com.eu.habbo.habbohotel.catalog.marketplace.MarketPlaceState;
import com.eu.habbo.habbohotel.users.inventory.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class HabboInventory {

    private static final Logger LOGGER = LoggerFactory.getLogger(HabboInventory.class);

    //Configuration. Loaded from database & updated accordingly.
    public static int MAXIMUM_ITEMS = 10000;
    private final Set<MarketPlaceOffer> items;
    private final Habbo habbo;
    private WardrobeComponent wardrobeComponent;
    private BadgesComponent badgesComponent;
    private BotsComponent botsComponent;
    private EffectsComponent effectsComponent;
    private ItemsComponent itemsComponent;
    private PetsComponent petsComponent;
    private PrefixesComponent prefixesComponent;
    private NickIconsComponent nickIconsComponent;
    private UserVisualSettingsComponent userVisualSettingsComponent;

    public HabboInventory(Habbo habbo) {
        this.habbo = habbo;
        try {
            this.badgesComponent = new BadgesComponent(this.habbo);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        try {
            this.botsComponent = new BotsComponent(this.habbo);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        try {
            this.effectsComponent = new EffectsComponent(this.habbo);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        try {
            this.itemsComponent = new ItemsComponent(this, this.habbo);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        try {
            this.petsComponent = new PetsComponent(this.habbo);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        try {
            this.wardrobeComponent = new WardrobeComponent(this.habbo);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        try {
            this.prefixesComponent = new PrefixesComponent(this.habbo);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        try {
            this.nickIconsComponent = new NickIconsComponent(this.habbo);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        try {
            this.userVisualSettingsComponent = new UserVisualSettingsComponent(this.habbo);
        } catch (Exception e) {
            LOGGER.error("Caught exception", e);
        }

        this.items = MarketPlace.getOwnOffers(this.habbo);
    }

    public WardrobeComponent getWardrobeComponent() {
        return this.wardrobeComponent;
    }

    public void setWardrobeComponent(WardrobeComponent wardrobeComponent) {
        this.wardrobeComponent = wardrobeComponent;
    }

    public BadgesComponent getBadgesComponent() {
        return this.badgesComponent;
    }

    public void setBadgesComponent(BadgesComponent badgesComponent) {
        this.badgesComponent = badgesComponent;
    }

    public BotsComponent getBotsComponent() {
        return this.botsComponent;
    }

    public void setBotsComponent(BotsComponent botsComponent) {
        this.botsComponent = botsComponent;
    }

    public EffectsComponent getEffectsComponent() {
        return this.effectsComponent;
    }

    public void setEffectsComponent(EffectsComponent effectsComponent) {
        this.effectsComponent = effectsComponent;
    }

    public ItemsComponent getItemsComponent() {
        return this.itemsComponent;
    }

    public void setItemsComponent(ItemsComponent itemsComponent) {
        this.itemsComponent = itemsComponent;
    }

    public PetsComponent getPetsComponent() {
        return this.petsComponent;
    }

    public void setPetsComponent(PetsComponent petsComponent) {
        this.petsComponent = petsComponent;
    }

    public PrefixesComponent getPrefixesComponent() {
        return this.prefixesComponent;
    }

    public void setPrefixesComponent(PrefixesComponent prefixesComponent) {
        this.prefixesComponent = prefixesComponent;
    }

    public NickIconsComponent getNickIconsComponent() {
        return this.nickIconsComponent;
    }

    public void setNickIconsComponent(NickIconsComponent nickIconsComponent) {
        this.nickIconsComponent = nickIconsComponent;
    }

    public UserVisualSettingsComponent getUserVisualSettingsComponent() {
        return this.userVisualSettingsComponent;
    }

    public void setUserVisualSettingsComponent(UserVisualSettingsComponent userVisualSettingsComponent) {
        this.userVisualSettingsComponent = userVisualSettingsComponent;
    }

    public void dispose() {
        this.badgesComponent.dispose();
        this.botsComponent.dispose();
        this.effectsComponent.dispose();
        this.itemsComponent.dispose();
        this.petsComponent.dispose();
        this.wardrobeComponent.dispose();
        this.prefixesComponent.dispose();
        this.nickIconsComponent.dispose();
        this.userVisualSettingsComponent.dispose();

        this.badgesComponent = null;
        this.botsComponent = null;
        this.effectsComponent = null;
        this.itemsComponent = null;
        this.petsComponent = null;
        this.wardrobeComponent = null;
        this.prefixesComponent = null;
        this.nickIconsComponent = null;
        this.userVisualSettingsComponent = null;
    }

    public void addMarketplaceOffer(MarketPlaceOffer marketPlaceOffer) {
        this.items.add(marketPlaceOffer);
    }

    public void removeMarketplaceOffer(MarketPlaceOffer marketPlaceOffer) {
        this.items.remove(marketPlaceOffer);
    }

    public Set<MarketPlaceOffer> getMarketplaceItems() {
        return this.items;
    }

    public int getSoldPriceTotal() {
        int i = 0;
        for (MarketPlaceOffer offer : this.items) {
            if (offer.getState().equals(MarketPlaceState.SOLD)) {
                i += offer.getPrice();
            }
        }
        return i;
    }

    public MarketPlaceOffer getOffer(int id) {
        synchronized (this.items) {
            for (MarketPlaceOffer offer : this.items) {
                if (offer.getOfferId() == id)
                    return offer;
            }
        }

        return null;
    }

    public Habbo getHabbo() {
        return this.habbo;
    }
}
