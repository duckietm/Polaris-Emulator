package polaris.hotel.permissions;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PermissionSettingContractTest {

    @Test
    void unknownPermissionValuesFailClosed() {
        assertEquals(PermissionSetting.DISALLOWED, PermissionSetting.fromString(null));
        assertEquals(PermissionSetting.DISALLOWED, PermissionSetting.fromString(""));
        assertEquals(PermissionSetting.DISALLOWED, PermissionSetting.fromString("999"));
    }

    @Test
    void knownPermissionValuesMapToExplicitSettings() {
        assertEquals(PermissionSetting.ALLOWED, PermissionSetting.fromString("1"));
        assertEquals(PermissionSetting.ROOM_OWNER, PermissionSetting.fromString("2"));
    }
}
