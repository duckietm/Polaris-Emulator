package com.eu.habbo.imaging.camera.render;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

final class CameraPlaneRenderer {
    private final JSONCamera data;
    private final int width;
    private final int height;
    private final CameraImageLoader imageLoader;
    private final String wallPaint;
    private final WallColorResolver wallColorResolver;

    private final BasicStroke simpleStroke = new BasicStroke(0);

    CameraPlaneRenderer(
            JSONCamera data,
            int width,
            int height,
            CameraImageLoader imageLoader,
            String wallPaint,
            WallColorResolver wallColorResolver) {
        this.data = data;
        this.width = width;
        this.height = height;
        this.imageLoader = imageLoader;
        this.wallPaint = wallPaint;
        this.wallColorResolver = wallColorResolver;
    }

    void renderSinglePlane(Graphics2D target, CameraPlane plane) {
        CameraPositionPoint[] points = plane.getCornerPoints();
        if (points == null || points.length == 0) {
            return;
        }
        Color planeColor = new Color(plane.getColor());
        CameraTexCols[] texCols = plane.getTexCols();
        boolean texturedPlane = texCols != null && texCols.length > 0;
        PlaneStyle planeStyle = getPlaneStyle(plane, planeColor);

        Polygon polygon = getPlanePolygon(points);

        boolean renderedTexture = false;
        if (texturedPlane) {
            renderedTexture = renderTexCols(target, polygon, plane, planeStyle);
        }

        if (!renderedTexture) {
            target.scale(1, 1);
            target.setBackground(planeStyle.fillColor());
            target.setStroke(this.simpleStroke);
            target.setColor(planeStyle.fillColor());
            target.fillPolygon(polygon);
            target.scale(1, 1);
        }
    }

    void renderSinglePlaneClippedToMask(
            Graphics2D target,
            CameraPlane plane,
            BufferedImage maskAlpha,
            Rectangle maskBounds,
            int canvasWidth,
            int canvasHeight) {
        BufferedImage planeLayer = renderPlaneLayer(plane, canvasWidth, canvasHeight);
        if (planeLayer == null) {
            return;
        }

        CameraRenderUtils.drawMaskedLayer(target, planeLayer, maskAlpha, maskBounds, canvasWidth, canvasHeight);
    }

    int getRenderBackgroundColor(int fallbackColor) {
        CameraPlane backgroundPlane = getBackgroundPlane();
        if (backgroundPlane != null) {
            return backgroundPlane.getColor();
        }
        return fallbackColor;
    }

    CameraPlane getBackgroundPlane() {
        if (this.data.getPlanes() == null || this.data.getPlanes().length == 0) {
            return null;
        }

        CameraPlane plane = this.data.getPlanes()[0];
        if (plane == null
                || hasTextureAssetWithPrefix(plane, "wall_texture")
                || hasTextureAssetWithPrefix(plane, "floor_texture")) {
            return null;
        }

        CameraPositionPoint[] points = plane.getCornerPoints();
        if (points == null || points.length < 4) {
            return null;
        }

        Polygon polygon = getPlanePolygon(points);
        Rectangle bounds = polygon.getBounds();
        return bounds.x <= 0 && bounds.y <= 0 && bounds.width >= this.width && bounds.height >= this.height
                ? plane
                : null;
    }

    boolean isWallPlane(CameraPlane plane) {
        return hasTextureAssetWithPrefix(plane, "wall_texture");
    }

    boolean isFloorPlane(CameraPlane plane) {
        return hasTextureAssetWithPrefix(plane, "floor_texture");
    }

    boolean isLandscapePlane(CameraPlane plane) {
        return hasTextureAssetWithPrefix(plane, "landscape_");
    }

    /**
     * A layer of a landscape (window view): AIR sends one plane per landscape layer, all with the
     * corner points of the wall they lie on and a depth just behind it (relative depth +0.02). The
     * textured layers name {@code landscape_*} assets; the colour layer has no texture, so it is
     * recognised by lying on a wall, untextured, behind it.
     */
    boolean isLandscapeLayer(CameraPlane plane) {
        if (plane == null || isWallPlane(plane) || isFloorPlane(plane)) {
            return false;
        }
        if (isLandscapePlane(plane)) {
            return true;
        }
        if ((plane.getTexCols() != null && plane.getTexCols().length > 0) || this.data.getPlanes() == null) {
            return false;
        }
        for (CameraPlane wall : this.data.getPlanes()) {
            if (wall != null && wall != plane && isWallPlane(wall) && liesBehind(plane, wall)) {
                return true;
            }
        }
        return false;
    }

    /** The landscape layers lying behind {@code wall}, back to front (the order to draw them). */
    List<CameraPlane> getLandscapeLayersBehind(CameraPlane wall) {
        List<CameraPlane> layers = new ArrayList<>();
        if (wall == null || this.data.getPlanes() == null) {
            return layers;
        }
        for (CameraPlane plane : this.data.getPlanes()) {
            if (plane != null && plane != wall && liesBehind(plane, wall) && isLandscapeLayer(plane)) {
                layers.add(plane);
            }
        }
        // Stable: layers of one landscape share a depth and keep the client's order.
        layers.sort((left, right) -> Float.compare(right.getZ(), left.getZ()));
        return layers;
    }

    private boolean liesBehind(CameraPlane plane, CameraPlane wall) {
        return plane.getZ() > wall.getZ() && sameCorners(plane.getCornerPoints(), wall.getCornerPoints());
    }

    private static boolean sameCorners(CameraPositionPoint[] left, CameraPositionPoint[] right) {
        if (left == null || right == null || left.length < 4 || right.length < 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (left[i] == null || right[i] == null || left[i].x != right[i].x || left[i].y != right[i].y) {
                return false;
            }
        }
        return true;
    }

    boolean hasPlaneMasks(CameraPlane plane) {
        return plane.getMasks() != null && plane.getMasks().length > 0;
    }

    boolean planeIntersects(CameraPlane plane, Rectangle bounds) {
        CameraPositionPoint[] points = plane.getCornerPoints();
        if (points == null || points.length < 4) {
            return false;
        }

        return getPlanePolygon(points).getBounds().intersects(bounds);
    }

    Polygon getPlanePolygon(CameraPositionPoint[] points) {
        if (points == null || points.length < 4) {
            return new Polygon();
        }

        Polygon polygon = new Polygon();
        polygon.addPoint(points[0].x, points[0].y);
        polygon.addPoint(points[1].x, points[1].y);
        polygon.addPoint(points[3].x, points[3].y);
        polygon.addPoint(points[2].x, points[2].y);
        return polygon;
    }

    BufferedImage buildPlaneTexture(CameraPlane plane) {
        PlaneStyle planeStyle = getPlaneStyle(plane, new Color(plane.getColor()));
        return buildPlaneTexture(plane, planeStyle);
    }

    AffineTransform getTextureTransform(CameraPlane plane, int textureWidth, int textureHeight) {
        CameraPositionPoint[] points = plane.getCornerPoints();
        if (points == null || points.length < 4 || textureWidth <= 0 || textureHeight <= 0) {
            return null;
        }

        CameraPositionPoint widthPoint = getTextureWidthPoint(plane, points);
        CameraPositionPoint heightPoint = getTextureHeightPoint(plane, points);
        return new AffineTransform(
                (widthPoint.x - points[0].x) / (double) textureWidth,
                (widthPoint.y - points[0].y) / (double) textureWidth,
                (heightPoint.x - points[0].x) / (double) textureHeight,
                (heightPoint.y - points[0].y) / (double) textureHeight,
                points[0].x,
                points[0].y);
    }

    BufferedImage flipVertical(BufferedImage image) {
        AffineTransform tx = AffineTransform.getScaleInstance(1, -1);
        tx.translate(0, -image.getHeight(null));
        BufferedImage flipped = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = flipped.createGraphics();
        graphics.drawImage(image, tx, null);
        graphics.dispose();
        return flipped;
    }

    private BufferedImage renderPlaneLayer(CameraPlane plane, int canvasWidth, int canvasHeight) {
        BufferedImage planeLayer = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D planeGraphics = planeLayer.createGraphics();
        CameraRenderUtils.applyPixelRenderingHints(planeGraphics);
        renderSinglePlane(planeGraphics, plane);
        planeGraphics.dispose();
        return planeLayer;
    }

    private PlaneStyle getPlaneStyle(CameraPlane plane, Color planeColor) {
        if (isWallPlane(plane)) {
            Integer override = this.wallColorResolver == null ? null : this.wallColorResolver.lookup(this.wallPaint);
            Color fill = (override != null) ? new Color(override) : planeColor;
            // neutralWall=false so the wallpaper texture (incl. _wall_color_* templates) renders;
            // tinting it with plane.color reproduces what the client paints.
            return new PlaneStyle(fill, fill, false);
        }

        CameraPlane topCapWall = findWallTopCapWallPlane(plane);
        if (topCapWall != null) {
            return new PlaneStyle(planeColor, planeColor, false);
        }

        return new PlaneStyle(planeColor, planeColor, false);
    }

    private CameraPlane findWallTopCapWallPlane(CameraPlane candidate) {
        if (candidate == null || isWallPlane(candidate) || isFloorPlane(candidate) || isLandscapePlane(candidate)) {
            return null;
        }

        CameraPositionPoint[] candidatePoints = candidate.getCornerPoints();
        if (candidatePoints == null || candidatePoints.length < 4) {
            return null;
        }

        if (!isThinPlaneEdge(candidatePoints[0], candidatePoints[1])
                || !isThinPlaneEdge(candidatePoints[2], candidatePoints[3])) {
            return null;
        }

        if (this.data.getPlanes() == null) {
            return null;
        }

        for (CameraPlane wallPlane : this.data.getPlanes()) {
            if (wallPlane == null || !isWallPlane(wallPlane)) {
                continue;
            }

            CameraPositionPoint[] wallPoints = wallPlane.getCornerPoints();
            if (wallPoints == null || wallPoints.length < 4) {
                continue;
            }

            if (planeContainsPoint(candidatePoints, wallPoints[2])
                    && planeContainsPoint(candidatePoints, wallPoints[3])) {
                return wallPlane;
            }
        }

        return null;
    }

    private boolean planeContainsPoint(CameraPositionPoint[] points, CameraPositionPoint target) {
        if (points == null || target == null) {
            return false;
        }

        for (CameraPositionPoint point : points) {
            if (samePoint(point, target)) {
                return true;
            }
        }

        return false;
    }

    private boolean isThinPlaneEdge(CameraPositionPoint left, CameraPositionPoint right) {
        if (left == null || right == null) {
            return false;
        }

        int dx = left.x - right.x;
        int dy = left.y - right.y;
        return (dx * dx) + (dy * dy) <= 160;
    }

    private boolean samePoint(CameraPositionPoint left, CameraPositionPoint right) {
        return left != null && right != null && left.x == right.x && left.y == right.y;
    }

    private boolean hasTextureAssetWithPrefix(CameraPlane plane, String prefix) {
        if (plane.getTexCols() == null) {
            return false;
        }

        for (CameraTexCols texCol : plane.getTexCols()) {
            if (texCol == null || texCol.getAssetNames() == null) {
                continue;
            }

            for (String asset : texCol.getAssetNames()) {
                if (asset != null && asset.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean renderTexCols(Graphics2D target, Polygon polygon, CameraPlane plane, PlaneStyle planeStyle) {
        BufferedImage texture = buildPlaneTexture(plane, planeStyle);
        if (texture == null) {
            return false;
        }

        CameraPositionPoint[] points = plane.getCornerPoints();
        if (points == null || points.length < 4) {
            return false;
        }

        // Wall texture sprites are authored with the baseboard at the bottom of the source
        // image and the client paints walls in a mirrored frame on both axes (this is why
        // mask.x has to be mirrored to land the door correctly â€” RoomPlane.as ~505-506).
        // Without these flips the camera shows wall textures upside-down and asymmetric
        // patterns (chevrons, half-walls) come out reversed compared to the room view.
        // Landscape planes lie exactly on their wall and AIR draws both types the same way.
        if (isWallPlane(plane) || isLandscapePlane(plane)) {
            texture = flipVertical(texture);
            texture = CameraUtils.flipHorizontal(texture);
        }

        AffineTransform oldTransform = target.getTransform();
        target.setClip(polygon);
        if (shouldPrefillTexturedPlane(plane)) {
            target.setColor(getTexturePrefillColor(plane, planeStyle));
            target.fillPolygon(polygon);
        }
        AffineTransform transform = getTextureTransform(plane, texture.getWidth(), texture.getHeight());
        if (transform == null) {
            target.setClip(null);
            target.setTransform(oldTransform);
            return false;
        }
        target.drawImage(texture, transform, null);
        target.setClip(null);
        target.setTransform(oldTransform);
        return true;
    }

    private boolean shouldPrefillTexturedPlane(CameraPlane plane) {
        // A landscape is a stack of layers (colour, background, foreground) with transparent
        // parts: each layer shows the ones below it.
        if (isLandscapePlane(plane)) {
            return false;
        }
        if (isWallPlane(plane) && hasPlaneMasks(plane)) {
            return true;
        }

        return plane.getMasks() == null || plane.getMasks().length == 0;
    }

    private Color getTexturePrefillColor(CameraPlane plane, PlaneStyle planeStyle) {
        if (isWallPlane(plane) && hasPlaneMasks(plane)) {
            // Masked walls are redrawn and the doorway is punched back out later, so using
            // the wall fill here prevents 1px background leaks along the transformed wall edge.
            return planeStyle.fillColor();
        }

        return planeStyle.fillColor();
    }

    private BufferedImage buildPlaneTexture(CameraPlane plane, PlaneStyle planeStyle) {
        Rectangle bounds = getPlaneTextureBounds(plane);
        int maxPlaneTexturePixels = this.imageLoader.options().maxPlaneTexturePixels();
        if (bounds.width <= 0 || bounds.height <= 0 || (long) bounds.width * bounds.height > maxPlaneTexturePixels) {
            return null;
        }

        if (plane.getTexCols() == null || plane.getTexCols().length == 0) {
            return null;
        }

        BufferedImage texture = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D textureGraphics = texture.createGraphics();
        textureGraphics.setComposite(AlphaComposite.Src);

        List<BufferedImage> columns = new ArrayList<>();
        for (CameraTexCols texCol : plane.getTexCols()) {
            this.imageLoader.options().checkDeadline();
            BufferedImage column = buildTextureColumn(texCol, bounds.height, plane.isBottomAligned(), planeStyle);
            if (column != null) {
                columns.add(column);
            }
        }

        if (columns.isEmpty()) {
            textureGraphics.dispose();
            return null;
        }

        for (int x = 0; x < bounds.width; ) {
            int startX = x;
            for (BufferedImage column : columns) {
                textureGraphics.drawImage(column, x, 0, null);
                x += column.getWidth();
                if (x >= bounds.width) {
                    break;
                }
            }
            if (x == startX) {
                break;
            }
        }

        textureGraphics.dispose();
        applyMasks(texture, plane.getMasks());
        return texture;
    }

    private Rectangle getPlaneTextureBounds(CameraPlane plane) {
        CameraPositionPoint[] points = plane.getCornerPoints();
        if (points == null || points.length < 4) {
            return new Rectangle();
        }

        CameraPositionPoint widthPoint = getTextureWidthPoint(plane, points);
        CameraPositionPoint heightPoint = getTextureHeightPoint(plane, points);
        int widthValue = Math.max(1, Math.abs(points[0].x - widthPoint.x));
        int heightValue;
        if (isWallPlane(plane) || isLandscapePlane(plane)) {
            heightValue = Math.max(1, Math.abs(points[0].y - heightPoint.y));
        } else {
            // For floors and floor side-faces, take the larger of the X and Y projections of
            // the height-axis. The X-only shortcut works for the main floor rhombus (X and Y
            // are similar in iso projection) but collapses to 1px for thin south-east/west
            // lip strips whose height-axis corner shares X with the anchor â€” that's why the
            // floor texture wasn't wrapping around the lip and showed a solid colour instead.
            int hX = Math.abs(points[0].x - heightPoint.x);
            int hY = Math.abs(points[0].y - heightPoint.y);
            heightValue = Math.max(1, Math.max(hX, hY));
        }
        return new Rectangle(0, 0, widthValue, heightValue);
    }

    private CameraPositionPoint getTextureWidthPoint(CameraPlane plane, CameraPositionPoint[] points) {
        return points[1];
    }

    private CameraPositionPoint getTextureHeightPoint(CameraPlane plane, CameraPositionPoint[] points) {
        return points[2];
    }

    private BufferedImage buildTextureColumn(
            CameraTexCols texCol, int heightValue, boolean bottomAligned, PlaneStyle planeStyle) {
        if (texCol == null || texCol.getAssetNames() == null || texCol.getAssetNames().length == 0) {
            return null;
        }

        List<BufferedImage> cells = new ArrayList<>();
        boolean repeat = true;
        int widthValue = 0;
        int naturalHeight = 0;

        for (String asset : texCol.getAssetNames()) {
            if (asset == null || asset.isEmpty()) {
                repeat = false;
                continue;
            }

            if (shouldSkipNeutralWallTexture(asset, planeStyle)) {
                continue;
            }

            BufferedImage cell =
                    asset.contains("//") ? this.imageLoader.readUrlImage(asset) : this.imageLoader.readSprite(asset);
            if (cell == null) {
                continue;
            }

            cell = tintTexture(cell, planeStyle.textureTint());
            cells.add(cell);
            widthValue = Math.max(widthValue, cell.getWidth());
            naturalHeight += cell.getHeight();
        }

        if (cells.isEmpty() || widthValue <= 0) {
            return null;
        }

        int columnHeight = repeat ? heightValue : Math.min(heightValue, Math.max(1, naturalHeight));
        BufferedImage column = new BufferedImage(widthValue, heightValue, BufferedImage.TYPE_INT_ARGB);
        Graphics2D columnGraphics = column.createGraphics();
        columnGraphics.setComposite(AlphaComposite.SrcOver);

        int y = bottomAligned ? heightValue - columnHeight : 0;
        int endY = y + columnHeight;
        while (y < endY) {
            int startY = y;
            for (BufferedImage cell : cells) {
                int x = (widthValue - cell.getWidth()) / 2;
                columnGraphics.drawImage(cell, x, y, null);
                y += cell.getHeight();
                if (y >= endY || !repeat) {
                    break;
                }
            }
            if (y == startY || !repeat) {
                break;
            }
        }

        columnGraphics.dispose();
        return column;
    }

    private boolean shouldSkipNeutralWallTexture(String asset, PlaneStyle planeStyle) {
        return planeStyle.neutralWall() && asset.startsWith("wall_texture") && asset.contains("_wall_color_");
    }

    private BufferedImage tintTexture(BufferedImage source, Color tint) {
        int w = source.getWidth();
        int h = source.getHeight();
        int[] pixels = new int[w * h];
        source.getRGB(0, 0, w, h, pixels, 0, w);

        float tintR = tint.getRed() / 255f;
        float tintG = tint.getGreen() / 255f;
        float tintB = tint.getBlue() / 255f;

        for (int i = 0; i < pixels.length; i++) {
            int pixel = pixels[i];
            int alpha = (pixel >>> 24) & 0xFF;
            if (alpha == 0) {
                pixels[i] = 0;
                continue;
            }
            int red = Math.round(((pixel >> 16) & 0xFF) * tintR);
            int green = Math.round(((pixel >> 8) & 0xFF) * tintG);
            int blue = Math.round((pixel & 0xFF) * tintB);
            pixels[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
        }

        BufferedImage tinted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        tinted.setRGB(0, 0, w, h, pixels, 0, w);
        return tinted;
    }

    private void applyMasks(BufferedImage texture, CameraMask[] masks) {
        if (masks == null || masks.length == 0) {
            return;
        }

        for (CameraMask mask : masks) {
            if (mask == null
                    || mask.getLocation() == null
                    || mask.getName() == null
                    || mask.getName().isEmpty()) {
                continue;
            }

            BufferedImage maskImage = this.imageLoader.readSprite(mask.getName());
            if (maskImage == null) {
                continue;
            }

            if (mask.isFlipH()) {
                maskImage = CameraUtils.flipHorizontal(maskImage);
            }
            if (mask.isFlipV()) {
                maskImage = flipVertical(maskImage);
            }

            // The client computes wall mask coordinates in a mirrored frame on both axes
            // (RoomPlane.as ~505-506). We reproduce that frame implicitly by flipping the
            // whole wall texture H+V in renderTexCols, which carries the mask cutouts along
            // with it â€” so the cutouts placed at raw (location.x, location.y) here end up
            // at the same screen position the room view paints them.
            clearMaskedPixels(texture, maskImage, mask.getLocation().x, mask.getLocation().y);
        }
    }

    private void clearMaskedPixels(BufferedImage texture, BufferedImage mask, int startX, int startY) {
        int maskW = mask.getWidth();
        int maskH = mask.getHeight();
        int textureW = texture.getWidth();
        int textureH = texture.getHeight();

        int xMin = Math.max(0, -startX);
        int yMin = Math.max(0, -startY);
        int xMax = Math.min(maskW, textureW - startX);
        int yMax = Math.min(maskH, textureH - startY);
        int rw = xMax - xMin;
        int rh = yMax - yMin;
        if (rw <= 0 || rh <= 0) {
            return;
        }

        int[] maskPixels = new int[rw * rh];
        mask.getRGB(xMin, yMin, rw, rh, maskPixels, 0, rw);

        int destX = startX + xMin;
        int destY = startY + yMin;
        int[] texturePixels = new int[rw * rh];
        texture.getRGB(destX, destY, rw, rh, texturePixels, 0, rw);

        boolean changed = false;
        for (int i = 0; i < maskPixels.length; i++) {
            if (((maskPixels[i] >>> 24) & 0xFF) > 0) {
                texturePixels[i] = 0;
                changed = true;
            }
        }

        if (changed) {
            texture.setRGB(destX, destY, rw, rh, texturePixels, 0, rw);
        }
    }

    private record PlaneStyle(Color fillColor, Color textureTint, boolean neutralWall) {}
}
