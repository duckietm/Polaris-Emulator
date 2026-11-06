package polaris.plugin.remap;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.security.CodeSigner;
import java.security.CodeSource;
import java.util.Objects;

/**
 * Loads one plugin jar and rewrites each of its classes with {@link LegacyRemapper} before defining
 * it, so a jar compiled against the old emulator names links against the current ones. A jar
 * compiled against the current names passes through unchanged.
 *
 * <p>A plugin class that itself lives under an old emulator package is defined under its new name;
 * asking for it by either name returns the same class.
 */
public final class RemappingPluginClassLoader extends URLClassLoader {

    static {
        ClassLoader.registerAsParallelCapable();
    }

    private final LegacyNames names;
    private final LegacyRemapper remapper;
    private final CodeSource codeSource;

    public RemappingPluginClassLoader(URL jar, ClassLoader parent, LegacyNames names) {
        super(new URL[] {Objects.requireNonNull(jar, "jar")}, parent);
        this.names = Objects.requireNonNull(names, "names");
        this.remapper = new LegacyRemapper(names, this::classBytes);
        this.codeSource = new CodeSource(jar, (CodeSigner[]) null);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        String internal = name.replace('.', '/');
        String mapped = this.names.mapType(internal);
        if (!mapped.equals(internal)) {
            return this.loadClass(mapped.replace('/', '.'));
        }

        byte[] original = this.ownClassBytes(internal);
        if (original == null) {
            String old = this.names.unmapType(internal);
            original = old == null ? null : this.ownClassBytes(old);
        }
        if (original == null) {
            throw new ClassNotFoundException(name);
        }

        byte[] remapped;
        try {
            remapped = this.remapper.remap(original);
        } catch (RuntimeException e) {
            throw new ClassNotFoundException(name, e);
        }

        int lastDot = name.lastIndexOf('.');
        if (lastDot > 0) {
            String packageName = name.substring(0, lastDot);
            if (this.getDefinedPackage(packageName) == null) {
                this.definePackage(packageName, null, null, null, null, null, null, null);
            }
        }
        return this.defineClass(name, remapped, 0, remapped.length, this.codeSource);
    }

    /** The class file of a plugin class, or of the emulator class an old or current name stands for. */
    private byte[] classBytes(String internalName) {
        byte[] own = this.ownClassBytes(internalName);
        if (own != null || this.getParent() == null) {
            return own;
        }
        try (InputStream input = this.getParent().getResourceAsStream(this.names.mapType(internalName) + ".class")) {
            return input == null ? null : input.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    /** The class file of an internal name inside this plugin's jar, or {@code null}. */
    private byte[] ownClassBytes(String internalName) {
        URL resource = this.findResource(internalName + ".class");
        if (resource == null) {
            return null;
        }
        try {
            // Without caches: a cached JarFile is shared JVM-wide and keeps the jar locked after close().
            URLConnection connection = resource.openConnection();
            connection.setUseCaches(false);
            try (InputStream input = connection.getInputStream()) {
                return input.readAllBytes();
            }
        } catch (IOException e) {
            return null;
        }
    }
}
