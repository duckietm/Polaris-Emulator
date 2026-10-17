package com.eu.habbo.imaging.camera.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

final class CameraDoorwayRenderer {
    private static final double DOORWAY_CLUSTER_DEPTH_TOLERANCE = 0.5;
    private static final double PENDING_AVATAR_DEPTH_WEIGHT = 64.0;

    private final JSONCamera data;
    private final int width;
    private final int height;
    private final CameraPlaneRenderer planeRenderer;
    private final CameraSpriteRenderer spriteRenderer;

    private final Map<CameraPlane, DoorwayMask> doorwayMaskCache = new IdentityHashMap<>();
    private final Map<CameraPlane, DoorwayMask> avatarRevealMaskCache = new IdentityHashMap<>();
    private final Set<CameraSprite> doorwayAvatarSprites = Collections.newSetFromMap(new IdentityHashMap<>());

    CameraDoorwayRenderer(
            JSONCamera data,
            int width,
            int height,
            CameraPlaneRenderer planeRenderer,
            CameraSpriteRenderer spriteRenderer) {
        this.data = data;
        this.width = width;
        this.height = height;
        this.planeRenderer = planeRenderer;
        this.spriteRenderer = spriteRenderer;
    }

    void precomputeDoorwayState() {
        this.doorwayAvatarSprites.clear();
        this.doorwayMaskCache.clear();
        this.avatarRevealMaskCache.clear();

        if (this.data.getPlanes() == null || this.data.getSprites() == null) {
            return;
        }

        List<AvatarSpriteCluster> avatarClusters = buildAvatarSpriteClusters();
        for (CameraPlane plane : this.data.getPlanes()) {
            if (plane == null || !this.planeRenderer.isWallPlane(plane) || !this.planeRenderer.hasPlaneMasks(plane)) {
                continue;
            }

            DoorwayMask doorwayMask = getDoorwayMask(plane);
            if (doorwayMask == null || doorwayMask.bounds.isEmpty()) {
                continue;
            }

            Rectangle candidateBounds = CameraRenderUtils.expandRectangle(doorwayMask.bounds, 32);
            List<AvatarSpriteCluster> overlappingClusters = new ArrayList<>();
            double backmostClusterDepth = Double.NEGATIVE_INFINITY;
            for (AvatarSpriteCluster cluster : avatarClusters) {
                if (!cluster.intersects(candidateBounds)) {
                    continue;
                }

                if (!clusterOverlapsMask(cluster, doorwayMask)) {
                    continue;
                }
                overlappingClusters.add(cluster);
                backmostClusterDepth = Math.max(backmostClusterDepth, cluster.referenceDepth());
            }

            for (AvatarSpriteCluster cluster : overlappingClusters) {
                if ((backmostClusterDepth - cluster.referenceDepth()) > DOORWAY_CLUSTER_DEPTH_TOLERANCE) {
                    continue;
                }
                for (CameraSprite sprite : cluster.sprites()) {
                    this.doorwayAvatarSprites.add(sprite);
                }
            }
        }
    }

    boolean isDoorwayAvatarSprite(CameraSprite sprite) {
        return sprite != null && this.doorwayAvatarSprites.contains(sprite);
    }

    void renderDoorwayOcclusions(BufferedImage render, Graphics2D graphics, int renderBackgroundColor) {
        if (this.data.getPlanes() == null) {
            return;
        }

        for (int planeIndex = 0; planeIndex < this.data.getPlanes().length; planeIndex++) {
            CameraPlane plane = this.data.getPlanes()[planeIndex];
            if (plane == null || !this.planeRenderer.isWallPlane(plane) || !this.planeRenderer.hasPlaneMasks(plane)) {
                continue;
            }

            DoorwayMask doorwayMask = getDoorwayMask(plane);
            if (doorwayMask == null || doorwayMask.bounds.isEmpty()) {
                continue;
            }

            // Repaint the masked wall face after the main z-pass so any same-depth floor
            // planes or wide avatar parts that spilled over the wall get covered again.
            this.planeRenderer.renderSinglePlane(graphics, plane);
            fillDoorwayMask(render, doorwayMask, renderBackgroundColor);
            renderForegroundPlanesOverDoorway(graphics, planeIndex, doorwayMask, true);
            renderDoorwayAvatarReveals(graphics, planeIndex, doorwayMask);
            renderForegroundPlanesOverDoorway(graphics, planeIndex, doorwayMask, false);
            renderForegroundSpritesOverWall(graphics, plane, doorwayMask);
        }
    }

    private boolean spriteIntersects(CameraSprite sprite, Rectangle bounds) {
        Rectangle spriteBounds = this.spriteRenderer.getSpriteBounds(sprite);
        if (spriteBounds == null) {
            return false;
        }

        return spriteBounds.intersects(bounds);
    }

    private List<AvatarSpriteCluster> buildAvatarSpriteClusters() {
        List<AvatarSpriteCluster> clusters = new ArrayList<>();
        List<CameraSprite> pendingSprites = new ArrayList<>();

        for (CameraSprite sprite : this.data.getSprites()) {
            if (sprite == null || !this.spriteRenderer.isLikelyAvatarSprite(sprite)) {
                continue;
            }

            Rectangle spriteBounds = this.spriteRenderer.getSpriteBounds(sprite);
            if (this.spriteRenderer.isAvatarAnchorSprite(sprite)) {
                AvatarSpriteCluster cluster = new AvatarSpriteCluster(sprite, spriteBounds);
                cluster.add(sprite, spriteBounds);
                clusters.add(cluster);
                continue;
            }

            pendingSprites.add(sprite);
        }

        if (clusters.isEmpty()) {
            if (!pendingSprites.isEmpty()) {
                AvatarSpriteCluster fallbackCluster = new AvatarSpriteCluster(null, null);
                for (CameraSprite sprite : pendingSprites) {
                    fallbackCluster.add(sprite, this.spriteRenderer.getSpriteBounds(sprite));
                }
                clusters.add(fallbackCluster);
            }
            return clusters;
        }

        for (CameraSprite pendingSprite : pendingSprites) {
            Rectangle pendingBounds = this.spriteRenderer.getSpriteBounds(pendingSprite);
            AvatarSpriteCluster bestCluster = findBestPendingAvatarCluster(clusters, pendingSprite, pendingBounds);
            bestCluster.add(pendingSprite, pendingBounds);
        }

        return clusters;
    }

    private AvatarSpriteCluster findBestPendingAvatarCluster(
            List<AvatarSpriteCluster> clusters, CameraSprite sprite, Rectangle spriteBounds) {
        AvatarSpriteCluster bestCluster = clusters.get(0);
        double bestScore = Double.POSITIVE_INFINITY;

        for (AvatarSpriteCluster cluster : clusters) {
            double score = pendingAvatarClusterScore(cluster, sprite, spriteBounds);
            if (score < bestScore) {
                bestScore = score;
                bestCluster = cluster;
            }
        }

        return bestCluster;
    }

    private double pendingAvatarClusterScore(AvatarSpriteCluster cluster, CameraSprite sprite, Rectangle spriteBounds) {
        Rectangle clusterBounds = cluster.assignmentBounds();
        double spatialScore = CameraRenderUtils.rectangleCenterDistanceSquared(spriteBounds, clusterBounds);
        double depthPenalty = this.spriteRenderer.isAvatarShadowSprite(sprite)
                ? 0.0
                : Math.abs(sprite.getZ() - cluster.referenceDepth()) * PENDING_AVATAR_DEPTH_WEIGHT;
        return spatialScore + depthPenalty;
    }

    private boolean clusterOverlapsMask(AvatarSpriteCluster cluster, DoorwayMask doorwayMask) {
        int shadowOverlap = clusterMaskOverlap(cluster, doorwayMask, this.spriteRenderer::isAvatarShadowSprite, null);
        if (shadowOverlap > 0) {
            return true;
        }

        if (cluster.hasSpriteMatching(this.spriteRenderer::isAvatarShadowSprite)) {
            return false;
        }

        int footprintOverlap =
                clusterMaskOverlap(cluster, doorwayMask, this.spriteRenderer::isAvatarFootprintSprite, null);
        if (footprintOverlap > 0) {
            return true;
        }

        if (cluster.hasSpriteMatching(this.spriteRenderer::isAvatarFootprintSprite)) {
            return false;
        }

        Rectangle footprintBounds = cluster.doorwayFootprintBounds();
        return clusterMaskOverlap(cluster, doorwayMask, sprite -> true, footprintBounds) > 0;
    }

    private int clusterMaskOverlap(
            AvatarSpriteCluster cluster,
            DoorwayMask doorwayMask,
            Predicate<CameraSprite> predicate,
            Rectangle clipBounds) {
        int overlap = 0;
        for (CameraSprite sprite : cluster.sprites()) {
            if (!predicate.test(sprite)) {
                continue;
            }
            overlap += this.spriteRenderer.spriteMaskOverlapPixelCount(
                    sprite, doorwayMask.alpha, doorwayMask.bounds, clipBounds);
        }
        return overlap;
    }

    private void renderDoorwayAvatarReveals(Graphics2D graphics, int maskedWallPlaneIndex, DoorwayMask doorwayMask) {
        if (this.data.getSprites() == null) {
            return;
        }

        DoorwayMask revealMask = getAvatarDoorwayRevealMask(maskedWallPlaneIndex, doorwayMask);
        for (CameraSprite sprite : this.data.getSprites()) {
            if (sprite == null || !this.doorwayAvatarSprites.contains(sprite)) {
                continue;
            }
            this.spriteRenderer.renderSingleSpriteClippedToMask(
                    graphics, sprite, revealMask.alpha, revealMask.bounds, this.width, this.height);
        }
    }

    private void renderForegroundPlanesOverDoorway(
            Graphics2D graphics, int maskedWallPlaneIndex, DoorwayMask doorwayMask, boolean floorPlanes) {
        if (this.data.getPlanes() == null) {
            return;
        }

        if (floorPlanes) {
            renderFloorPlanesOverDoorway(graphics, doorwayMask);
            return;
        }

        CameraPlane backgroundPlane = this.planeRenderer.getBackgroundPlane();
        for (int planeIndex = maskedWallPlaneIndex + 1; planeIndex < this.data.getPlanes().length; planeIndex++) {
            CameraPlane plane = this.data.getPlanes()[planeIndex];
            if (plane == null || plane == backgroundPlane || this.planeRenderer.isLandscapePlane(plane)) {
                continue;
            }

            if (this.planeRenderer.isWallPlane(plane) && this.planeRenderer.hasPlaneMasks(plane)) {
                continue;
            }

            if (this.planeRenderer.isFloorPlane(plane) != floorPlanes) {
                continue;
            }

            if (this.planeRenderer.planeIntersects(plane, doorwayMask.bounds)) {
                this.planeRenderer.renderSinglePlaneClippedToMask(
                        graphics, plane, doorwayMask.alpha, doorwayMask.bounds, this.width, this.height);
            }
        }
    }

    private void renderFloorPlanesOverDoorway(Graphics2D graphics, DoorwayMask doorwayMask) {
        List<RenderLayer> floorLayers = new ArrayList<>();
        CameraPlane backgroundPlane = this.planeRenderer.getBackgroundPlane();
        int order = 0;

        for (CameraPlane plane : this.data.getPlanes()) {
            if (plane == null || plane == backgroundPlane || this.planeRenderer.isLandscapePlane(plane)) {
                continue;
            }

            if (this.planeRenderer.isFloorPlane(plane)
                    && this.planeRenderer.planeIntersects(plane, doorwayMask.bounds)) {
                floorLayers.add(RenderLayer.forPlane(plane, order));
            }

            order++;
        }

        floorLayers.sort(CameraDoorwayRenderer::compareRenderLayers);
        for (RenderLayer layer : floorLayers) {
            this.planeRenderer.renderSinglePlaneClippedToMask(
                    graphics, layer.plane, doorwayMask.alpha, doorwayMask.bounds, this.width, this.height);
        }
    }

    private void renderForegroundSpritesOverWall(Graphics2D graphics, CameraPlane plane, DoorwayMask doorwayMask) {
        if (this.data.getSprites() == null) {
            return;
        }

        Rectangle wallBounds =
                this.planeRenderer.getPlanePolygon(plane.getCornerPoints()).getBounds();
        for (CameraSprite sprite : this.data.getSprites()) {
            if (sprite == null || isDoorwayAvatarSprite(sprite) || sprite.getZ() >= plane.getZ()) {
                continue;
            }
            if (spriteIntersects(sprite, wallBounds)) {
                this.spriteRenderer.renderSingleSprite(graphics, sprite);
            }
        }
    }

    private void fillDoorwayMask(BufferedImage render, DoorwayMask doorwayMask, int renderBackgroundColor) {
        Rectangle bounds = doorwayMask.bounds;
        int x0 = Math.max(0, bounds.x);
        int y0 = Math.max(0, bounds.y);
        int x1 = Math.min(this.width, bounds.x + bounds.width);
        int y1 = Math.min(this.height, bounds.y + bounds.height);
        int rw = x1 - x0;
        int rh = y1 - y0;
        if (rw <= 0 || rh <= 0) {
            return;
        }

        int[] maskPixels = new int[rw * rh];
        doorwayMask.alpha.getRGB(x0, y0, rw, rh, maskPixels, 0, rw);
        int[] renderPixels = new int[rw * rh];
        render.getRGB(x0, y0, rw, rh, renderPixels, 0, rw);

        int fill = 0xFF000000 | (renderBackgroundColor & 0x00FFFFFF);
        boolean changed = false;
        for (int i = 0; i < maskPixels.length; i++) {
            if (((maskPixels[i] >>> 24) & 0xFF) > 0) {
                renderPixels[i] = fill;
                changed = true;
            }
        }
        if (changed) {
            render.setRGB(x0, y0, rw, rh, renderPixels, 0, rw);
        }
    }

    private DoorwayMask getDoorwayMask(CameraPlane plane) {
        if (this.doorwayMaskCache.containsKey(plane)) {
            return this.doorwayMaskCache.get(plane);
        }

        DoorwayMask doorwayMask = buildDoorwayMask(plane);
        this.doorwayMaskCache.put(plane, doorwayMask);
        return doorwayMask;
    }

    private DoorwayMask buildDoorwayMask(CameraPlane plane) {
        CameraPositionPoint[] points = plane.getCornerPoints();
        if (points == null || points.length < 4) {
            return null;
        }

        BufferedImage texture = this.planeRenderer.buildPlaneTexture(plane);
        if (texture == null) {
            return null;
        }

        if (this.planeRenderer.isWallPlane(plane)) {
            texture = this.planeRenderer.flipVertical(texture);
            texture = CameraUtils.flipHorizontal(texture);
        }

        AffineTransform transform =
                this.planeRenderer.getTextureTransform(plane, texture.getWidth(), texture.getHeight());
        if (transform == null) {
            return null;
        }

        Polygon polygon = this.planeRenderer.getPlanePolygon(points);
        BufferedImage renderedWall = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D wallGraphics = renderedWall.createGraphics();
        CameraRenderUtils.applyPixelRenderingHints(wallGraphics);
        wallGraphics.setClip(polygon);
        wallGraphics.drawImage(texture, transform, null);
        wallGraphics.dispose();

        Rectangle polygonBounds = polygon.getBounds();
        BufferedImage renderMask = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
        for (int x = polygonBounds.x; x < polygonBounds.x + polygonBounds.width; x++) {
            if (x < 0 || x >= this.width) {
                continue;
            }
            for (int y = polygonBounds.y; y < polygonBounds.y + polygonBounds.height; y++) {
                if (y < 0 || y >= this.height || !polygon.contains(x + 0.5, y + 0.5)) {
                    continue;
                }
                if (((renderedWall.getRGB(x, y) >>> 24) & 0xFF) == 0) {
                    renderMask.setRGB(x, y, 0xFFFFFFFF);
                }
            }
        }

        Rectangle bounds = CameraRenderUtils.getAlphaBounds(renderMask);
        return bounds.isEmpty() ? null : new DoorwayMask(renderMask, bounds);
    }

    private DoorwayMask getAvatarDoorwayRevealMask(int maskedWallPlaneIndex, DoorwayMask doorwayMask) {
        CameraPlane plane = this.data.getPlanes()[maskedWallPlaneIndex];
        if (this.avatarRevealMaskCache.containsKey(plane)) {
            return this.avatarRevealMaskCache.get(plane);
        }

        DoorwayMask revealMask = buildAvatarDoorwayRevealMask(maskedWallPlaneIndex, doorwayMask);
        this.avatarRevealMaskCache.put(plane, revealMask);
        return revealMask;
    }

    private DoorwayMask buildAvatarDoorwayRevealMask(int maskedWallPlaneIndex, DoorwayMask doorwayMask) {
        Rectangle bounds = doorwayMask.bounds;
        BufferedImage foregroundPlaneMask = buildForegroundPlaneMask(maskedWallPlaneIndex, bounds, false);
        foregroundPlaneMask = CameraRenderUtils.expandMask(foregroundPlaneMask, bounds, 10);
        BufferedImage revealMask = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);

        for (int x = bounds.x; x < bounds.x + bounds.width; x++) {
            if (x < 0 || x >= this.width) {
                continue;
            }
            for (int y = bounds.y; y < bounds.y + bounds.height; y++) {
                if (y < 0 || y >= this.height) {
                    continue;
                }
                if (((foregroundPlaneMask.getRGB(x, y) >>> 24) & 0xFF) > 0) {
                    continue;
                }
                revealMask.setRGB(x, y, doorwayMask.alpha.getRGB(x, y));
            }
        }

        Rectangle revealBounds = CameraRenderUtils.getAlphaBounds(revealMask);
        return revealBounds.isEmpty() ? doorwayMask : new DoorwayMask(revealMask, revealBounds);
    }

    private BufferedImage buildForegroundPlaneMask(
            int maskedWallPlaneIndex, Rectangle bounds, boolean includeFloorPlanes) {
        BufferedImage planeMask = new BufferedImage(this.width, this.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D maskGraphics = planeMask.createGraphics();
        CameraRenderUtils.applyPixelRenderingHints(maskGraphics);
        maskGraphics.setColor(Color.WHITE);

        CameraPlane backgroundPlane = this.planeRenderer.getBackgroundPlane();
        for (int planeIndex = maskedWallPlaneIndex + 1; planeIndex < this.data.getPlanes().length; planeIndex++) {
            CameraPlane plane = this.data.getPlanes()[planeIndex];
            if (plane == null || plane == backgroundPlane || this.planeRenderer.isLandscapePlane(plane)) {
                continue;
            }

            if (this.planeRenderer.isFloorPlane(plane) != includeFloorPlanes) {
                continue;
            }

            if (!this.planeRenderer.planeIntersects(plane, bounds)) {
                continue;
            }

            CameraPositionPoint[] points = plane.getCornerPoints();
            if (points == null || points.length < 4) {
                continue;
            }

            maskGraphics.fillPolygon(this.planeRenderer.getPlanePolygon(points));
        }

        maskGraphics.dispose();
        return planeMask;
    }

    private static int compareRenderLayers(RenderLayer left, RenderLayer right) {
        int depthCompare = Double.compare(right.depth, left.depth);
        if (depthCompare != 0) {
            return depthCompare;
        }
        return Integer.compare(left.order, right.order);
    }

    private record RenderLayer(double depth, int order, CameraPlane plane) {
        private static RenderLayer forPlane(CameraPlane plane, int order) {
            return new RenderLayer(plane.getZ(), order, plane);
        }
    }

    private static final class AvatarSpriteCluster {
        private final List<CameraSprite> sprites = new ArrayList<>();
        private final Rectangle anchorBounds;
        private final double anchorDepth;
        private Rectangle bounds = null;
        private double maxDepth = Double.NEGATIVE_INFINITY;

        private AvatarSpriteCluster(CameraSprite anchorSprite, Rectangle anchorBounds) {
            this.anchorBounds = anchorBounds == null ? null : new Rectangle(anchorBounds);
            this.anchorDepth = anchorSprite == null ? Double.NaN : anchorSprite.getZ();
        }

        private void add(CameraSprite sprite, Rectangle spriteBounds) {
            this.sprites.add(sprite);
            this.maxDepth = Math.max(this.maxDepth, sprite.getZ());
            if (spriteBounds == null || spriteBounds.isEmpty()) {
                return;
            }

            if (this.bounds == null) {
                this.bounds = new Rectangle(spriteBounds);
            } else {
                this.bounds = this.bounds.union(spriteBounds);
            }
        }

        private boolean intersects(Rectangle other) {
            return this.bounds != null && other != null && this.bounds.intersects(other);
        }

        private List<CameraSprite> sprites() {
            return this.sprites;
        }

        private boolean hasSpriteMatching(Predicate<CameraSprite> predicate) {
            for (CameraSprite sprite : this.sprites) {
                if (predicate.test(sprite)) {
                    return true;
                }
            }
            return false;
        }

        private Rectangle assignmentBounds() {
            return this.bounds != null ? this.bounds : this.anchorBounds;
        }

        private Rectangle doorwayFootprintBounds() {
            Rectangle sourceBounds = assignmentBounds();
            if (sourceBounds == null || sourceBounds.isEmpty()) {
                return sourceBounds;
            }

            int footprintY = sourceBounds.y + (int) Math.floor(sourceBounds.height * 0.6);
            int footprintHeight = Math.max(1, (sourceBounds.y + sourceBounds.height) - footprintY);
            return new Rectangle(sourceBounds.x, footprintY, sourceBounds.width, footprintHeight);
        }

        private double referenceDepth() {
            return Double.isNaN(this.anchorDepth) ? this.maxDepth : this.anchorDepth;
        }
    }

    record DoorwayMask(BufferedImage alpha, Rectangle bounds) {}
}
