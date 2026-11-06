package polaris.messages.incoming.catalog;

import polaris.Emulator;
import polaris.hotel.catalog.CatalogManager;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.catalog.AlertPurchaseFailedComposer;
import polaris.messages.outgoing.generic.alerts.HotelWillCloseInMinutesComposer;
import polaris.threading.runnables.ShutdownEmulator;

public class CatalogBuyItemEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 250;
    }

    @Override
    public String getRatelimitGroup() {
        return "catalog.purchase";
    }

    @Override
    public void handle() {
        if (Emulator.getIntUnixTimestamp() - this.client.getUser().getUserStats().lastPurchaseTimestamp
                >= CatalogManager.PURCHASE_COOLDOWN) {
            this.client.getUser().getUserStats().lastPurchaseTimestamp = Emulator.getIntUnixTimestamp();
            if (ShutdownEmulator.timestamp > 0) {
                this.client.sendResponse(new HotelWillCloseInMinutesComposer(
                        (ShutdownEmulator.timestamp - Emulator.getIntUnixTimestamp()) / 60));
                return;
            }

            CatalogPurchaseCommand command = CatalogPurchaseCommandReader.readFrom(this.packet);
            new CatalogPurchaseApplicationService(
                            this.client, Emulator.getGameEnvironment(), Emulator.getTexts(), Emulator.getThreading())
                    .purchase(command);
            return;
        }

        this.client.sendResponse(new AlertPurchaseFailedComposer(AlertPurchaseFailedComposer.SERVER_ERROR).compose());
    }
}
