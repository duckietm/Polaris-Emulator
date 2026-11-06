package polaris.plugin.events.users;

import polaris.hotel.commands.Command;
import polaris.hotel.users.User;

public class UserExecuteCommandEvent extends UserEvent {

    public final Command command;
    public final String[] params;
    private boolean success;

    public UserExecuteCommandEvent(User user, Command command, String[] params) {
        super(user);

        this.command = command;
        this.params = params;
        this.success = true;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public boolean isSuccess() {
        return success;
    }
}
