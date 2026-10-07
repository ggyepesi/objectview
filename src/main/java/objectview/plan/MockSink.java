package objectview.plan;

import objectview.Viewable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A component-level test renderer over the real {@link RenderExecutor}. Unlike a
 * string trace, every mock retains the complete decision (field occurrence,
 * representation, value/target identity and disclosure state). Tests can therefore
 * compare what a renderer was told to paint without re-deriving that information.
 */
public final class MockSink implements RenderSink {

    /** One mock counterpart for every renderer-visible outcome. */
    public sealed interface Mock permits MockObject, MockCollection, MockNavigation,
            MockBackReference, MockLeaf, MockSkip, MockDeferred {
        Occurrence occurrence();
        default String at() { return occurrence().at(); }
        default String label() { return occurrence().label(); }
    }

    public record MockObject(ObjectOccurrence object,
                             List<Mock> children) implements Mock {
        public MockObject { children = List.copyOf(children); }
        @Override public Occurrence occurrence() { return object.at(); }
        public RenderExecutor.Level level() { return object.level(); }
        public Viewable target() { return level().target(); }
        public String caption() { return level().caption(); }
        public Occurrence captionAt() { return object.captionAt(); }
        public Representation representation() { return object.representation(); }
        public boolean expanded() { return object.expanded(); }
    }

    public record MockCollection(RenderExecutor.Decision decision,
                                 List<Mock> members) implements Mock {
        public MockCollection { members = List.copyOf(members); }
        @Override public Occurrence occurrence() { return decision.at(); }
        public int size() { return decision.size(); }
        public boolean expanded() { return decision.open(); }
        public Representation representation() { return decision.representation(); }
    }

    public record MockNavigation(RenderExecutor.Decision decision) implements Mock {
        @Override public Occurrence occurrence() { return decision.at(); }
        public Viewable target() { return decision.object().target(); }
        public String caption() { return decision.object().caption(); }
        public Occurrence captionAt() { return decision.object().captionAt(decision.at()); }
        public Representation representation() { return decision.representation(); }
    }

    public record MockBackReference(RenderExecutor.Decision decision) implements Mock {
        @Override public Occurrence occurrence() { return decision.at(); }
        public Viewable target() { return decision.object().target(); }
        public String caption() { return decision.object().caption(); }
        public Occurrence captionAt() { return decision.object().captionAt(decision.at()); }
        public Representation representation() { return decision.representation(); }
    }

    /** Leaves are distinct mock component types, not a string tagged after the fact. */
    public sealed interface MockLeaf extends Mock permits MockText, MockLink, MockMedia {
        RenderExecutor.Decision decision();
        @Override default Occurrence occurrence() { return decision().at(); }
        default Representation representation() { return decision().representation(); }
        default Object value() { return decision().value(); }
    }

    public record MockText(RenderExecutor.Decision decision) implements MockLeaf {}
    public record MockLink(RenderExecutor.Decision decision) implements MockLeaf {}
    public record MockMedia(RenderExecutor.Decision decision) implements MockLeaf {}

    public record MockSkip(RenderExecutor.Decision decision) implements Mock {
        @Override public Occurrence occurrence() { return decision.at(); }
        public SkipReason reason() { return decision.skip(); }
    }

    public record MockDeferred(Occurrence occurrence, DeferReason reason) implements Mock {}

    private sealed interface Pending permits PendingObject, PendingCollection {}
    private record PendingObject(ObjectOccurrence occurrence) implements Pending {}
    private record PendingCollection(RenderExecutor.Decision decision) implements Pending {}

    private final List<String> trace = new ArrayList<>();
    private final Deque<List<Mock>> open = new ArrayDeque<>();
    private final Deque<Pending> pending = new ArrayDeque<>();
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

    @Override public void beginObject(ObjectOccurrence object) {
        String caption = object.level().caption();
        line("OBJECT", displayAt(object.at()),
                (caption == null ? "" : "caption=\"" + caption + "\" ")
                        + (object.expanded() ? "open" : "folded"));
        depth++;
        pending.push(new PendingObject(object));
        open.push(new ArrayList<>());
    }

    @Override public void endObject(ObjectOccurrence object) {
        depth--;
        PendingObject begun = (PendingObject) pending.pop();
        List<Mock> children = open.pop();
        add(new MockObject(begun.occurrence(), children));
    }

    @Override public void navigation(RenderExecutor.Decision decision) {
        String caption = decision.object().caption();
        line("LINK-TO", decision.at().at(),
                caption == null ? "\"Open\"" : "\"" + caption + "\"");
        add(new MockNavigation(decision));
    }

    @Override public void backReference(RenderExecutor.Decision decision) {
        String caption = decision.object().caption();
        line("BACKREF", decision.at().at(), caption == null ? "" : "\"" + caption + "\"");
        add(new MockBackReference(decision));
    }

    @Override public void beginCollection(RenderExecutor.Decision decision) {
        line("COLLECTION", decision.at().at(),
                "(" + decision.size() + ") " + (decision.open() ? "open" : "folded"));
        depth++;
        pending.push(new PendingCollection(decision));
        open.push(new ArrayList<>());
    }

    @Override public void endCollection(RenderExecutor.Decision decision) {
        depth--;
        PendingCollection begun = (PendingCollection) pending.pop();
        List<Mock> members = open.pop();
        add(new MockCollection(begun.decision(), members));
    }

    @Override public void leaf(RenderExecutor.Decision decision) {
        line(decision.representation().name(), decision.at().at(),
                "\"" + decision.value() + "\"");
        add(switch (decision.representation()) {
            case TEXT -> new MockText(decision);
            case LINK -> new MockLink(decision);
            case MEDIA -> new MockMedia(decision);
            default -> throw new IllegalArgumentException(
                    "Leaf decision has non-leaf representation " + decision.representation());
        });
    }

    @Override public void skip(RenderExecutor.Decision decision) {
        line("SKIP", decision.at().at(), decision.skip().name());
        add(new MockSkip(decision));
    }

    @Override public void defer(Occurrence at, DeferReason reason) {
        line("DEFER", displayAt(at), reason.name());
        add(new MockDeferred(at, reason));
    }

    private void add(Mock mock) {
        if (open.size() == 1 && depth == 0) root = mock;
        open.peek().add(mock);
    }

    private static String displayAt(Occurrence at) {
        return at.at().isEmpty() ? "<root>" : at.at();
    }

    private void line(String kind, String at, String detail) {
        trace.add("  ".repeat(depth) + kind + " " + at
                + (detail.isEmpty() ? "" : " " + detail));
    }
}
