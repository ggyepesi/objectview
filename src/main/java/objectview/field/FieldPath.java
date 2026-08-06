package objectview.field;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The one representation of an object field path inside ObjectView.
 *
 * <p>A path is a sequence of stable field keys.  Dotted text is deliberately a
 * serialization/UI boundary only: callers parse it once with {@link #parse(String)}
 * and format it with {@link #dotted()}.  Keeping the segments immutable prevents
 * rendering, search and field configuration from independently splitting,
 * joining or rewriting the same path.
 */
public record FieldPath(List<String> segments) implements Comparable<FieldPath> {

    public static final FieldPath ROOT = new FieldPath(List.of());

    public FieldPath {
        segments = segments == null ? List.of() : List.copyOf(segments);
        for (String segment : segments) {
            if (segment == null || segment.isBlank() || segment.indexOf('.') >= 0) {
                throw new IllegalArgumentException(
                        "Field-path segments must be non-blank field keys: " + segment);
            }
        }
    }

    public static FieldPath parse(String dotted) {
        if (dotted == null || dotted.isBlank()) return ROOT;
        return new FieldPath(Arrays.asList(dotted.split("\\.", -1)));
    }

    public static FieldPath of(String... segments) {
        return segments == null || segments.length == 0
                ? ROOT : new FieldPath(Arrays.asList(segments));
    }

    public FieldPath append(String segment) {
        Objects.requireNonNull(segment, "segment");
        List<String> result = new ArrayList<>(segments);
        result.add(segment);
        return new FieldPath(result);
    }

    public FieldPath append(FieldPath suffix) {
        if (suffix == null || suffix.isRoot()) return this;
        List<String> result = new ArrayList<>(segments);
        result.addAll(suffix.segments);
        return new FieldPath(result);
    }

    public FieldPath parent() {
        return segments.isEmpty()
                ? ROOT : new FieldPath(segments.subList(0, segments.size() - 1));
    }

    public String leaf() {
        return segments.isEmpty() ? "" : segments.get(segments.size() - 1);
    }

    public String first() {
        return segments.isEmpty() ? "" : segments.get(0);
    }

    public int size() { return segments.size(); }

    public boolean isRoot() { return segments.isEmpty(); }

    public String dotted() { return String.join(".", segments); }

    @Override public int compareTo(FieldPath other) {
        return dotted().compareTo(other == null ? "" : other.dotted());
    }

    @Override public String toString() { return dotted(); }
}
