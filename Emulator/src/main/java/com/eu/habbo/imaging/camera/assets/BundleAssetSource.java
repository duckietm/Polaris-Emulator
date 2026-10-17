package com.eu.habbo.imaging.camera.assets;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds camera sprite names in the hotel's Octane/Nitro bundles, the same libraries the client
 * drew them from:
 *
 * <ul>
 *   <li>room planes, masks and textures ({@code wall_texture_64_...}, {@code door_64}) in the
 *       {@code room} library, a {@code room.nitro} file or the client's {@code room} asset folder;
 *   <li>avatar parts ({@code h_std_hd_1_2_0}: size, action, part type, part id, direction, frame)
 *       in the figure library that {@code FigureMap.json} lists for that part type and id, and
 *       effect parts ({@code h_std_fx29_...}) in the library {@code EffectMap.json} lists for the
 *       effect;
 *   <li>furniture ({@code <classname>_<size>_<layer>_<direction>_<frame>}) in {@code
 *       <classname>.nitro}, found by trying the name's underscore prefixes longest first; a colour
 *       variant ({@code name*3}) uses its base library and asset names;
 *   <li>pets the same way in the pet folder.
 * </ul>
 *
 * Libraries are loaded on first use and kept in a cache bounded by decoded bytes; sliced images
 * get a second bounded cache. Nothing is loaded at boot.
 */
public final class BundleAssetSource implements CameraAssetSource {
    private static final Logger LOGGER = LoggerFactory.getLogger(BundleAssetSource.class);
    private static final Pattern AVATAR_PART = Pattern.compile("^(?:h|sh)_[a-z]+_([a-z]{1,5})_(\\d+)_\\d+_\\d+$");
    private static final Pattern AVATAR_EFFECT = Pattern.compile("^(?:h|sh)_[a-z]+_fx(\\d+)_.*$");
    private static final int MAX_PREFIX_TOKENS = 12;
    private static final int MAX_FILE_CHECKS = 8192;

    /** The folders and files this source reads; any may be null. */
    public record Roots(
            Path furnitureDir,
            Path figureDir,
            Path figureMap,
            Path effectDir,
            Path effectMap,
            Path petDir,
            Path roomLibrary) {}

    private final Roots roots;
    private final NitroLibraryLoader loader;
    private final WeightedLruCache<Path, Optional<NitroLibrary>> libraries;
    private final WeightedLruCache<String, Optional<BufferedImage>> images;
    private final Map<Path, Boolean> fileChecks = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, Boolean> eldest) {
            return this.size() > MAX_FILE_CHECKS;
        }
    };
    private final Object loadLock = new Object();
    private volatile FigureLibraryMap figureLibraries;

    public BundleAssetSource(Roots roots, NitroLibraryLoader loader, long libraryCacheBytes, long imageCacheBytes) {
        this.roots = new Roots(
                normalize(roots.furnitureDir()),
                normalize(roots.figureDir()),
                normalize(roots.figureMap()),
                normalize(roots.effectDir()),
                normalize(roots.effectMap()),
                normalize(roots.petDir()),
                normalize(roots.roomLibrary()));
        this.loader = loader;
        this.libraries = new WeightedLruCache<>(
                libraryCacheBytes, library -> library.map(NitroLibrary::weight).orElse(64L));
        this.images = new WeightedLruCache<>(
                imageCacheBytes,
                image -> image.map(i -> (long) i.getWidth() * i.getHeight() * 4L + 64L)
                        .orElse(64L));
    }

    private static Path normalize(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    @Override
    public BufferedImage image(String requestedName) {
        String assetName = withoutColourIndex(requestedName);
        if (!NitroLibraryLoader.isSafeName(assetName)) {
            return null;
        }
        Optional<BufferedImage> cached = this.images.get(assetName);
        if (cached != null) {
            return cached.orElse(null);
        }
        NitroLibrary library = this.libraryFor(assetName);
        BufferedImage image = library == null ? null : library.image(assetName);
        this.images.put(assetName, Optional.ofNullable(image));
        return image;
    }

    @Override
    public int[] palette(String requestedName, String paletteSource) {
        String assetName = withoutColourIndex(requestedName);
        if (!NitroLibraryLoader.isSafeName(assetName)) {
            return null;
        }
        NitroLibrary library = this.libraryFor(assetName);
        NitroLibrary.Palette palette = library == null ? null : library.palette(paletteSource);
        return palette == null ? null : palette.rgb();
    }

    @Override
    public boolean isConfigured() {
        return isDirectory(this.roots.furnitureDir())
                || isDirectory(this.roots.figureDir())
                || isDirectory(this.roots.petDir())
                || (this.roots.roomLibrary() != null && Files.exists(this.roots.roomLibrary()));
    }

    @Override
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (isDirectory(this.roots.furnitureDir())) parts.add("furniture " + this.roots.furnitureDir());
        if (isDirectory(this.roots.figureDir())) parts.add("figure " + this.roots.figureDir());
        if (isDirectory(this.roots.effectDir())) parts.add("effects " + this.roots.effectDir());
        if (isDirectory(this.roots.petDir())) parts.add("pets " + this.roots.petDir());
        if (this.roots.roomLibrary() != null && Files.exists(this.roots.roomLibrary())) {
            parts.add("room " + this.roots.roomLibrary());
        }
        return "bundles: " + String.join(", ", parts);
    }

    /** The library a sprite name belongs to, or null. */
    NitroLibrary libraryFor(String assetName) {
        if (this.roots.roomLibrary() != null) {
            NitroLibrary room = this.library(this.roots.roomLibrary());
            if (room != null && room.hasAsset(assetName)) {
                return room;
            }
        }

        Matcher effect = AVATAR_EFFECT.matcher(assetName);
        if (effect.matches() && this.roots.effectDir() != null) {
            NitroLibrary library = this.firstHolding(
                    this.roots.effectDir(), this.figureLibraries().librariesForEffect(effect.group(1)), assetName);
            if (library != null) {
                return library;
            }
        }

        Matcher part = AVATAR_PART.matcher(assetName);
        if (part.matches() && this.roots.figureDir() != null) {
            NitroLibrary library = this.firstHolding(
                    this.roots.figureDir(),
                    this.figureLibraries().librariesForPart(part.group(1), part.group(2)),
                    assetName);
            if (library != null) {
                return library;
            }
        }

        NitroLibrary furniture = this.byPrefix(this.roots.furnitureDir(), assetName);
        if (furniture != null) {
            return furniture;
        }
        return this.byPrefix(this.roots.petDir(), assetName);
    }

    private NitroLibrary firstHolding(Path dir, List<String> libraryNames, String assetName) {
        for (String libraryName : libraryNames) {
            if (!NitroLibraryLoader.isSafeName(libraryName)) {
                continue;
            }
            NitroLibrary library = this.libraryFile(dir, libraryName);
            if (library != null && library.hasAsset(assetName)) {
                return library;
            }
        }
        return null;
    }

    private NitroLibrary byPrefix(Path dir, String assetName) {
        if (!isDirectory(dir)) {
            return null;
        }
        String base = assetName;
        String[] tokens = base.split("_");
        int longest = Math.min(tokens.length - 1, MAX_PREFIX_TOKENS);
        for (int count = longest; count >= 1; count--) {
            String prefix = String.join("_", java.util.Arrays.copyOfRange(tokens, 0, count));
            NitroLibrary library = this.libraryFile(dir, prefix);
            if (library != null && library.hasAsset(assetName)) {
                return library;
            }
        }
        // A sprite named exactly after its library (no suffix), e.g. some wall items.
        NitroLibrary whole = this.libraryFile(dir, base);
        return whole != null && whole.hasAsset(assetName) ? whole : null;
    }

    private NitroLibrary libraryFile(Path dir, String libraryName) {
        Path file = dir.resolve(libraryName + ".nitro").normalize();
        if (!file.startsWith(dir) || !this.exists(file)) {
            return null;
        }
        return this.library(file);
    }

    private boolean exists(Path file) {
        synchronized (this.fileChecks) {
            Boolean known = this.fileChecks.get(file);
            if (known != null) {
                return known;
            }
        }
        boolean exists = Files.isRegularFile(file);
        synchronized (this.fileChecks) {
            this.fileChecks.put(file, exists);
        }
        return exists;
    }

    private NitroLibrary library(Path path) {
        Optional<NitroLibrary> cached = this.libraries.get(path);
        if (cached != null) {
            return cached.orElse(null);
        }
        synchronized (this.loadLock) {
            cached = this.libraries.get(path);
            if (cached != null) {
                return cached.orElse(null);
            }
            NitroLibrary library = null;
            try {
                library = this.loader.load(path);
            } catch (IOException | RuntimeException e) {
                LOGGER.warn("Camera renderer could not read asset library {}: {}", path, e.getMessage());
            }
            this.libraries.put(path, Optional.ofNullable(library));
            return library;
        }
    }

    private FigureLibraryMap figureLibraries() {
        FigureLibraryMap map = this.figureLibraries;
        if (map != null) {
            return map;
        }
        synchronized (this.loadLock) {
            if (this.figureLibraries == null) {
                FigureLibraryMap loaded = FigureLibraryMap.empty();
                try {
                    loaded = FigureLibraryMap.load(this.roots.figureMap(), this.roots.effectMap(), 32L * 1024 * 1024);
                } catch (IOException | RuntimeException e) {
                    LOGGER.warn("Camera renderer could not read the figure/effect map: {}", e.getMessage());
                }
                this.figureLibraries = loaded;
            }
            return this.figureLibraries;
        }
    }

    /** {@code name*3_64_a_0_0} or {@code name*3}: a colour variant draws from its base library and assets. */
    static String withoutColourIndex(String name) {
        if (name == null) {
            return null;
        }
        int star = name.indexOf('*');
        if (star < 0) {
            return name;
        }
        int end = star + 1;
        while (end < name.length() && Character.isDigit(name.charAt(end))) {
            end++;
        }
        return name.substring(0, star) + name.substring(end);
    }

    private static boolean isDirectory(Path path) {
        return path != null && Files.isDirectory(path);
    }
}
