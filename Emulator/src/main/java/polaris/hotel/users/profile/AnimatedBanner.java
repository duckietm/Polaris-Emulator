package polaris.hotel.users.profile;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.UnaryOperator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import org.w3c.dom.Node;
import polaris.hotel.users.profile.ProfileImageService.ProfileImageException;

/**
 * An animated GIF banner. The client places the picture in the editor (scale and centre in the 1280 x 220
 * frame); the server decodes every frame, composes it as the GIF says (offsets and disposal), places it the
 * same way at half size and writes a new looping GIF, so only the pixels of the original reach the disk.
 */
public final class AnimatedBanner {
    /** Half the still banner: an animation at full size would not fit the upload limit. */
    public static final int WIDTH = 640;

    public static final int HEIGHT = 110;
    public static final int MAX_FRAMES = 120;
    public static final int MAX_SOURCE_SIDE = 2048;
    /** The written GIF may grow past the upload, but not without limit. */
    public static final int MAX_OUTPUT_BYTES = 1_500_000;

    private static final String GIF_METADATA = "javax_imageio_gif_image_1.0";

    /**
     * A full-size canvas is up to 16 MB: at most two GIFs are composed at once, so a burst of uploads cannot
     * take the server's memory. The others are refused and may try again.
     */
    private static final Semaphore RENDERING = new Semaphore(2);

    /** Where the picture sits in the 1280 x 220 editor frame. */
    public record Placement(double scale, double centerX, double centerY, boolean pixelated) {}

    record Frame(BufferedImage image, int delayCentiseconds) {}

    private AnimatedBanner() {}

    public static boolean isGif(byte[] bytes) {
        return bytes != null && bytes.length > 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F';
    }

    /** Decodes, places and re-encodes a GIF banner. */
    public static byte[] render(byte[] gif, Placement placement) throws ProfileImageException {
        if (placement == null
                || !(placement.scale() > 0)
                || Double.isNaN(placement.centerX())
                || Double.isNaN(placement.centerY())) {
            throw new ProfileImageException("invalid", "The banner has no placement.");
        }

        if (!RENDERING.tryAcquire()) {
            throw new ProfileImageException("rate_limited", "Other banners are being prepared, try again.");
        }

        byte[] encoded;
        try {
            // Each frame is placed as soon as it is composed: only the small placed copies are kept.
            List<Frame> frames = decode(gif, image -> place(image, placement));
            encoded = encode(
                    frames.stream().map(Frame::image).toList(),
                    frames.stream().map(Frame::delayCentiseconds).toList());
        } catch (IOException e) {
            throw new ProfileImageException("failed", "The animation could not be written.");
        } finally {
            RENDERING.release();
        }
        if (encoded.length > MAX_OUTPUT_BYTES) {
            throw new ProfileImageException("too_large", "The animation is too large once placed.");
        }
        return encoded;
    }

    /** Every frame as the full logical screen, composed as the GIF's offsets and disposal methods say. */
    static List<Frame> decode(byte[] gif) throws ProfileImageException {
        return decode(gif, AnimatedBanner::copy);
    }

    /** Every composed frame handed to {@code keep}, which returns what is kept of it. */
    static List<Frame> decode(byte[] gif, UnaryOperator<BufferedImage> keep) throws ProfileImageException {
        if (!isGif(gif)) throw new ProfileImageException("invalid", "This is not a GIF.");

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(gif))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (input == null || !readers.hasNext()) throw new ProfileImageException("invalid", "GIF cannot be read.");

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false);
                int count = reader.getNumImages(true);
                if (count < 1) throw new ProfileImageException("invalid", "The GIF has no frames.");
                if (count > MAX_FRAMES) {
                    throw new ProfileImageException("too_large", "The GIF has more than " + MAX_FRAMES + " frames.");
                }

                int width = Math.max(1, reader.getWidth(0));
                int height = Math.max(1, reader.getHeight(0));
                Node screen = reader.getStreamMetadata() == null
                        ? null
                        : child(
                                reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"),
                                "LogicalScreenDescriptor");
                if (screen != null) {
                    width = Math.max(width, attribute(screen, "logicalScreenWidth", width));
                    height = Math.max(height, attribute(screen, "logicalScreenHeight", height));
                }
                if (width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE) {
                    throw new ProfileImageException(
                            "too_large", "The GIF is larger than " + MAX_SOURCE_SIDE + " pixels.");
                }

                BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                List<Frame> frames = new ArrayList<>(count);
                for (int index = 0; index < count; index++) {
                    // A frame declares its own size: checked before its pixels are allocated.
                    if (reader.getWidth(index) > MAX_SOURCE_SIDE || reader.getHeight(index) > MAX_SOURCE_SIDE) {
                        throw new ProfileImageException(
                                "too_large", "The GIF is larger than " + MAX_SOURCE_SIDE + " pixels.");
                    }
                    BufferedImage part = reader.read(index);
                    Node meta = reader.getImageMetadata(index).getAsTree(GIF_METADATA);
                    Node descriptor = child(meta, "ImageDescriptor");
                    Node control = child(meta, "GraphicControlExtension");
                    int left = descriptor == null ? 0 : attribute(descriptor, "imageLeftPosition", 0);
                    int top = descriptor == null ? 0 : attribute(descriptor, "imageTopPosition", 0);
                    int delay = control == null ? 10 : attribute(control, "delayTime", 10);
                    String disposal = control == null ? "none" : text(control, "disposalMethod", "none");

                    BufferedImage before = disposal.equals("restoreToPrevious") ? copy(canvas) : null;
                    Graphics2D graphics = canvas.createGraphics();
                    try {
                        graphics.drawImage(part, left, top, null);
                    } finally {
                        graphics.dispose();
                    }
                    // Browsers play frames shorter than 2 centiseconds at 10: do the same, so it looks as it did.
                    frames.add(new Frame(keep.apply(canvas), delay < 2 ? 10 : delay));

                    if (disposal.equals("restoreToBackgroundColor")) {
                        Graphics2D clear = canvas.createGraphics();
                        try {
                            clear.setComposite(AlphaComposite.Clear);
                            clear.fillRect(left, top, part.getWidth(), part.getHeight());
                        } finally {
                            clear.dispose();
                        }
                    } else if (before != null) {
                        canvas = before;
                    }
                }
                return frames;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw new ProfileImageException("invalid", "The GIF cannot be read.");
        }
    }

    /** One frame placed in the half-size banner, over a darkened covering copy where it leaves the strip empty. */
    static BufferedImage place(BufferedImage source, Placement placement) {
        double half = 0.5;
        double scale = placement.scale() * half;
        double width = source.getWidth() * scale;
        double height = source.getHeight() * scale;
        double x = placement.centerX() * half - width / 2;
        double y = placement.centerY() * half - height / 2;

        BufferedImage out = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = out.createGraphics();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    placement.pixelated()
                            ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                            : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (x > 0 || y > 0 || x + width < WIDTH || y + height < HEIGHT) {
                double cover = Math.max((double) WIDTH / source.getWidth(), (double) HEIGHT / source.getHeight());
                int coverWidth = (int) Math.ceil(source.getWidth() * cover);
                int coverHeight = (int) Math.ceil(source.getHeight() * cover);
                graphics.drawImage(
                        source, (WIDTH - coverWidth) / 2, (HEIGHT - coverHeight) / 2, coverWidth, coverHeight, null);
                graphics.setColor(new Color(0, 0, 0, 110));
                graphics.fillRect(0, 0, WIDTH, HEIGHT);
            }
            graphics.drawImage(
                    source,
                    (int) Math.round(x),
                    (int) Math.round(y),
                    (int) Math.round(width),
                    (int) Math.round(height),
                    null);
        } finally {
            graphics.dispose();
        }
        return out;
    }

    /** A looping GIF; ImageIO builds a palette for each frame. */
    static byte[] encode(List<BufferedImage> frames, List<Integer> delays) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            ImageWriteParam param = writer.getDefaultWriteParam();
            for (int index = 0; index < frames.size(); index++) {
                BufferedImage frame = frames.get(index);
                IIOMetadata metadata =
                        writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(frame), param);
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(GIF_METADATA);

                IIOMetadataNode control = node(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", "none");
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", Integer.toString(delays.get(index)));
                control.setAttribute("transparentColorIndex", "0");

                if (index == 0) {
                    IIOMetadataNode extensions = node(root, "ApplicationExtensions");
                    IIOMetadataNode loop = new IIOMetadataNode("ApplicationExtension");
                    loop.setAttribute("applicationID", "NETSCAPE");
                    loop.setAttribute("authenticationCode", "2.0");
                    loop.setUserObject(new byte[] {0x1, 0, 0});
                    extensions.appendChild(loop);
                }

                metadata.setFromTree(GIF_METADATA, root);
                writer.writeToSequence(new IIOImage(frame, null, metadata), param);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static Node child(Node parent, String name) {
        if (parent == null) return null;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeName().equals(name)) return node;
        }
        return null;
    }

    private static IIOMetadataNode node(IIOMetadataNode root, String name) {
        Node existing = child(root, name);
        if (existing instanceof IIOMetadataNode metadataNode) return metadataNode;
        IIOMetadataNode created = new IIOMetadataNode(name);
        root.appendChild(created);
        return created;
    }

    private static int attribute(Node node, String name, int fallback) {
        Node value = node.getAttributes() == null ? null : node.getAttributes().getNamedItem(name);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.getNodeValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String text(Node node, String name, String fallback) {
        Node value = node.getAttributes() == null ? null : node.getAttributes().getNamedItem(name);
        return value == null ? fallback : value.getNodeValue();
    }
}
