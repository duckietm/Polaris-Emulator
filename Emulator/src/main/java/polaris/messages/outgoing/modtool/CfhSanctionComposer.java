package polaris.messages.outgoing.modtool;

import polaris.messages.ISerialize;
import polaris.messages.ServerMessage;
import polaris.messages.outgoing.MessageComposer;
import polaris.messages.outgoing.Outgoing;

public class CfhSanctionComposer extends MessageComposer {
    private final int unknownInt1;
    private final int accountId;
    private final CFHSanction sanction;

    public CfhSanctionComposer(int unknownInt1, int accountId, CFHSanction sanction) {
        this.unknownInt1 = unknownInt1;
        this.accountId = accountId;
        this.sanction = sanction;
    }

    @Override
    protected ServerMessage composeInternal() {
        this.response.init(Outgoing.CfhSanctionComposer);
        this.response.appendInt(this.unknownInt1);
        this.response.appendInt(this.accountId);
        this.sanction.serialize(this.response);
        return this.response;
    }

    public static class CFHSanction implements ISerialize {
        private final String name;
        private final int length;
        private final int unknownInt1;
        private final boolean avatarOnly;
        private final String tradelockInfo;
        private final String machineBanInfo;

        public CFHSanction(
                String name,
                int length,
                int unknownInt1,
                boolean avatarOnly,
                String tradelockInfo,
                String machineBanInfo) {
            this.name = name;
            this.length = length;
            this.unknownInt1 = unknownInt1;
            this.avatarOnly = avatarOnly;
            this.tradelockInfo = tradelockInfo;
            this.machineBanInfo = machineBanInfo;
        }

        @Override
        public void serialize(ServerMessage message) {
            message.appendString(this.name);
            message.appendInt(this.length);
            message.appendInt(this.unknownInt1);
            message.appendBoolean(this.avatarOnly);
            message.appendString(this.tradelockInfo);
            message.appendString(this.machineBanInfo);
        }

        public String getName() {
            return name;
        }

        public int getLength() {
            return length;
        }

        public int getUnknownInt1() {
            return unknownInt1;
        }

        public boolean isAvatarOnly() {
            return avatarOnly;
        }

        public String getTradelockInfo() {
            return tradelockInfo;
        }

        public String getMachineBanInfo() {
            return machineBanInfo;
        }
    }

    public int getUnknownInt1() {
        return unknownInt1;
    }

    public int getAccountId() {
        return accountId;
    }

    public CFHSanction getSanction() {
        return sanction;
    }
}
