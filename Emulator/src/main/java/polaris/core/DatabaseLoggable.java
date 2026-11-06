package polaris.core;

import java.sql.PreparedStatement;
import java.sql.SQLException;

public interface DatabaseLoggable {

    String getQuery();

    void log(PreparedStatement statement) throws SQLException;
}
