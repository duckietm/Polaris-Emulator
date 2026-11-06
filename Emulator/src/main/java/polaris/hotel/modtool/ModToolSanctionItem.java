package polaris.hotel.modtool;

public class ModToolSanctionItem {
    public int id;
    public int userId;
    public int sanctionLevel;
    public int probationTimestamp;
    public boolean isMuted;
    public int muteDuration;
    public int tradeLockedUntil;
    public String reason;

    public ModToolSanctionItem(
            int id,
            int userId,
            int sanctionLevel,
            int probationTimestamp,
            boolean isMuted,
            int muteDuration,
            int tradeLockedUntil,
            String reason) {
        this.id = id;
        this.userId = userId;
        this.sanctionLevel = sanctionLevel;
        this.probationTimestamp = probationTimestamp;
        this.isMuted = isMuted;
        this.muteDuration = muteDuration;
        this.tradeLockedUntil = tradeLockedUntil;
        this.reason = reason;
    }
}
