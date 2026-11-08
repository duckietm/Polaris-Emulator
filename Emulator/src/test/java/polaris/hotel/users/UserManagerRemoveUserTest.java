package polaris.hotel.users;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

/** A parked session that expires after its user logged in again leaves the new session online. */
class UserManagerRemoveUserTest {

    private static User user() {
        UserInfo info = mock(UserInfo.class);
        when(info.getId()).thenReturn(5);
        when(info.getUsername()).thenReturn("Someone");
        User user = mock(User.class);
        when(user.getUserInfo()).thenReturn(info);
        return user;
    }

    @Test
    void anOldSessionDoesNotRemoveTheNewOne() {
        UserManager manager = new UserManager(Runnable::run);
        User ghost = user();
        User fresh = user();
        manager.addUser(ghost);
        manager.addUser(fresh);

        manager.removeUser(ghost);

        assertSame(fresh, manager.getUser(5));
        assertSame(fresh, manager.getUser("someone"));
    }

    @Test
    void theSessionItselfIsRemoved() {
        UserManager manager = new UserManager(Runnable::run);
        User fresh = user();
        manager.addUser(fresh);

        manager.removeUser(fresh);

        assertNull(manager.getUser(5));
    }
}
