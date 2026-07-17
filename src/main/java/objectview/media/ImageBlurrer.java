package objectview.media;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Host-supplied policy for blurring answer-revealing images — e.g. a quiz hides the
 * subject's name baked into a portrait. {@code objectview} calls the ACTIVE blurrer
 * when a view opts into blurring ({@code ViewConfig.isBlurImages()}); by
 * default nothing is blurred, so the library has no quiz/OCR dependency. A host
 * registers its implementation via {@link #setActive}.
 */
public interface ImageBlurrer {

    /** Whether an image for this object (type/name) should be blurred. */
    boolean blurs(String type, String name);

    /** The blurred image, or {@code src} unchanged if nothing to blur. */
    BufferedImage blur(String type, String name, BufferedImage src);

    /** The no-op blurrer: never blurs. */
    ImageBlurrer NONE = new ImageBlurrer() {
        @Override public boolean blurs(String type, String name) { return false; }
        @Override public BufferedImage blur(String type, String name, BufferedImage src) { return src; }
    };

    AtomicReference<ImageBlurrer> ACTIVE = new AtomicReference<>(NONE);

    /** Register the host's blurrer (null resets to {@link #NONE}). */
    static void setActive(ImageBlurrer blurrer) {
        ACTIVE.set(blurrer == null ? NONE : blurrer);
    }

    /** The currently registered blurrer (never null). */
    static ImageBlurrer active() {
        return ACTIVE.get();
    }
}
