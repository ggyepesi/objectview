package objectview.utils.swing;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Host-supplied policy for rasterizing SVG bytes to PNG/JPEG bytes, so
 * {@code objectview} can display SVG images without a hard SVG-toolkit
 * dependency (e.g. Batik). By default, SVG is unsupported and rasterization
 * returns {@code null} (the caller then treats the image as undecodable —
 * raster formats still work). A host that needs SVG registers a rasterizer
 * via {@link #setActive}, mirroring {@link objectview.media.ImageBlurrer}.
 */
public interface SvgRasterizer {

    /**
     * Rasterize SVG bytes to PNG ({@code jpeg == false}) or JPEG
     * ({@code jpeg == true}) bytes, or {@code null} if SVG rasterization is
     * unavailable.
     */
    byte[] rasterize(byte[] svg, boolean jpeg) throws Exception;

    /** The no-op rasterizer: no SVG support. */
    SvgRasterizer NONE = (svg, jpeg) -> null;

    AtomicReference<SvgRasterizer> ACTIVE = new AtomicReference<>(NONE);

    /** Register the host's rasterizer (null resets to {@link #NONE}). */
    static void setActive(SvgRasterizer rasterizer) {
        ACTIVE.set(rasterizer == null ? NONE : rasterizer);
    }

    /** The currently registered rasterizer (never null). */
    static SvgRasterizer active() {
        return ACTIVE.get();
    }
}
