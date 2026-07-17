package objectview.media;

/**
 * A media (image) value the renderer can turn into an {@link ImagePane} without
 * depending on any backing. A backing type (e.g. a Wikidata media value) holds
 * the metadata and implements this; {@code ValueRenderer} converts it to
 * a real image pane at render time. Keeping the interface generic (and the
 * backing value serializable) means the data pool never carries Swing components.
 */
public interface MediaValue {

    /** The image URL. */
    String mediaUrl();

    /** A label/title for the image (may be blank). */
    String mediaLabel();

    /** True when the URL is an SVG. */
    boolean mediaSvg();
}
