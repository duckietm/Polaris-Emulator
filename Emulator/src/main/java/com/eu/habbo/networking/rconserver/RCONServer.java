package com.eu.habbo.networking.rconserver;

import com.eu.habbo.Emulator;
import com.eu.habbo.messages.rcon.*;
import com.eu.habbo.networking.Server;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import gnu.trove.map.hash.THashMap;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressWarnings({"rawtypes", "unchecked"})
public class RCONServer extends Server {

    private static final Logger LOGGER = LoggerFactory.getLogger(RCONServer.class);

    private final THashMap<String, Class<? extends RCONMessage>> messages;
    private final GsonBuilder gsonBuilder;
    private final boolean rateLimitEnabled;
    private final LoadingCache<String, RateLimiter> rateLimiters;
    List<String> allowedAdresses = new ArrayList<>();

    public RCONServer(String host, int port) throws Exception {
        super("RCON Server", host, port, 1, 2);

        this.messages = new THashMap<>();

        this.gsonBuilder = new GsonBuilder();
        this.gsonBuilder.registerTypeAdapter(RCONMessage.class, new RCONMessage.RCONMessageSerializer());
        this.rateLimitEnabled = Emulator.getConfig().getBoolean("rcon.rate_limit.enabled", true);
        RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                .limitForPeriod(Math.max(1, Emulator.getConfig().getInt("rcon.rate_limit.limit_for_period", 60)))
                .limitRefreshPeriod(Duration.ofMillis(Math.max(100, Emulator.getConfig().getInt("rcon.rate_limit.refresh_period_ms", 1000))))
                .timeoutDuration(Duration.ofMillis(Math.max(0, Emulator.getConfig().getInt("rcon.rate_limit.timeout_ms", 0))))
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
        this.addRCONMessage("updatesoundboard", UpdateSoundboard.class);
        this.addRCONMessage("updatecatalog", UpdateCatalog.class);
        this.addRCONMessage("executecommand", ExecuteCommand.class);
        this.addRCONMessage("progressachievement", ProgressAchievement.class);
        this.addRCONMessage("updateuser", UpdateUser.class);
        this.addRCONMessage("friendrequest", FriendRequest.class);
        this.addRCONMessage("imagehotelalert", ImageHotelAlert.class);
        this.addRCONMessage("imagealertuser", ImageAlertUser.class);
        this.addRCONMessage("stalkuser", StalkUser.class);
        this.addRCONMessage("staffalert", StaffAlert.class);
        this.addRCONMessage("modticket", CreateModToolTicket.class);
        this.addRCONMessage("talkuser", TalkUser.class);
        this.addRCONMessage("changeroomowner", ChangeRoomOwner.class);
        this.addRCONMessage("muteuser", MuteUser.class);
        this.addRCONMessage("giverespect", GiveRespect.class);
        this.addRCONMessage("ignoreuser", IgnoreUser.class);
        this.addRCONMessage("setmotto", SetMotto.class);
        this.addRCONMessage("giveuserclothing", GiveUserClothing.class);
        this.addRCONMessage("modifysubscription", ModifyUserSubscription.class);
        this.addRCONMessage("changeusername", ChangeUsername.class);
        this.addRCONMessage("updateitems", UpdateItems.class);

        Collections.addAll(this.allowedAdresses, Emulator.getConfig().getValue("rcon.allowed", "127.0.0.1").split(";"));
    }

    @Override
    public void initializePipeline() {
        super.initializePipeline();

        this.serverBootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ch.pipeline().addLast(new RCONServerHandler());
            }
        });
    }


    public void addRCONMessage(String key, Class<? extends RCONMessage> clazz) {
        this.messages.put(key, clazz);
    }

    public String handle(ChannelHandlerContext ctx, String key, String body) throws Exception {
        if (!this.acquirePermit(ctx)) {
            LOGGER.warn("RCON rate limit exceeded for {}", remoteAddress(ctx));
            return "RATE_LIMITED";
        }

        Class<? extends RCONMessage> message = this.messages.get(key.replace("_", "").toLowerCase());

        String result;
        if (message != null) {
            try {
                RCONMessage rcon = message.getDeclaredConstructor().newInstance();
                Gson gson = this.gsonBuilder.create();
                Object payload = gson.fromJson(body, rcon.type);
                if (rcon.validate(payload)) {
                    rcon.handle(gson, payload);
                }
                LOGGER.info("Handled RCON Message: {}", message.getSimpleName());
                result = gson.toJson(rcon, RCONMessage.class);

                if (Emulator.debugging) {
                    LOGGER.debug("RCON Data {} RCON Result {}", body, result);
                }

                return result;
            } catch (Exception ex) {
                LOGGER.error("Failed to handle RCONMessage", ex);
            }
        } else {
            LOGGER.error("Couldn't find: {}", key);
        }

        throw new ArrayIndexOutOfBoundsException("Unhandled RCON Message");
    }

    public List<String> getCommands() {
        return new ArrayList<>(this.messages.keySet());
    }

    private boolean acquirePermit(ChannelHandlerContext ctx) {
        return !this.rateLimitEnabled || this.rateLimiters.get(remoteAddress(ctx)).acquirePermission();
    }

    private static String remoteAddress(ChannelHandlerContext ctx) {
        if (ctx == null || ctx.channel() == null) {
            return "unknown";
        }

        SocketAddress address = ctx.channel().remoteAddress();
        return address == null ? "unknown" : address.toString();
    }
}
