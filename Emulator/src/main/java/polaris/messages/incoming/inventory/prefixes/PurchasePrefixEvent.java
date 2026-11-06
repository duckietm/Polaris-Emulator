package polaris.messages.incoming.inventory.prefixes;

import polaris.Emulator;
import polaris.hotel.users.User;
import polaris.hotel.users.customization.UserCustomizationPurchaseService;
import polaris.hotel.users.customization.UserCustomizationPurchaseService.CustomPrefixRequest;
import polaris.hotel.users.customization.UserCustomizationPurchaseService.PurchaseResult;
import polaris.hotel.users.customization.UserCustomizationRepository;
import polaris.messages.incoming.MessageHandler;
import polaris.messages.outgoing.generic.alerts.BubbleAlertComposer;
import polaris.messages.outgoing.generic.alerts.BubbleAlertKeys;
import polaris.messages.outgoing.inventory.nickicons.UserNickIconsComposer;
import polaris.messages.outgoing.inventory.prefixes.ActivePrefixUpdatedComposer;
import polaris.messages.outgoing.inventory.prefixes.CustomPrefixPurchaseFailedComposer;
import polaris.messages.outgoing.inventory.prefixes.PrefixReceivedComposer;
import polaris.messages.outgoing.rooms.users.RoomUserDataComposer;
import polaris.messages.outgoing.users.UserCreditsComposer;
import polaris.messages.outgoing.users.UserCurrencyComposer;

public class PurchasePrefixEvent extends MessageHandler {

    @Override
    public int getRatelimit() {
        return 500;
    }

    @Override
    public void handle() throws Exception {
        CustomPrefixRequest request = new CustomPrefixRequest(
                this.packet.readString(),
                this.packet.readString(),
                this.packet.readString(),
                this.packet.readString(),
                this.packet.readString());
        User user = this.client.getUser();
        if (user == null) {
            return;
        }

        UserCustomizationPurchaseService service = new UserCustomizationPurchaseService(
                new UserCustomizationRepository(Emulator.getDatabase().getDataSource()),
                Emulator.getGameEnvironment().getWordFilter().getWords());
        PurchaseResult result = service.purchaseCustomPrefix(user, request);
        if (result.status() != UserCustomizationPurchaseService.Status.SUCCESS) {
            this.fail(result.message());
            return;
        }

        if (result.creditsChanged()) {
            this.client.sendResponse(new UserCreditsComposer(user));
        }
        if (result.currencyChanged()) {
            this.client.sendResponse(new UserCurrencyComposer(user));
        }
        result.prefix().run();
        user.getInventory().getPrefixesComponent().addPrefix(result.prefix());
        user.getInventory().getPrefixesComponent().setActive(result.prefix().getId());
        this.client.sendResponse(new PrefixReceivedComposer(result.prefix()));
        this.client.sendResponse(new ActivePrefixUpdatedComposer(result.prefix()));
        this.client.sendResponse(new UserNickIconsComposer(user));
        if (user.getUserInfo().getCurrentRoom() != null) {
            user.getUserInfo().getCurrentRoom().sendComposer(new RoomUserDataComposer(user).compose());
        }
    }

    private void fail(String message) {
        this.client.sendResponse(new BubbleAlertComposer(BubbleAlertKeys.FURNITURE_PLACEMENT_ERROR.key, message));
        this.client.sendResponse(new CustomPrefixPurchaseFailedComposer(message));
    }

    private boolean containsControlChars(String text) {
        return UserCustomizationPurchaseService.containsControlChars(text);
    }

    private boolean isAllowedFont(String font) {
        return UserCustomizationPurchaseService.isAllowedFont(font);
    }

    private boolean isAllowedEffect(String effect) {
        return UserCustomizationPurchaseService.isAllowedEffect(effect);
    }

    private boolean isValidIcon(String icon) {
        return UserCustomizationPurchaseService.isValidIcon(icon);
    }
}
