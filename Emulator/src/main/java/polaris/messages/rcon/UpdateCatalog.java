package polaris.messages.rcon;

import com.google.gson.Gson;
import polaris.Emulator;
import polaris.messages.outgoing.catalog.CatalogModeComposer;
import polaris.messages.outgoing.catalog.CatalogUpdatedComposer;
import polaris.messages.outgoing.catalog.DiscountComposer;
import polaris.messages.outgoing.catalog.GiftConfigurationComposer;
import polaris.messages.outgoing.catalog.RecyclerLogicComposer;
import polaris.messages.outgoing.catalog.marketplace.MarketplaceConfigComposer;

public class UpdateCatalog extends RCONMessage<UpdateCatalog.JSONUpdateCatalog> {

    public UpdateCatalog() {
        super(JSONUpdateCatalog.class);
    }

    @Override
    public void handle(Gson gson, JSONUpdateCatalog json) {
        Emulator.getGameEnvironment().getCatalogManager().initialize();
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new CatalogUpdatedComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new CatalogModeComposer(0));
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new DiscountComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new MarketplaceConfigComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new GiftConfigurationComposer());
        Emulator.getGameServer().getGameClientManager().sendBroadcastResponse(new RecyclerLogicComposer());
        Emulator.getGameEnvironment().getCraftingManager().reload();
    }

    static class JSONUpdateCatalog {}
}
