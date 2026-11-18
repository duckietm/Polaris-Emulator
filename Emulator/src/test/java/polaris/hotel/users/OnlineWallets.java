package polaris.hotel.users;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** An online user with a real wallet lock, for tests outside this package that coordinate wallets. */
public final class OnlineWallets {

    private OnlineWallets() {}

    public static User online(int id) {
        User user = mock(User.class);
        UserInfo info = new UserInfo(id, 0);
        when(user.getUserInfo()).thenReturn(info);
        return user;
    }
}
