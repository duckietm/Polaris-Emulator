package com.eu.habbo.habbohotel.permissions;

public enum PermissionSetting {

    DISALLOWED,


    ALLOWED,


    ROOM_OWNER;

    public static PermissionSetting fromString(String value) {
        if (value == null) {
            return DISALLOWED;
        }

        switch (value) {
            case "1":
                return ALLOWED;
            case "2":
                return ROOM_OWNER;

        }

        return DISALLOWED;
    }
}
