package com.eu.habbo.imaging.camera;

import java.util.Locale;

/**
 * How the camera packets (RenderRoom 3226 and RenderRoomThumbnail 1982) are served, set by {@code
 * camera.render.mode}.
 */
public enum CameraRenderMode {
    /** Only the PNG the client rendered itself is accepted (the Octane client today). */
    UPLOAD,
    /** Only the official client's scene JSON is accepted; the server renders it. */
    SERVER,
    /** Both, told apart by the payload bytes; JSON only when assets are configured. */
    AUTO;

    public static CameraRenderMode parse(String value) {
        if (value == null) {
            return AUTO;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "upload", "png", "client" -> UPLOAD;
            case "server", "json", "air" -> SERVER;
            default -> AUTO;
        };
    }

    /** What the payload bytes look like. */
    public enum Payload {
        PNG,
        ZLIB,
        UNKNOWN;

        /** Reads the first bytes: PNG magic, or a zlib header (CMF 0x78 with a valid FCHECK). */
        public static Payload detect(byte[] head) {
            if (head == null || head.length < 2) {
                return UNKNOWN;
            }
            if (head.length >= 4 && head[0] == (byte) 0x89 && head[1] == 0x50 && head[2] == 0x4E && head[3] == 0x47) {
                return PNG;
            }
            int cmf = head[0] & 0xFF;
            int flg = head[1] & 0xFF;
            if ((cmf & 0x0F) == 8 && (cmf >> 4) <= 7 && ((cmf << 8) | flg) % 31 == 0) {
                return ZLIB;
            }
            return UNKNOWN;
        }
    }

    /** What a handler does with one payload. */
    public enum Decision {
        /** Store the uploaded PNG (the existing path). */
        STORE_UPLOAD,
        /** Render the scene JSON on the server. */
        RENDER,
        /** A PNG while the mode is {@link #SERVER}. */
        REFUSE_UPLOAD_DISABLED,
        /** Scene JSON while the mode is {@link #UPLOAD}. */
        REFUSE_RENDER_DISABLED,
        /** Scene JSON but no asset source is configured, so there is nothing to render with. */
        REFUSE_RENDER_UNAVAILABLE,
        /** Neither a PNG nor zlib data. */
        REFUSE_UNKNOWN
    }

    public Decision decide(Payload payload, boolean serverRenderAvailable) {
        return switch (payload) {
            case PNG -> this == SERVER ? Decision.REFUSE_UPLOAD_DISABLED : Decision.STORE_UPLOAD;
            case ZLIB -> {
                if (this == UPLOAD) {
                    yield Decision.REFUSE_RENDER_DISABLED;
                }
                yield serverRenderAvailable ? Decision.RENDER : Decision.REFUSE_RENDER_UNAVAILABLE;
            }
            case UNKNOWN -> Decision.REFUSE_UNKNOWN;
        };
    }
}
