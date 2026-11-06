package polaris.messages.incoming.soundboard;

import polaris.hotel.users.User;

final class SoundboardManagementAccess {
    static final String PERMISSION = "acc_soundboard_manage";

    private SoundboardManagementAccess() {}

    static boolean canManage(User user) {
        return user != null && user.hasPermission(PERMISSION);
    }
}
