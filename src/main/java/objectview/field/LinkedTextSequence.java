package objectview.field;

import java.util.ArrayList;
import java.util.List;

/**
 * Ordinary text assembled from independently linked and unlinked runs. Renderers may
 * make each linked run clickable; search, sort, copy and non-interactive renderers use
 * the same {@link #text()} projection.
 */
public final class LinkedTextSequence implements CharSequence {
    public record Part(String text, String url) {
        public Part {
            text = text == null ? "" : text;
            url = url == null ? "" : url.trim();
        }

        public boolean linked() { return !url.isBlank(); }
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
        return parts.stream().filter(Part::linked).map(Part::url).toList();
    }

    @Override public int length() { return text.length(); }
    @Override public char charAt(int index) { return text.charAt(index); }
    @Override public CharSequence subSequence(int start, int end) {
        return text.subSequence(start, end);
    }
    @Override public String toString() { return text; }
}
