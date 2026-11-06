package polaris.plugin.events.users;

import polaris.hotel.users.User;

public class UserGetIPAddressEvent extends UserEvent {
    public final String oldIp;

    private String updatedIp;
    private boolean changedIP = false;

    public UserGetIPAddressEvent(User user, String ip) {
        super(user);
        this.oldIp = ip;
    }

    public void setUpdatedIp(String updatedIp) {
        this.updatedIp = updatedIp;
        this.changedIP = true;
    }

    public boolean hasChangedIP() {
        return changedIP;
    }

    public String getUpdatedIp() {
        return updatedIp;
    }
}
