package com.eu.habbo.habbohotel.items.interactions.wired.contract;

import com.eu.habbo.messages.ServerMessage;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A contract as the official contract editor reads and writes it (AIR {@code WiredContractController}, {@code
 * AbstractContract.addContentsToComposer}, {@code PaymentContract}, {@code RewardContract}).
 *
 * <pre>
 *   int contractId, short type (0 payment, 1 trade, 2 reward),
 *   TradeRequirementRulesDefinition: bool hasGive, [int n, rule[n]], bool hasGet, [rule],
 *     rule: int count, node[count]; node: byte type (0 coins, 1 furni), int amount,
 *     furni: bool wall, int typeId (furnidata id), string legacy poster id,
 *   payment: short mode, string receive text, string layout type,
 *   reward:  short earnings category, bool show by default, string reward text
 * </pre>
 *
 * <p>The same shape travels both ways, so one reader and one writer serve the contents packet and the update packet.
 *
 * @param give the payment alternatives, or null when the contract type has none (reward)
 * @param get the reward rule, or null when the contract type has none (payment)
 */
public record WiredNativeContract(
        int contractId,
        int contractType,
        List<List<Node>> give,
        List<Node> get,
        int paymentMode,
        String receiveText,
        String layoutType,
        int rewardCategory,
        boolean showDialog,
        String rewardText) {

    public static final int TYPE_PAYMENT = 0;
    public static final int TYPE_TRADE = 1;
    public static final int TYPE_REWARD = 2;

    public static final int NODE_COINS = 0;
    public static final int NODE_FURNI = 1;

    /** AIR {@code PaymentContract}: 0 "something (donation)", 1 "specific payment". */
    public static final int PAYMENT_MODE_DONATION = 0;

    public static final int PAYMENT_MODE_SPECIFIC = 1;

    public static final String LAYOUT_GENERIC = "generic";
    public static final String LAYOUT_GAMES = "games";

    /** AIR {@code RewardContract} earnings categories (credit earnings 11 and 13). */
    public static final int EARNINGS_CATEGORY_DEFAULT = 11;

    public static final int EARNINGS_CATEGORY_ALTERNATIVE = 13;

    /** AIR editor caps: {@code AddEditContractElement}, {@code TradeRuleEditorPreset}, {@code TradeRuleListEditorPreset}. */
    public static final int MAX_COINS = 100_000;

    public static final int MAX_FURNI = 500;
    public static final int MAX_NODES = 5;
    public static final int MAX_PAYMENT_RULES = 3;

    /** AIR text inputs: receive text 60 ({@code TextInputParam}), reward text 200 ({@code TextAreaParam}). */
    public static final int MAX_RECEIVE_TEXT = 60;

    public static final int MAX_REWARD_TEXT = 200;
    public static final int MAX_POSTER_ID = 20;

    /** One coin pile or one pile of a furni type. */
    public record Node(int type, int amount, boolean wall, int typeId, String posterId) {
        public static Node coins(int amount) {
            return new Node(NODE_COINS, amount, false, 0, "");
        }

        public static Node furni(int amount, boolean wall, int typeId, String posterId) {
            return new Node(NODE_FURNI, amount, wall, typeId, posterId == null ? "" : posterId);
        }

        public boolean isFurni() {
            return this.type == NODE_FURNI;
        }
    }

    public WiredNativeContract {
        give = give == null ? null : copyRules(give);
        get = get == null ? null : List.copyOf(get);
        receiveText = receiveText == null ? "" : receiveText;
        layoutType = layoutType == null ? LAYOUT_GENERIC : layoutType;
        rewardText = rewardText == null ? "" : rewardText;
    }

    private static List<List<Node>> copyRules(List<List<Node>> rules) {
        List<List<Node>> copy = new ArrayList<>();
        for (List<Node> rule : rules) copy.add(rule == null ? List.of() : List.copyOf(rule));
        return Collections.unmodifiableList(copy);
    }

    /** The same contract under another id (the database id after a read, the wire id before a write). */
    public WiredNativeContract withContractId(int id) {
        return new WiredNativeContract(
                id,
                contractType,
                give,
                get,
                paymentMode,
                receiveText,
                layoutType,
                rewardCategory,
                showDialog,
                rewardText);
    }

    /** Write the whole contract, as the contents packet carries it. */
    public void write(ServerMessage message) {
        message.appendInt(this.contractId);
        message.appendShort(this.contractType);
        message.appendBoolean(this.give != null);
        if (this.give != null) {
            message.appendInt(this.give.size());
            for (List<Node> rule : this.give) writeRule(message, rule);
        }
        message.appendBoolean(this.get != null);
        if (this.get != null) writeRule(message, this.get);
        if (this.contractType == TYPE_PAYMENT) {
            message.appendShort(this.paymentMode);
            message.appendString(this.receiveText);
            message.appendString(this.layoutType);
        }
        if (this.contractType == TYPE_REWARD) {
            message.appendShort(this.rewardCategory);
            message.appendBoolean(this.showDialog);
            message.appendString(this.rewardText);
        }
    }

    private static void writeRule(ServerMessage message, List<Node> rule) {
        message.appendInt(rule.size());
        for (Node node : rule) {
            message.appendByte(node.type());
            message.appendInt(node.amount());
            if (node.isFurni()) {
                message.appendBoolean(node.wall());
                message.appendInt(node.typeId());
                message.appendString(node.posterId());
            }
        }
    }

    /**
     * Read an update packet, strictly: a short, malformed or over-long packet is refused as a whole, so nothing a
     * client cut off half way can reach the contract.
     *
     * @throws IllegalArgumentException when the packet is not one well-formed contract
     */
    public static WiredNativeContract read(ByteBuf buffer) {
        Reader in = new Reader(buffer);
        int contractId = in.readInt();
        int type = in.readShort();
        if (type < TYPE_PAYMENT || type > TYPE_REWARD) throw new IllegalArgumentException("contract type " + type);

        List<List<Node>> give = null;
        if (in.readBoolean()) {
            int count = in.readInt();
            if (count < 0 || count > MAX_PAYMENT_RULES) throw new IllegalArgumentException("payment rules " + count);
            give = new ArrayList<>();
            for (int i = 0; i < count; i++) give.add(readRule(in));
        }
        List<Node> get = in.readBoolean() ? readRule(in) : null;

        int paymentMode = PAYMENT_MODE_SPECIFIC;
        String receiveText = "";
        String layoutType = LAYOUT_GENERIC;
        int rewardCategory = EARNINGS_CATEGORY_DEFAULT;
        boolean showDialog = false;
        String rewardText = "";
        if (type == TYPE_PAYMENT) {
            paymentMode = in.readShort();
            receiveText = in.readString();
            layoutType = in.readString();
        }
        if (type == TYPE_REWARD) {
            rewardCategory = in.readShort();
            showDialog = in.readBoolean();
            rewardText = in.readString();
        }
        if (buffer.isReadable()) throw new IllegalArgumentException("trailing bytes");

        return new WiredNativeContract(
                contractId,
                type,
                give,
                get,
                paymentMode,
                receiveText,
                layoutType,
                rewardCategory,
                showDialog,
                rewardText);
    }

    private static List<Node> readRule(Reader in) {
        int count = in.readInt();
        if (count < 0 || count > MAX_NODES) throw new IllegalArgumentException("rule nodes " + count);
        List<Node> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int type = in.readByte();
            int amount = in.readInt();
            if (type == NODE_COINS) {
                nodes.add(Node.coins(amount));
            } else if (type == NODE_FURNI) {
                boolean wall = in.readBoolean();
                int typeId = in.readInt();
                String posterId = in.readString();
                if (posterId.length() > MAX_POSTER_ID) throw new IllegalArgumentException("poster id");
                nodes.add(Node.furni(amount, wall, typeId, posterId));
            } else {
                throw new IllegalArgumentException("node type " + type);
            }
        }
        return nodes;
    }

    /** Reads that refuse to run past the end of the packet instead of answering zero. */
    private record Reader(ByteBuf buffer) {
        private void need(int bytes) {
            if (this.buffer.readableBytes() < bytes) throw new IllegalArgumentException("truncated contract");
        }

        int readInt() {
            need(4);
            return this.buffer.readInt();
        }

        int readShort() {
            need(2);
            return this.buffer.readShort();
        }

        int readByte() {
            need(1);
            return this.buffer.readByte();
        }

        boolean readBoolean() {
            return readByte() == 1;
        }

        String readString() {
            int length = readShort() & 0xFFFF;
            need(length);
            byte[] data = new byte[length];
            this.buffer.readBytes(data);
            return new String(data, StandardCharsets.UTF_8);
        }
    }
}
