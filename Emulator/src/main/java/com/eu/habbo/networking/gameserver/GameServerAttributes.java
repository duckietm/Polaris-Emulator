package com.eu.habbo.networking.gameserver;

import com.eu.habbo.crypto.HabboRC4;
import com.eu.habbo.habbohotel.gameclients.GameClient;
import io.netty.util.AttributeKey;

public class GameServerAttributes {

    public static final AttributeKey<GameClient> CLIENT = AttributeKey.valueOf("GameClient");
    public static final AttributeKey<HabboRC4> CRYPTO_CLIENT = AttributeKey.valueOf("CryptoClient");
    public static final AttributeKey<HabboRC4> CRYPTO_SERVER = AttributeKey.valueOf("CryptoServer");
    public static final AttributeKey<String> WS_IP = AttributeKey.valueOf("WebSocketIP");
    /** The visitor bucket this game connection is counted under; unset when it is not limited. */
    public static final AttributeKey<String> VISITOR = AttributeKey.valueOf("GameVisitor");

    public static final AttributeKey<byte[]> WS_AES_KEY = AttributeKey.valueOf("WsAesKey");
}
