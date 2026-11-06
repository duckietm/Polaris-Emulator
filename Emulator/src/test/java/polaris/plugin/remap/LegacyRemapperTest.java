package polaris.plugin.remap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class LegacyRemapperTest {

    private static final LegacyNames NAMES = LegacyNames.parse(List.of(
            "# a small table in the shipped format",
            "package test/oldpkg/ test/newpkg/",
            "package test/oldpkg/deep/ test/newpkg/shallow/",
            "type test/oldpkg/OldUser test/newpkg/User",
            "method getOld getNew",
            "field test/newpkg/Constants OLD_HEADER NEW_HEADER",
            "field test/newpkg/Base habbo user"));

    @Test
    void typesFollowTheLongestPackagePrefixAndExplicitRenames() {
        assertEquals("test/newpkg/User", NAMES.mapType("test/oldpkg/OldUser"));
        assertEquals("test/newpkg/User$Inner", NAMES.mapType("test/oldpkg/OldUser$Inner"));
        assertEquals("test/newpkg/shallow/Type", NAMES.mapType("test/oldpkg/deep/Type"));
        assertEquals("test/newpkg/Other", NAMES.mapType("test/oldpkg/Other"));
        assertEquals("test/oldpkgx/Other", NAMES.mapType("test/oldpkgx/Other"));
        assertEquals("java/lang/String", NAMES.mapType("java/lang/String"));

        assertEquals("test/oldpkg/OldUser", NAMES.unmapType("test/newpkg/User"));
        assertEquals("test/oldpkg/deep/Type", NAMES.unmapType("test/newpkg/shallow/Type"));
        assertNull(NAMES.unmapType("other/Type"));

        assertEquals("getNew", NAMES.renamedMethod("getOld"));
        assertNull(NAMES.renamedMethod("getOther"));
        assertEquals("NEW_HEADER", NAMES.mapField("test/newpkg/Constants", "OLD_HEADER"));
        assertEquals("OTHER", NAMES.mapField("test/newpkg/Constants", "OTHER"));
    }

    @Test
    void rejectsUnknownEntries() {
        assertThrows(IllegalArgumentException.class, () -> LegacyNames.parse(List.of("rename a b")));
    }

    @Test
    void theShippedTableCoversThePluginBaseClassAndTheUserType() {
        LegacyNames shipped = LegacyNames.standard();
        assertEquals("polaris/plugin/PolarisPlugin", shipped.mapType("com/eu/habbo/plugin/HabboPlugin"));
        assertEquals("polaris/hotel/users/User", shipped.mapType("com/eu/habbo/habbohotel/users/Habbo"));
        assertEquals("polaris/Emulator", shipped.mapType("com/eu/habbo/Emulator"));
        assertEquals("getUser", shipped.renamedMethod("getHabbo"));
    }

    @Test
    void oldPluginLinksToRenamedTypesMethodsAndReflection(@TempDir Path temp) throws Exception {
        ClassLoader parent = emulator();
        Path jar = jar(
                temp,
                Map.of(
                        "fixture/Caller", caller(),
                        "fixture/Sub", sub(),
                        "fixture/Plain", plain()));

        try (RemappingPluginClassLoader loader =
                new RemappingPluginClassLoader(jar.toUri().toURL(), parent, NAMES)) {
            Class<?> caller = loader.loadClass("fixture.Caller");
            assertSame(loader, caller.getClassLoader());

            assertEquals("moved", caller.getMethod("call").invoke(null));
            assertSame(
                    parent.loadClass("test.newpkg.User"),
                    caller.getMethod("reflect").invoke(null));
            assertEquals("getNew", caller.getMethod("methodName").invoke(null));
            assertEquals("field", caller.getMethod("inheritedField").invoke(null), "field read through a subclass");

            Class<?> sub = loader.loadClass("fixture.Sub");
            assertEquals("sub", sub.getMethod("viaBase").invoke(null), "override reached through the base type");
            assertEquals(
                    "sub", sub.getMethod("getNew").invoke(sub.getConstructor().newInstance()));

            Class<?> plain = loader.loadClass("fixture.Plain");
            assertEquals("plain", plain.getMethod("call").invoke(null));
            assertTrue(plain.getMethod("getOld") != null, "an unrelated plugin method keeps its name");
        }
    }

    @Test
    void pluginClassUnderAnOldPackageIsDefinedOnceUnderItsNewName(@TempDir Path temp) throws Exception {
        Path jar = jar(temp, Map.of("test/oldpkg/plugin/Main", plain("test/oldpkg/plugin/Main")));

        try (RemappingPluginClassLoader loader =
                new RemappingPluginClassLoader(jar.toUri().toURL(), emulator(), NAMES)) {
            Class<?> byOldName = loader.loadClass("test.oldpkg.plugin.Main");
            assertSame(byOldName, loader.loadClass("test.newpkg.plugin.Main"));
            assertEquals("test.newpkg.plugin.Main", byOldName.getName());
            assertThrows(ClassNotFoundException.class, () -> loader.loadClass("fixture.Missing"));
        }
    }

    /**
     * The emulator side, already renamed: {@code User.value()}, {@code Base.getNew()}, the field
     * {@code Base.user} and {@code SubEvent extends Base}. Class files are also served as resources,
     * as the emulator jar serves them.
     */
    private static ClassLoader emulator() {
        Map<String, byte[]> classes = Map.of(
                "test.newpkg.User", user(),
                "test.newpkg.Base", base(),
                "test.newpkg.SubEvent", subEvent());
        return new ClassLoader(LegacyRemapperTest.class.getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null) {
                    throw new ClassNotFoundException(name);
                }
                return this.defineClass(name, bytes, 0, bytes.length);
            }

            @Override
            public InputStream getResourceAsStream(String name) {
                byte[] bytes = name.endsWith(".class")
                        ? classes.get(name.substring(0, name.length() - 6).replace('/', '.'))
                        : null;
                return bytes == null ? super.getResourceAsStream(name) : new ByteArrayInputStream(bytes);
            }
        };
    }

    private static byte[] subEvent() {
        ClassWriter writer = open("test/newpkg/SubEvent", "test/newpkg/Base");
        constructor(writer, "test/newpkg/Base");
        return close(writer);
    }

    private static byte[] user() {
        ClassWriter writer = open("test/newpkg/User", "java/lang/Object");
        returning(writer, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "value", "moved");
        return close(writer);
    }

    private static byte[] base() {
        ClassWriter writer = open("test/newpkg/Base", "java/lang/Object");
        writer.visitField(
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                        "user",
                        "Ljava/lang/String;",
                        null,
                        "field")
                .visitEnd();
        constructor(writer, "java/lang/Object");
        returning(writer, Opcodes.ACC_PUBLIC, "getNew", "base");
        return close(writer);
    }

    /** Compiled against the old names: static calls, Class.forName and a method name for getMethod. */
    private static byte[] caller() {
        ClassWriter writer = open("fixture/Caller", "java/lang/Object");
        MethodVisitor call =
                writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()Ljava/lang/String;", null, null);
        call.visitCode();
        call.visitMethodInsn(Opcodes.INVOKESTATIC, "test/oldpkg/OldUser", "value", "()Ljava/lang/String;", false);
        call.visitInsn(Opcodes.ARETURN);
        call.visitMaxs(0, 0);
        call.visitEnd();

        MethodVisitor reflect = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "reflect", "()Ljava/lang/Class;", null, new String[] {
                    "java/lang/ClassNotFoundException"
                });
        reflect.visitCode();
        reflect.visitLdcInsn("test.oldpkg.OldUser");
        reflect.visitMethodInsn(
                Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        reflect.visitInsn(Opcodes.ARETURN);
        reflect.visitMaxs(0, 0);
        reflect.visitEnd();

        returning(writer, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "methodName", "getOld");

        // The field is declared by Base under its old name, but read through the old SubEvent.
        MethodVisitor inherited = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "inheritedField", "()Ljava/lang/String;", null, null);
        inherited.visitCode();
        inherited.visitFieldInsn(Opcodes.GETSTATIC, "test/oldpkg/SubEvent", "habbo", "Ljava/lang/String;");
        inherited.visitInsn(Opcodes.ARETURN);
        inherited.visitMaxs(0, 0);
        inherited.visitEnd();
        return close(writer);
    }

    /** Extends the old {@code Base}, overrides {@code getOld()} and calls it through the base type. */
    private static byte[] sub() {
        ClassWriter writer = open("fixture/Sub", "test/oldpkg/Base");
        constructor(writer, "test/oldpkg/Base");
        returning(writer, Opcodes.ACC_PUBLIC, "getOld", "sub");

        MethodVisitor viaBase = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "viaBase", "()Ljava/lang/String;", null, null);
        viaBase.visitCode();
        viaBase.visitTypeInsn(Opcodes.NEW, "fixture/Sub");
        viaBase.visitInsn(Opcodes.DUP);
        viaBase.visitMethodInsn(Opcodes.INVOKESPECIAL, "fixture/Sub", "<init>", "()V", false);
        viaBase.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "test/oldpkg/Base", "getOld", "()Ljava/lang/String;", false);
        viaBase.visitInsn(Opcodes.ARETURN);
        viaBase.visitMaxs(0, 0);
        viaBase.visitEnd();
        return close(writer);
    }

    private static byte[] plain() {
        return plain("fixture/Plain");
    }

    /** No emulator supertype: its own {@code getOld()} is not the emulator's and keeps its name. */
    private static byte[] plain(String name) {
        ClassWriter writer = open(name, "java/lang/Object");
        constructor(writer, "java/lang/Object");
        returning(writer, Opcodes.ACC_PUBLIC, "getOld", "plain");

        MethodVisitor call =
                writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "call", "()Ljava/lang/String;", null, null);
        call.visitCode();
        call.visitTypeInsn(Opcodes.NEW, name);
        call.visitInsn(Opcodes.DUP);
        call.visitMethodInsn(Opcodes.INVOKESPECIAL, name, "<init>", "()V", false);
        call.visitMethodInsn(Opcodes.INVOKEVIRTUAL, name, "getOld", "()Ljava/lang/String;", false);
        call.visitInsn(Opcodes.ARETURN);
        call.visitMaxs(0, 0);
        call.visitEnd();
        return close(writer);
    }

    private static ClassWriter open(String name, String superName) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, superName, null);
        return writer;
    }

    private static void constructor(ClassWriter writer, String superName) {
        MethodVisitor init = writer.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, superName, "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
    }

    private static void returning(ClassWriter writer, int access, String method, String value) {
        MethodVisitor visitor = writer.visitMethod(access, method, "()Ljava/lang/String;", null, null);
        visitor.visitCode();
        visitor.visitLdcInsn(value);
        visitor.visitInsn(Opcodes.ARETURN);
        visitor.visitMaxs(0, 0);
        visitor.visitEnd();
    }

    private static byte[] close(ClassWriter writer) {
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static Path jar(Path directory, Map<String, byte[]> classes) throws IOException {
        Path jar = directory.resolve("plugin.jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey() + ".class"));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return jar;
    }
}
