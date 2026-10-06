package objectview.plan;

import objectview.Viewable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A sink of the real {@link RenderExecutor} that builds no UI. It records the decision
 * trace and a tree of mock components, one counterpart per representation, so a test can
 * state exactly what is rendered, skipped and deferred.
 */
public final class MockSink implements RenderSink {

    /** A mock counterpart of one painted component. */
    public sealed interface Mock permits MockObject, MockCollection, MockNavigation,
            MockBackReference, MockLeaf {}

    public record MockObject(String at, String label, String caption, boolean expanded,
                             List<Mock> children) implements Mock {}

    public record MockCollection(String at, String label, int size, boolean expanded,
                                 List<Mock> members) implements Mock {}

    public record MockNavigation(String at, String label, String caption) implements Mock {}

    public record MockBackReference(String at, String label, String caption) implements Mock {}

    /** A caption-less leaf: {@link Representation#TEXT}, {@code LINK} or {@code MEDIA}. */
    public record MockLeaf(String at, String label, Representation representation,
                           Object value) implements Mock {}

    private final List<String> trace = new ArrayList<>();
    private final Deque<List<Mock>> open = new ArrayDeque<>();
    private final Deque<Object[]> pending = new ArrayDeque<>();
    private Mock root;
    private int depth;

    public MockSink() {
        open.push(new ArrayList<>());
    }

    /** One line per decision, indented by nesting. */
    public List<String> trace() { return List.copyOf(trace); }

    public String traceText() { return String.join("\n", trace); }

    /** The root mock component (the rendered top-level object). */
    public Mock root() { return root; }

    @Override public void beginObject(Occurrence at, Viewable target, String caption,
                                      boolean expanded) {
        line("OBJECT", at.at().isEmpty() ? "<root>" : at.at(),
                (caption == null ? "" : "caption=\"" + caption + "\" ")
                        + (expanded ? "open" : "folded"));
        depth++;
        pending.push(new Object[]{at, caption, expanded});
        open.push(new ArrayList<>());
    }

    @Override public void endObject(Occurrence at) {
        depth--;
        Object[] begun = pending.pop();
        List<Mock> children = open.pop();
        add(new MockObject(at.at(), at.label(), (String) begun[1], (Boolean) begun[2],
                List.copyOf(children)));
    }

    @Override public void navigation(Occurrence at, Viewable target, String caption) {
        line("LINK-TO", at.at(), caption == null ? "\"Open\"" : "\"" + caption + "\"");
        add(new MockNavigation(at.at(), at.label(), caption));
    }

    @Override public void backReference(Occurrence at, Viewable target, String caption) {
        line("BACKREF", at.at(), caption == null ? "" : "\"" + caption + "\"");
        add(new MockBackReference(at.at(), at.label(), caption));
    }

    @Override public void beginCollection(Occurrence at, int size, boolean expanded) {
        line("COLLECTION", at.at(), "(" + size + ") " + (expanded ? "open" : "folded"));
        depth++;
        pending.push(new Object[]{at, size, expanded});
        open.push(new ArrayList<>());
    }

    @Override public void endCollection(Occurrence at) {
        depth--;
        Object[] begun = pending.pop();
        List<Mock> members = open.pop();
        add(new MockCollection(at.at(), at.label(), (Integer) begun[1], (Boolean) begun[2],
                List.copyOf(members)));
    }

    @Override public void leaf(Occurrence at, Representation representation, Object value) {
        line(representation.name(), at.at(), "\"" + value + "\"");
        add(new MockLeaf(at.at(), at.label(), representation, value));
    }

    @Override public void skip(Occurrence at, SkipReason reason) {
        line("SKIP", at.at(), reason.name());
    }

    @Override public void defer(Occurrence at, DeferReason reason) {
        line("DEFER", at.at().isEmpty() ? "<root>" : at.at(), reason.name());
    }

    private void add(Mock mock) {
        if (open.size() == 1 && depth == 0) root = mock;
        open.peek().add(mock);
    }

    private void line(String kind, String at, String detail) {
        trace.add("  ".repeat(depth) + kind + " " + at + (detail.isEmpty() ? "" : " " + detail));
    }
}
