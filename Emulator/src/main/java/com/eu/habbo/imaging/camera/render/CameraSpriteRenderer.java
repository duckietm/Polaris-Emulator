package com.eu.habbo.imaging.camera.render;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

final class CameraSpriteRenderer {
    private final CameraImageLoader imageLoader;

    CameraSpriteRenderer(CameraImageLoader imageLoader) {
        this.imageLoader = imageLoader;
    }

    void renderSingleSprite(Graphics2D target, CameraSprite sprite) {
        renderSprite(target, sprite);
    }

    void renderSingleSpriteClippedToMask(
            Graphics2D target,
            CameraSprite sprite,
            BufferedImage maskAlpha,
            Rectangle maskBounds,
            int width,
            int height) {
        BufferedImage spriteLayer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D spriteGraphics = spriteLayer.createGraphics();
        CameraRenderUtils.applyPixelRenderingHints(spriteGraphics);
        renderSprite(spriteGraphics, sprite);
        spriteGraphics.dispose();

        CameraRenderUtils.drawMaskedLayer(target, spriteLayer, maskAlpha, maskBounds, width, height);
    }

    Rectangle getSpriteBounds(CameraSprite sprite) {
        if (sprite == null) {
            return null;
        }

        BufferedImage spriteImage = getSpriteImage(sprite);
        if (spriteImage == null) {
            return new Rectangle(sprite.getX(), sprite.getY(), 1, 1);
        }

        return new Rectangle(sprite.getX(), sprite.getY(), spriteImage.getWidth(), spriteImage.getHeight());
    }

    BufferedImage getSpriteImage(CameraSprite sprite) {
        BufferedImage image = this.imageLoader.getSpriteImage(sprite);
        if (image != null
                && sprite.isExternalImage()
                && sprite.getWidth() > 0
                && sprite.getHeight() > 0
                && sprite.getWidth() <= 1024
                && sprite.getHeight() <= 1024
                && (sprite.getWidth() != image.getWidth() || sprite.getHeight() != image.getHeight())) {
            // The client sends the size it drew an external image at; draw it the same size.
            image = CameraUtils.resize(image, sprite.getWidth(), sprite.getHeight());
        }
        return image;
    }

    boolean isAvatarFootprintSprite(CameraSprite sprite) {
        String part = getAvatarSpritePart(sprite);
        return "sd".equals(part) || "lg".equals(part) || "sh".equals(part) || "wa".equals(part);
    }

    boolean isAvatarShadowSprite(CameraSprite sprite) {
        return "sd".equals(getAvatarSpritePart(sprite));
    }

    boolean isAvatarAnchorSprite(CameraSprite sprite) {
        String name = sprite.getName();
        return name != null && name.startsWith("avatar_");
    }

    String getAvatarSpritePart(CameraSprite sprite) {
        if (sprite == null) {
            return null;
        }

        String name = sprite.getName();
        if (name == null) {
            return null;
        }

        String[] parts = name.split("_");
        return parts.length > 2 ? parts[2] : null;
    }

    boolean isLikelyAvatarSprite(CameraSprite sprite) {
        String name = sprite.getName();
        if (name == null || name.isEmpty() || sprite.isFromUrl()) {
            return false;
        }

        return name.startsWith("avatar_")
                || name.startsWith("h_")
                || name.startsWith("hd_")
                || name.startsWith("hr_")
                || name.startsWith("ha_")
                || name.startsWith("he_")
                || name.startsWith("ea_")
                || name.startsWith("fa_")
                || name.startsWith("ch_")
                || name.startsWith("cc_")
                || name.startsWith("cp_")
                || name.startsWith("ca_")
                || name.startsWith("lg_")
                || name.startsWith("sh_")
                || name.startsWith("wa_");
    }

    boolean spriteOverlapsMask(CameraSprite sprite, BufferedImage maskAlpha, Rectangle maskBounds) {
        return spriteOverlapsMask(sprite, maskAlpha, maskBounds, null);
    }

    boolean spriteOverlapsMask(
            CameraSprite sprite, BufferedImage maskAlpha, Rectangle maskBounds, Rectangle clipBounds) {
        return spriteMaskOverlapPixelCount(sprite, maskAlpha, maskBounds, clipBounds) > 0;
    }

    int spriteMaskOverlapPixelCount(
            CameraSprite sprite, BufferedImage maskAlpha, Rectangle maskBounds, Rectangle clipBounds) {
        Rectangle spriteBounds = getSpriteBounds(sprite);
        if (spriteBounds == null) {
            return 0;
        }

        Rectangle intersection = spriteBounds.intersection(maskBounds);
        if (clipBounds != null) {
            intersection = intersection.intersection(clipBounds);
        }
        if (intersection.isEmpty()) {
            return 0;
        }

        BufferedImage spriteImage = getSpriteImage(sprite);
        if (spriteImage == null) {
            return 0;
        }

        int overlapPixels = 0;
        for (int x = intersection.x; x < intersection.x + intersection.width; x++) {
            int imageX = x - spriteBounds.x;
            if (sprite.isFlipH()) {
                imageX = spriteImage.getWidth() - 1 - imageX;
            }
            if (imageX < 0 || imageX >= spriteImage.getWidth()) {
                continue;
            }

            for (int y = intersection.y; y < intersection.y + intersection.height; y++) {
                if (((maskAlpha.getRGB(x, y) >>> 24) & 0xFF) == 0) {
                    continue;
                }

                int imageY = y - spriteBounds.y;
                if (imageY < 0 || imageY >= spriteImage.getHeight()) {
                    continue;
                }

                if (((spriteImage.getRGB(imageX, imageY) >>> 24) & 0xFF) > 0) {
                    overlapPixels++;
                }
            }
        }

        return overlapPixels;
    }

    private void renderSprite(Graphics2D target, CameraSprite sprite) {
        Integer rawAlpha = sprite.getAlpha();
        int alpha = rawAlpha == null ? 255 : Math.max(0, Math.min(255, rawAlpha));

        BufferedImage spriteImage = getSpriteImage(sprite);
        boolean averageColor = sprite.colorIsAverage();
        if (spriteImage == null) {
            // The picture could not be had (fetching off, refused, missing badge part): draw the
            // block of average colour the client sent for it, at the size it drew it.
            spriteImage = averageColorBlock(sprite);
            if (spriteImage == null) {
                return;
            }
        }

        if (sprite.isFlipH()) {
            spriteImage = CameraUtils.flipHorizontal(spriteImage);
        }

        if (!sprite.getPaletteSourceName().isEmpty() && !sprite.isExternalImage()) {
            int[] palette = this.imageLoader.readPalette(sprite.getName(), sprite.getPaletteSourceName());

            if (palette != null) {
                spriteImage = copyBufferedImage(spriteImage);
                CameraUtils.applyGreenIndexedPalette(spriteImage, palette);
            }
        }

        // An average colour is not a tint: the picture itself is drawn as it is.
        if (!averageColor && sprite.getColor() != 16777215 && sprite.getColor() != -1) {
            spriteImage = copyBufferedImage(spriteImage);
            CameraUtils.recolor(spriteImage, new Color((0xFF << 24) | sprite.getColor()));
        }

        Composite previousComposite = target.getComposite();
        BlendComposite.Mode blendMode = BlendComposite.Mode.fromName(sprite.getBlendMode());
        if (blendMode != null) {
            target.setComposite(new BlendComposite(blendMode, alpha / 255.0f));
        } else {
            target.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha / 255.0f));
        }

        if (sprite.getSkew() != 0) {
            AffineTransform oldTransform = target.getTransform();
            AffineTransform transform = AffineTransform.getShearInstance(0, 0);
            transform.shear(0, sprite.isFlipH() ? sprite.getSkew() : -sprite.getSkew());
            target.setTransform(transform);
            target.drawImage(spriteImage, sprite.getX(), sprite.getY() + (spriteImage.getHeight() / 2), null);
            target.setTransform(oldTransform);
        } else {
            target.drawImage(spriteImage, sprite.getX(), sprite.getY(), null);
        }

        target.setComposite(previousComposite);
    }

    /** A {@code width} x {@code height} block of the sprite's average colour, or null when it has none. */
    static BufferedImage averageColorBlock(CameraSprite sprite) {
        if (!sprite.colorIsAverage()
                || sprite.getColor() == -1
                || sprite.getWidth() <= 0
                || sprite.getHeight() <= 0
                || sprite.getWidth() > 1024
                || sprite.getHeight() > 1024) {
            return null;
        }
        BufferedImage block = new BufferedImage(sprite.getWidth(), sprite.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = block.createGraphics();
        graphics.setColor(new Color(sprite.getColor() & 0xFFFFFF));
        graphics.fillRect(0, 0, sprite.getWidth(), sprite.getHeight());
        graphics.dispose();
        return block;
    }

    private BufferedImage copyBufferedImage(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return copy;
    }
}
