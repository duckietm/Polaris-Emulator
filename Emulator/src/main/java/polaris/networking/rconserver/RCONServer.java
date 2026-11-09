package polaris.networking.rconserver;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.gson.GsonBuilder;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import polaris.Emulator;
import polaris.messages.command.CommandRegistry;
import polaris.messages.rcon.AlertUser;
import polaris.messages.rcon.BanUser;
import polaris.messages.rcon.ChangeRoomOwner;
import polaris.messages.rcon.ChangeUsername;
import polaris.messages.rcon.CreateModToolTicket;
import polaris.messages.rcon.DisconnectUser;
import polaris.messages.rcon.EpicPopup;
import polaris.messages.rcon.ExecuteCommand;
import polaris.messages.rcon.ForwardUser;
import polaris.messages.rcon.FriendRequest;
import polaris.messages.rcon.GiveBadge;
import polaris.messages.rcon.GiveCredits;
import polaris.messages.rcon.GivePixels;
import polaris.messages.rcon.GivePoints;
import polaris.messages.rcon.GiveRespect;
import polaris.messages.rcon.GiveUserClothing;
import polaris.messages.rcon.HotelAlert;
import polaris.messages.rcon.IgnoreUser;
import polaris.messages.rcon.ImageAlertUser;
import polaris.messages.rcon.ImageHotelAlert;
import polaris.messages.rcon.ModifyUserSubscription;
import polaris.messages.rcon.MuteUser;
import polaris.messages.rcon.ProgressAchievement;
import polaris.messages.rcon.RCONMessage;
import polaris.messages.rcon.ReloadContent;
import polaris.messages.rcon.SendGift;
import polaris.messages.rcon.SendRoomBundle;
import polaris.messages.rcon.SetMotto;
import polaris.messages.rcon.SetRank;
import polaris.messages.rcon.StaffAlert;
import polaris.messages.rcon.StalkUser;
import polaris.messages.rcon.StressStart;
import polaris.messages.rcon.StressStatus;
import polaris.messages.rcon.StressStop;
import polaris.messages.rcon.TalkUser;
import polaris.messages.rcon.UnbanUser;
import polaris.messages.rcon.UpdateCatalog;
import polaris.messages.rcon.UpdateItems;
import polaris.messages.rcon.UpdateSoundboard;
import polaris.messages.rcon.UpdateUser;
import polaris.messages.rcon.UpdateWheel;
import polaris.messages.rcon.UpdateWordfilter;
import polaris.networking.Server;

@SuppressWarnings({"rawtypes", "unchecked"})
public class RCONServer extends Server {

    private static final Logger LOGGER = LoggerFactory.getLogger(RCONServer.class);

    private final CommandRegistry registry;
    private final GsonBuilder gsonBuilder;
    private final boolean rateLimitEnabled;
    private final LoadingCache<String, RateLimiter> rateLimiters;
    List<String> allowedAdresses = new ArrayList<>();

    public RCONServer(String host, int port) throws Exception {
        this(host, port, false);
    }

    public RCONServer(String host, int port, boolean stressEnabled) throws Exception {
        super("RCON Server", host, port, 1, 2);

        this.registry = new CommandRegistry();

        this.gsonBuilder = new GsonBuilder();
        this.gsonBuilder.registerTypeAdapter(RCONMessage.class, new RCONMessage.RCONMessageSerializer());
        this.rateLimitEnabled = Emulator.getConfig().getBoolean("rcon.rate_limit.enabled", true);
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(Math.max(1, Emulator.getConfig().getInt("rcon.rate_limit.limit_for_period", 60)))
                .limitRefreshPeriod(Duration.ofMillis(
                        Math.max(100, Emulator.getConfig().getInt("rcon.rate_limit.refresh_period_ms", 1000))))
                .timeoutDuration(
                        Duration.ofMillis(Math.max(0, Emulator.getConfig().getInt("rcon.rate_limit.timeout_ms", 0))))
                .build();
        this.rateLimiters = Caffeine.newBuilder()
                .maximumSize(512)
                .expireAfterAccess(Duration.ofMinutes(10))
                .build(address -> RateLimiter.of("rcon-" + address, rateLimiterConfig));

        this.addRCONMessage("alertuser", AlertUser.class);
        this.addRCONMessage("disconnect", DisconnectUser.class);
        this.addRCONMessage("forwarduser", ForwardUser.class);
        this.addRCONMessage("givebadge", GiveBadge.class);
        this.addRCONMessage("givecredits", GiveCredits.class);
        this.addRCONMessage("givepixels", GivePixels.class);
        this.addRCONMessage("givepoints", GivePoints.class);
        this.addRCONMessage("hotelalert", HotelAlert.class);
        this.addRCONMessage("sendgift", SendGift.class);
        this.addRCONMessage("sendroombundle", SendRoomBundle.class);
        this.addRCONMessage("setrank", SetRank.class);
        this.addRCONMessage("updatewordfilter", UpdateWordfilter.class);
        this.addRCONMessage("updatewheel", UpdateWheel.class);
        this.addRCONMessage("reloadcontent", ReloadContent.class);
        this.addRCONMessage("updatesoundboard", UpdateSoundboard.class);
        this.addRCONMessage("updatecatalog", UpdateCatalog.class);
        this.addRCONMessage("executecommand", ExecuteCommand.class);
        this.addRCONMessage("progressachievement", ProgressAchievement.class);
        this.addRCONMessage("updateuser", UpdateUser.class);
        this.addRCONMessage("friendrequest", FriendRequest.class);
        this.addRCONMessage("imagehotelalert", ImageHotelAlert.class);
        this.addRCONMessage("imagealertuser", ImageAlertUser.class);
        this.addRCONMessage("epicpopup", EpicPopup.class);
        this.addRCONMessage("stalkuser", StalkUser.class);
        this.addRCONMessage("staffalert", StaffAlert.class);
        this.addRCONMessage("modticket", CreateModToolTicket.class);
        this.addRCONMessage("talkuser", TalkUser.class);
        this.addRCONMessage("changeroomowner", ChangeRoomOwner.class);
        this.addRCONMessage("muteuser", MuteUser.class);
        this.addRCONMessage("banuser", BanUser.class);
        this.addRCONMessage("unbanuser", UnbanUser.class);
        this.addRCONMessage("giverespect", GiveRespect.class);
        this.addRCONMessage("ignoreuser", IgnoreUser.class);
        this.addRCONMessage("setmotto", SetMotto.class);
        this.addRCONMessage("giveuserclothing", GiveUserClothing.class);
        this.addRCONMessage("modifysubscription", ModifyUserSubscription.class);
        this.addRCONMessage("changeusername", ChangeUsername.class);
        this.addRCONMessage("updateitems", UpdateItems.class);
        if (stressEnabled) {
            this.addRCONMessage("stressstart", StressStart.class);
            this.addRCONMessage("stressstatus", StressStatus.class);
            this.addRCONMessage("stressstop", StressStop.class);
            LOGGER.warn("Polaris stress controls are ENABLED on the RCON listener");
        }

        Collections.addAll(
                this.allowedAdresses,
                Emulator.getConfig().getValue("rcon.allowed", "127.0.0.1").split(";"));
    }

    @Override
    public void initializePipeline() {
        super.initializePipeline();

        this.serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                // One TCP read is not one request: a long command arrives in several segments, and a
                // fast client may send its object in pieces. Frame on the JSON object itself.
                ch.pipeline().addLast(RCONServerHandler.framer(), new RCONServerHandler());
            }
        });
    }

    public void addRCONMessage(String key, Class<? extends RCONMessage> clazz) {
        this.registry.register(key, clazz);
    }

    public String handle(ChannelHandlerContext ctx, String key, String body) throws Exception {
        if (!this.acquirePermit(ctx)) {
            LOGGER.warn("RCON rate limit exceeded for {}", remoteAddress(ctx));
            return RconResponse.error(RCONMessage.STATUS_ERROR, "rate limited").toJson(this.gsonBuilder.create());
        }

        // Command lookup, payload parsing, validation and handling are transport-neutral
        // and shared with the CMS HTTP API through CommandRegistry. The {status, message}
        // envelope it produces is byte-identical to the legacy RCON response.
        return this.registry.dispatch(key, body).toResponseJson();
    }

    public List<String> getCommands() {
        return this.registry.getCommands();
    }

    /**
     * The shared, transport-neutral command registry. Exposed so the CMS HTTP API
     * can dispatch the exact same commands (with the same validation and response
     * envelope) without duplicating registrations.
     */
    public CommandRegistry getRegistry() {
        return this.registry;
    }

    private boolean acquirePermit(ChannelHandlerContext ctx) {
        return !this.rateLimitEnabled
                || this.rateLimiters.get(remoteAddress(ctx)).acquirePermission();
    }

    private static String remoteAddress(ChannelHandlerContext ctx) {
        if (ctx == null || ctx.channel() == null) {
            return "unknown";
        }

        return rateLimitKey(ctx.channel().remoteAddress());
    }

    /**
     * The key of a caller's rate limiter: its IP address alone. The textual form of a socket address carries the
     * source port, which is new on every connection, so keying on it gave each request a fresh limiter.
     */
    static String rateLimitKey(SocketAddress address) {
        if (address instanceof InetSocketAddress inet && inet.getAddress() != null) {
            return inet.getAddress().getHostAddress();
        }

        return address == null ? "unknown" : address.toString();
    }
}
