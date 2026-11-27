package objectview.field;

import objectview.Viewable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordinary text assembled from independently linked and unlinked runs. Renderers may
 * make each linked run clickable; search, sort, copy and non-interactive renderers use
 * the same {@link #text()} projection.
 */
public final class LinkedTextSequence implements CharSequence {
    public record Part(String text, String url, Viewable reference) {
        public Part(String text, String url) { this(text, url, null); }
        public Part {
            text = text == null ? "" : text;
            url = url == null ? "" : url.trim();
            if (reference != null && !url.isEmpty()) {
                throw new IllegalArgumentException("A text link has one destination");
            }
        }

        public boolean linked() { return reference != null || !url.isBlank(); }
    }

    private final List<Part> parts;
    private final String text;

    private LinkedTextSequence(List<Part> parts) {
        List<Part> retained = new ArrayList<>();
        StringBuilder joined = new StringBuilder();
        if (parts != null) for (Part part : parts) {
            if (part == null || part.text().isEmpty()) continue;
            retained.add(part);
            joined.append(part.text());
        }
        this.parts = List.copyOf(retained);
        this.text = joined.toString();
    }

    public static LinkedTextSequence of(Part... parts) {
        return new LinkedTextSequence(parts == null ? List.of() : List.of(parts));
    }

    public static Part text(String text) { return new Part(text, ""); }

    public static Part link(String text, String url) { return new Part(text, url); }

    public static Part reference(String text, Viewable target) {
        return new Part(text, "", java.util.Objects.requireNonNull(target));
    }

    public boolean hasLinks() { return parts.stream().anyMatch(Part::linked); }

    /** Keep the text and its external URLs, but make instance mentions plain. */
    public LinkedTextSequence withoutInstanceLinks() {
        if (parts.stream().noneMatch(part -> part.reference() != null)) return this;
        return new LinkedTextSequence(parts.stream()
                .map(part -> part.reference() != null ? text(part.text()) : part)
                .toList());
    }

    public List<Part> parts() { return parts; }

    public String text() { return text; }

    /** The linked run containing this character offset, or null for plain text. */
    public Part linkedPartAt(int offset) {
        if (offset < 0 || offset >= text.length()) return null;
        int start = 0;
        for (Part part : parts) {
            int end = start + part.text().length();
            if (offset < end) return part.linked() ? part : null;
            start = end;
        }
        return null;
    }

    public List<String> urls() {
        return parts.stream().map(Part::url).filter(url -> !url.isBlank()).toList();
    }

    @Override public int length() { return text.length(); }
    @Override public char charAt(int index) { return text.charAt(index); }
    @Override public CharSequence subSequence(int start, int end) {
        return text.subSequence(start, end);
    }
    @Override public String toString() { return text; }
}
