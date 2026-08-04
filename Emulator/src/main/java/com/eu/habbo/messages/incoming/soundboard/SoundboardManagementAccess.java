package com.eu.habbo.messages.incoming.soundboard;

import com.eu.habbo.habbohotel.users.Habbo;

final class SoundboardManagementAccess {
    static final String PERMISSION = "acc_soundboard_manage";

    private SoundboardManagementAccess() {}

    static boolean canManage(Habbo habbo) {
        return habbo != null && habbo.hasPermission(PERMISSION);
    }
}
