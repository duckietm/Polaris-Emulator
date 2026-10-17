package com.eu.habbo.imaging.camera.render;

/** A render ran past its deadline and was abandoned. */
public final class CameraRenderTimeoutException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public CameraRenderTimeoutException() {
        super("Camera render took longer than its deadline");
    }
}
