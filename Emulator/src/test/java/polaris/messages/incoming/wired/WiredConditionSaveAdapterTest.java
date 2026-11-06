package polaris.messages.incoming.wired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import polaris.hotel.items.interactions.InteractionWiredCondition;
import polaris.hotel.items.interactions.wired.WiredSettings;
import polaris.hotel.rooms.Room;
import polaris.hotel.rooms.RoomUnit;
import polaris.hotel.wired.WiredConditionType;
import polaris.hotel.wired.core.WiredContext;
import polaris.messages.ClientMessage;
import polaris.messages.ServerMessage;

class WiredConditionSaveAdapterTest {

    private final WiredConditionSaveAdapter adapter = new WiredConditionSaveAdapter();

    @Test
    void typedSettingsWinDeterministicallyWhenBothSaveDescriptorsExist() {
        ModernCondition condition = new ModernCondition();
        ClientMessage packet = new ClientMessage(
                0,
                Unpooled.buffer()
                        .writeInt(1)
                        .writeInt(73)
                        .writeShort(0)
                        .writeInt(0)
                        .writeInt(0) // quantifier code
                        .writeInt(0) // furni sources
                        .writeInt(0) // user sources
                        .writeInt(0) // variable ids
                        .writeInt(0)); // second furni picks

        assertTrue(this.adapter.save(condition, packet));
        assertEquals(73, condition.savedValue);
        assertFalse(condition.legacyCalled);
    }

    @Test
    void binaryConditionCompiledAgainstPacketSaveApiUsesItsOriginalPayload(@TempDir Path temp) throws Exception {
        Path classes = compileLegacyCondition(temp);

        try (URLClassLoader loader = new URLClassLoader(
                new java.net.URL[] {classes.toUri().toURL()}, WiredConditionSaveAdapterTest.class.getClassLoader())) {
            Class<?> type = Class.forName("fixture.LegacyPacketCondition", true, loader);
            InteractionWiredCondition condition =
                    (InteractionWiredCondition) type.getConstructor().newInstance();
            ClientMessage packet = new ClientMessage(0, Unpooled.buffer().writeInt(91));

            assertTrue(this.adapter.save(condition, packet));
            assertEquals(91, type.getField("savedValue").getInt(null));
        }
    }

    private static Path compileLegacyCondition(Path temp) throws Exception {
        Path sources = Files.createDirectories(temp.resolve("src"));
        Path classes = Files.createDirectories(temp.resolve("classes"));
        Path baseSource = sources.resolve("polaris/hotel/items/interactions/InteractionWiredCondition.java");
        Path conditionSource = sources.resolve("fixture/LegacyPacketCondition.java");
        Files.createDirectories(baseSource.getParent());
        Files.createDirectories(conditionSource.getParent());
        Files.writeString(baseSource, """
                package polaris.hotel.items.interactions;

                import polaris.hotel.items.Item;
                import polaris.messages.ClientMessage;

                public abstract class InteractionWiredCondition {
                    public InteractionWiredCondition(
                            int id,
                            int userId,
                            Item item,
                            String extradata,
                            int limitedStack,
                            int limitedSells) {}

                    public abstract boolean saveData(ClientMessage packet);
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(conditionSource, """
                package fixture;

                import polaris.hotel.items.interactions.InteractionWiredCondition;
                import polaris.messages.ClientMessage;

                public final class LegacyPacketCondition extends InteractionWiredCondition {
                    public static int savedValue;

                    public LegacyPacketCondition() {
                        super(0, 0, null, "", 0, 0);
                    }

                    @Override
                    public boolean saveData(ClientMessage packet) {
                        savedValue = packet.readInt();
                        return savedValue == 91;
                    }
                }
                """, StandardCharsets.UTF_8);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Legacy compatibility fixture requires a full JDK");
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int result = compiler.run(
                null,
                diagnostics,
                diagnostics,
                "-classpath",
                System.getProperty("java.class.path"),
                "-d",
                classes.toString(),
                baseSource.toString(),
                conditionSource.toString());
        assertEquals(0, result, diagnostics.toString(StandardCharsets.UTF_8));
        return classes;
    }

    private static final class ModernCondition extends InteractionWiredCondition {
        private int savedValue;
        private boolean legacyCalled;

        private ModernCondition() {
            super(0, 0, null, "", 0, 0);
        }

        @Override
        public boolean saveData(WiredSettings settings) {
            this.savedValue = settings.getIntParams()[0];
            return true;
        }

        @Override
        public boolean saveData(ClientMessage packet) {
            this.legacyCalled = true;
            return false;
        }

        @Override
        public WiredConditionType getType() {
            return WiredConditionType.USER_COUNT;
        }

        @Override
        public boolean evaluate(WiredContext ctx) {
            return false;
        }

        @Override
        public boolean execute(RoomUnit roomUnit, Room room, Object[] stuff) {
            return false;
        }

        @Override
        public String getWiredData() {
            return "";
        }

        @Override
        public void serializeWiredData(ServerMessage message, Room room) {}

        @Override
        public void loadWiredData(ResultSet set, Room room) {}

        @Override
        public void onPickUp() {}
    }
}
