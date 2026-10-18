package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.eu.habbo.habbohotel.items.interactions.wired.contract.WiredNativeContract.Node;
import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The official contract editor's layout (AIR {@code WiredContractController}, {@code AbstractContract}, {@code
 * PaymentContract}, {@code RewardContract}). The payment fixture is the one the renderer's WiredContractProtocol test
 * pins, so both ends agree byte for byte.
 */
class WiredNativeContractWireTest {
    private static final String PAYMENT_HEX =
            "0000002a00000100000001000000020000000007010000000201000000010001390000010003506179000767656e65726963";

    private static final WiredNativeContract PAYMENT = new WiredNativeContract(
            42,
            WiredNativeContract.TYPE_PAYMENT,
            List.of(List.of(Node.coins(7), Node.furni(2, true, 1, "9"))),
            null,
            WiredNativeContract.PAYMENT_MODE_SPECIFIC,
            "Pay",
            WiredNativeContract.LAYOUT_GENERIC,
            WiredNativeContract.EARNINGS_CATEGORY_DEFAULT,
            false,
            "");

    private static String encode(WiredNativeContract contract) {
        ServerMessage message = new ServerMessage(1);
        contract.write(message);
        ByteBuf frame = message.get();
        // Frame length (4) and header (2) come first.
        return ByteBufUtil.hexDump(frame, 6, frame.readableBytes() - 6);
    }

    private static WiredNativeContract decode(String hex) {
        return WiredNativeContract.read(Unpooled.wrappedBuffer(HexFormat.of().parseHex(hex)));
    }

    @Test
    void writesAndReadsTheOfficialPaymentLayout() {
        assertEquals(PAYMENT_HEX, encode(PAYMENT));
        assertEquals(PAYMENT, decode(PAYMENT_HEX));
    }

    @Test
    void roundTripsTradeAndRewardWithTheirOwnOptions() {
        WiredNativeContract trade = new WiredNativeContract(
                7,
                WiredNativeContract.TYPE_TRADE,
                List.of(List.of(Node.coins(3)), List.of(Node.furni(1, false, 1389, ""))),
                List.of(Node.coins(11)),
                WiredNativeContract.PAYMENT_MODE_SPECIFIC,
                "",
                WiredNativeContract.LAYOUT_GENERIC,
                WiredNativeContract.EARNINGS_CATEGORY_DEFAULT,
                false,
                "");
        WiredNativeContract reward = new WiredNativeContract(
                8,
                WiredNativeContract.TYPE_REWARD,
                null,
                List.of(Node.coins(5)),
                WiredNativeContract.PAYMENT_MODE_SPECIFIC,
                "",
                WiredNativeContract.LAYOUT_GENERIC,
                WiredNativeContract.EARNINGS_CATEGORY_ALTERNATIVE,
                true,
                "Thanks ♥");

        assertEquals(trade, decode(encode(trade)));
        assertEquals(reward, decode(encode(reward)));
        assertNull(decode(encode(reward)).give(), "an absent rule stays absent, not empty");
    }

    @Test
    void refusesEveryTruncationTrailingBytesAndBadCounts() {
        for (int length = 0; length < PAYMENT_HEX.length(); length += 2) {
            String truncated = PAYMENT_HEX.substring(0, length);
            assertThrows(IllegalArgumentException.class, () -> decode(truncated), "length " + length);
        }
        assertThrows(IllegalArgumentException.class, () -> decode(PAYMENT_HEX + "00"));
        assertThrows(IllegalArgumentException.class, () -> decode("0000002a0003"), "type 3");
        assertThrows(IllegalArgumentException.class, () -> decode("0000002a0000017fffffff"), "payment rule count");
        assertThrows(IllegalArgumentException.class, () -> decode("0000002a0000010000000100000006"), "node count");
        assertThrows(IllegalArgumentException.class, () -> decode("0000002a000001000000010000000102"), "node type 2");
    }
}
