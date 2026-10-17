package com.eu.habbo.imaging.camera.render;

/**
 * Per-render limits: texture size, the external image fetch budget and the render deadline.
 *
 * @param maxPlaneTexturePixels largest plane texture (width times height) built
 * @param fetchEnabled whether {@code http...} sprite names are fetched at all
 * @param fetchMaxPerRender most external images fetched in one render
 * @param fetchBudgetMs total time external fetches may take in one render
 * @param fetchConnectTimeoutMs connect timeout of one fetch
 * @param fetchReadTimeoutMs read timeout of one fetch
 * @param fetchMaxBytes largest external image accepted, in bytes
 * @param fetchMaxPixels largest external image accepted, width times height
 * @param allowedHosts comma separated hosts (or {@code *.suffix}) that may be private addresses
 * @param deadlineNanos {@link System#nanoTime()} after which the render is abandoned, 0 for none
 */
public record CameraRenderOptions(
        int maxPlaneTexturePixels,
        boolean fetchEnabled,
        int fetchMaxPerRender,
        int fetchBudgetMs,
        int fetchConnectTimeoutMs,
        int fetchReadTimeoutMs,
        int fetchMaxBytes,
        int fetchMaxPixels,
        String allowedHosts,
        long deadlineNanos) {

    public static CameraRenderOptions defaults() {
        return new CameraRenderOptions(1_048_576, false, 30, 8000, 2000, 3000, 2_097_152, 4_000_000, "", 0L);
    }

    public CameraRenderOptions withDeadline(long deadline) {
        return new CameraRenderOptions(
                this.maxPlaneTexturePixels,
                this.fetchEnabled,
                this.fetchMaxPerRender,
                this.fetchBudgetMs,
                this.fetchConnectTimeoutMs,
                this.fetchReadTimeoutMs,
                this.fetchMaxBytes,
                this.fetchMaxPixels,
                this.allowedHosts,
                deadline);
    }

    /** Throws {@link CameraRenderTimeoutException} once the deadline has passed. */
    public void checkDeadline() {
        if (this.deadlineNanos != 0L && System.nanoTime() - this.deadlineNanos > 0) {
            throw new CameraRenderTimeoutException();
        }
    }
}
