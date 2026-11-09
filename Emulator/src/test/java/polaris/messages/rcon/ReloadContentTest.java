package polaris.messages.rcon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import polaris.core.ContentReloader;

/** RCON reloadcontent: the All-in-1-Manager's reload buttons. */
class ReloadContentTest {

    private final Gson gson = new Gson();

    @AfterEach
    void tearDown() {
        ContentReloader.uninstall();
    }

    private static ReloadContent.ReloadContentJSON target(String name) {
        ReloadContent.ReloadContentJSON json = new ReloadContent.ReloadContentJSON();
        json.target = name;
        return json;
    }

    @Test
    void reloadsTheTarget() {
        List<String> ran = new ArrayList<>();
        ContentReloader.install(new ContentReloader(Map.of("achievements", () -> ran.add("achievements"))));
        ReloadContent command = new ReloadContent();

        command.handle(this.gson, target("achievements"));

        assertEquals(RCONMessage.STATUS_OK, command.status);
        assertEquals(List.of("achievements"), ran);
    }

    @Test
    void answersAnUnknownTargetWithTheList() {
        ContentReloader.install(new ContentReloader(Map.of("polls", () -> {}, "calendar", () -> {})));
        ReloadContent command = new ReloadContent();

        command.handle(this.gson, target("catalog"));

        assertEquals(RCONMessage.STATUS_ERROR, command.status);
        assertTrue(command.message.contains("calendar, polls"), command.message);
    }

    @Test
    void reportsAFailedReload() {
        ContentReloader.install(new ContentReloader(Map.of("polls", () -> {
            throw new IllegalStateException("database down");
        })));
        ReloadContent command = new ReloadContent();

        command.handle(this.gson, target("polls"));

        assertEquals(RCONMessage.SYSTEM_ERROR, command.status);
        assertTrue(command.message.contains("database down"), command.message);
    }

    @Test
    void waitsForStartUp() {
        ReloadContent command = new ReloadContent();

        command.handle(this.gson, target("polls"));

        assertEquals(RCONMessage.STATUS_ERROR, command.status);
    }
}
