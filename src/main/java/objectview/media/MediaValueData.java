package objectview.media;

import java.util.Objects;

/**
 * Serializable, backing-neutral media metadata.
 *
 * <p>This is the canonical value stored at a snapshot boundary. UI components,
 * datasource-specific media objects and curation values can all be reduced to these
 * three facts without making the snapshot depend on their implementation classes.
 */
public class MediaValueData implements MediaValue {

    private String label;
    private String url;
    private boolean svg;

    public MediaValueData() {
        this("", "", false);
    }

    public MediaValueData(String label, String url, boolean svg) {
        this.label = label == null ? "" : label;
        this.url = url == null ? "" : url;
        this.svg = svg;
    }

    @Override public String mediaLabel() { return label; }
    @Override public String mediaUrl() { return url; }
    @Override public boolean mediaSvg() { return svg; }

    public String label() { return label; }
    public void label(String label) { this.label = label == null ? "" : label; }
    public String url() { return url; }
    public void url(String url) { this.url = url == null ? "" : url; }
    public boolean svg() { return svg; }
    public void svg(boolean svg) { this.svg = svg; }
    public boolean hasUrl() { return !url.isBlank(); }

    public String displayText() {
        return !label.isBlank() ? label : url.isBlank() ? "<media>" : url;
    }

    @Override public String toString() { return displayText(); }

    @Override public boolean equals(Object other) {
        return other instanceof MediaValue media
                && Objects.equals(url, media.mediaUrl())
                && Objects.equals(label, media.mediaLabel())
                && svg == media.mediaSvg();
    }

    @Override public int hashCode() { return Objects.hash(label, url, svg); }
}
