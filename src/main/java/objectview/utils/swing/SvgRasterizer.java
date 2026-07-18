package objectview.utils.swing;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Host-supplied policy for rasterizing SVG bytes to PNG/JPEG bytes, so
 * {@code objectview} can display SVG images without a hard SVG-toolkit
 * dependency (e.g. Batik). By default SVG is unsupported and rasterization
 * returns {@code null} (the caller treats the image as undecodable — raster
 * formats still work).
 *
 * <p>A host enables SVG either by registering programmatically with
 * {@link #setActive}, or — preferably — by providing an implementation on the
 * classpath as a {@link ServiceLoader} service (a
 * {@code META-INF/services/objectview.utils.swing.SvgRasterizer} entry). The
 * service is discovered automatically from any entry point, mirroring
 * {@link objectview.render.CardSearchBarFactory} and avoiding reliance on a
 * particular bootstrap class being loaded.
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

    AtomicReference<SvgRasterizer> ACTIVE = new AtomicReference<>();

    /** Register the host's rasterizer explicitly (null re-enables discovery). */
    static void setActive(SvgRasterizer rasterizer) {
        ACTIVE.set(rasterizer);
    }

    /** The active rasterizer: an explicit override if set, else the first one
     *  discovered on the classpath, else {@link #NONE} (never null). */
    static SvgRasterizer active() {
        SvgRasterizer rasterizer = ACTIVE.get();
        if (rasterizer == null) {
            rasterizer = ServiceLoader.load(SvgRasterizer.class)
                    .findFirst()
                    .orElse(NONE);
            ACTIVE.compareAndSet(null, rasterizer);
        }
        return ACTIVE.get();
    }
}
