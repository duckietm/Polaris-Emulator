package polaris.plugin.remap;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

/**
 * Rewrites a class compiled against the old emulator names so it links against the current ones.
 *
 * <p>Types, descriptors, signatures and {@code Class} constants follow {@link LegacyNames}. A
 * renamed method is renamed only on classes that are emulator classes or extend or implement one,
 * found by walking the hierarchy through {@code classBytes}: a plugin's own unrelated method with
 * the same name keeps it. String constants that are exactly an old class name or a renamed method
 * name are rewritten, so plain reflection ({@code Class.forName}, {@code getMethod("getHabbo")})
 * keeps resolving.
 */
public final class LegacyRemapper extends Remapper {

    private final LegacyNames names;
    private final Function<String, byte[]> classBytes;
    private final Map<String, Boolean> linksToEmulator = new ConcurrentHashMap<>();
    private final Map<String, String> superNames = new ConcurrentHashMap<>();

    /**
     * @param classBytes the class file of an internal name, old or current, or {@code null} when it
     *     is not available (JDK and library classes); used to walk class hierarchies
     */
    public LegacyRemapper(LegacyNames names, Function<String, byte[]> classBytes) {
        super(Opcodes.ASM9);
        this.names = Objects.requireNonNull(names, "names");
        this.classBytes = Objects.requireNonNull(classBytes, "classBytes");
    }

    @Override
    public String map(String internalName) {
        return this.names.mapType(internalName);
    }

    @Override
    public String mapMethodName(String owner, String name, String descriptor) {
        String renamed = this.names.renamedMethod(name);
        return renamed != null && this.linksToEmulator(owner) ? renamed : name;
    }

    /**
     * A field is renamed on the class that declares it, but bytecode names the class it was read
     * through ({@code UserLoginEvent.habbo} for a field of {@code UserEvent}), so the superclasses are
     * walked until a renamed declaration is found.
     */
    @Override
    public String mapFieldName(String owner, String name, String descriptor) {
        if (!this.names.isRenamedField(name)) {
            return name;
        }
        for (String type = owner; type != null; type = this.superName(type)) {
            String renamed = this.names.mapField(this.map(type), name);
            if (!renamed.equals(name)) {
                return renamed;
            }
        }
        return name;
    }

    @Override
    public Object mapValue(Object value) {
        if (value instanceof String text) {
            return this.mapString(text);
        }
        return super.mapValue(value);
    }

    public byte[] remap(byte[] classFile) {
        ClassReader reader = new ClassReader(classFile);
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassRemapper(writer, this), 0);
        return writer.toByteArray();
    }

    private String mapString(String text) {
        String method = this.names.renamedMethod(text);
        if (method != null) {
            return method;
        }
        if (text.indexOf('/') < 0 && text.indexOf('.') > 0) {
            String internal = text.replace('.', '/');
            String mapped = this.names.mapType(internal);
            return mapped.equals(internal) ? text : mapped.replace('/', '.');
        }
        return this.names.mapType(text);
    }

    /** The superclass of a type, or {@code null} at the top or when its class file is unavailable. */
    private String superName(String type) {
        if (type.startsWith("[") || type.startsWith("java/")) {
            return null;
        }
        String known = this.superNames.get(type);
        if (known != null) {
            return known.isEmpty() ? null : known;
        }
        byte[] bytes = this.classBytes.apply(type);
        String superName = bytes == null ? null : new ClassReader(bytes).getSuperName();
        this.superNames.put(type, superName == null ? "" : superName);
        return superName;
    }

    private boolean linksToEmulator(String owner) {
        if (owner.startsWith("[")) {
            return false;
        }
        if (this.names.isEmulatorType(owner)) {
            return true;
        }
        if (owner.startsWith("java/") || owner.startsWith("javax/") || owner.startsWith("jdk/")) {
            return false;
        }
        Boolean known = this.linksToEmulator.get(owner);
        if (known != null) {
            return known;
        }
        this.linksToEmulator.put(owner, Boolean.FALSE);
        boolean linked = false;
        byte[] bytes = this.classBytes.apply(owner);
        if (bytes != null) {
            ClassReader reader = new ClassReader(bytes);
            String superName = reader.getSuperName();
            linked = superName != null && this.linksToEmulator(superName);
            for (String implemented : reader.getInterfaces()) {
                linked = linked || this.linksToEmulator(implemented);
            }
        }
        this.linksToEmulator.put(owner, linked);
        return linked;
    }
}
