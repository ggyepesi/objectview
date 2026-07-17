package objectview.media;

/**
 * A field value that can produce a renderable image. Lets headless
 * consumers (e.g. the web backend) serve an image without depending on the
 * Swing image component that implements it — they just ask for PNG bytes.
 */
public interface ImageRef {

    /** Rendered PNG bytes, or null if no image is available. */
    byte[] pngBytes() throws Exception;
}
