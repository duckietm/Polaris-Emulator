package polaris.messages.outgoing.handshake;

import polaris.Emulator;
import polaris.hotel.bots.BotManager;
import polaris.hotel.mentions.MentionManager;
import polaris.hotel.rooms.RoomChatMessage;
import polaris.messages.ServerMessage;
import polaris.messages.incoming.catalog.CheckPetNameEvent;
import polaris.messages.incoming.users.SaveMottoEvent;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

/**
 * The hotel settings a client would otherwise repeat in its own configuration: the address photos are published
 * under, the lengths the server enforces and the words after @ it treats as mentions. Sent with the login state, so a
 * client shows the limits and aliases the server applies. New fields only ever go at the end.
 */
public class HotelSettingsComposer extends MessageComposer {
    public static final int PROTOCOL_VERSION = 1;

    private final String cameraUrl;
    private final int chatMaxLength;
    private final int mottoMaxLength;
    private final int botNameMaxLength;
    private final int petNameMaxLength;
    private final String everyoneAliases;
    private final String friendsAliases;
    private final String roomAliases;

    /** The values the hotel runs with now. */
    public HotelSettingsComposer() {
        this(
                Emulator.getConfig().getValue("camera.url", ""),
                RoomChatMessage.MAXIMUM_LENGTH,
                SaveMottoEvent.maximumLength(),
                BotManager.MAXIMUM_NAME_LENGTH,
                CheckPetNameEvent.PET_NAME_LENGTH_MAXIMUM,
                MentionManager.everyoneAliasList(),
                MentionManager.friendsAliasList(),
                MentionManager.roomAliasList());
    }

    public HotelSettingsComposer(
            String cameraUrl,
            int chatMaxLength,
            int mottoMaxLength,
            int botNameMaxLength,
            int petNameMaxLength,
            String everyoneAliases,
            String friendsAliases,
            String roomAliases) {
        this.cameraUrl = cameraUrl == null ? "" : cameraUrl;
        this.chatMaxLength = chatMaxLength;
        this.mottoMaxLength = mottoMaxLength;
        this.botNameMaxLength = botNameMaxLength;
        this.petNameMaxLength = petNameMaxLength;
        this.everyoneAliases = everyoneAliases == null ? "" : everyoneAliases;
        this.friendsAliases = friendsAliases == null ? "" : friendsAliases;
        this.roomAliases = roomAliases == null ? "" : roomAliases;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.HotelSettingsComposer);
        this.response.appendInt(PROTOCOL_VERSION);
        this.response.appendString(this.cameraUrl);
        this.response.appendInt(this.chatMaxLength);
        this.response.appendInt(this.mottoMaxLength);
        this.response.appendInt(this.botNameMaxLength);
        this.response.appendInt(this.petNameMaxLength);
        this.response.appendString(this.everyoneAliases);
        this.response.appendString(this.friendsAliases);
        this.response.appendString(this.roomAliases);

        return this.response;
    }
}
