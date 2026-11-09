package polaris.messages.incoming.housekeeping;

import polaris.hotel.users.User;

/**
 * Who carries out a housekeeping action: a logged-in staff member from the panel, or the system
 * operator behind the RCON {@code housekeeping}, {@code banuser} and {@code unbanuser} commands
 * (the All-in-1 Manager). The actions' shared cores audit with this operator; the rank-of-operator
 * guards are checked by the panel handler and handed to the core, so the system operator, which
 * has no rank, simply passes them.
 *
 * @param id   the operator's user id, {@link #SYSTEM_ID} for the system operator
 * @param name the name the audit log and alert signatures show
 * @param ip   the operator's address for the audit log, {@link #SYSTEM_IP} for RCON
 * @param user the staff member, or null for the system operator
 */
public record HousekeepingOperator(int id, String name, String ip, User user) {
    public static final int SYSTEM_ID = 0;
    public static final String SYSTEM_NAME = "All-in-1 Manager";
    public static final String SYSTEM_IP = "rcon";

    private static final HousekeepingOperator SYSTEM =
            new HousekeepingOperator(SYSTEM_ID, SYSTEM_NAME, SYSTEM_IP, null);

    /** A guard the system operator always passes. */
    public static final java.util.function.BooleanSupplier ALLOWED = () -> true;

    static HousekeepingOperator staff(User user) {
        return new HousekeepingOperator(
                user.getUserInfo().getId(),
                user.getUserInfo().getUsername(),
                user.getUserInfo().getIpLogin(),
                user);
    }

    public static HousekeepingOperator system() {
        return SYSTEM;
    }

    public boolean isSystem() {
        return this.user == null;
    }

    /** A reload step a core runs after its change: the panel runs the :update_* command, RCON its static reload. */
    @FunctionalInterface
    public interface Reload {
        void run() throws Exception;
    }
}
