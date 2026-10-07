package objectview.plan;

import objectview.field.FieldPath;

/**
 * Receives the executor's complete decisions while it walks their nested structure: a
 * trace, a mock component tree, or any renderer without its own component tree. A sink
 * has no other input and makes no selection, representation, disclosure, navigation or
 * caption decision of its own.
 */
public interface RenderSink {

    /** Where a decision applies. {@code path} is the config path (no member indices);
     * {@code at} is the readable occurrence, e.g. {@code offices[0].position}. */
    record Occurrence(FieldPath path, String label, String at) {
        public static Occurrence root() { return new Occurrence(FieldPath.ROOT, "", ""); }

        public Occurrence field(String name, String label) {
            return new Occurrence(path.append(name), label,
                    at.isEmpty() ? name : at + "." + name);
        }

        public Occurrence member(int index) {
            return new Occurrence(path, label, at + "[" + index + "]");
        }
    }

    enum SkipReason { OFF, ABSENT }

    enum DeferReason { COLLAPSED }

    /** Complete object occurrence, including the representation and DISPLAY occurrence
     * that a component-level test must retain. {@code field} is null only for the root.
     * Keeping the executor's level avoids re-reading fields merely to describe a mock. */
    record ObjectOccurrence(Occurrence at, ObjectPlan.FieldPlan field,
                            Representation representation, RenderExecutor.Level level,
                            Occurrence captionAt, boolean expanded) {}

    void beginObject(ObjectOccurrence object);

    void endObject(ObjectOccurrence object);

    /** A scalar reference to an object that has its own card. The decision's
     * {@code object().caption()} is the target's ticked DISPLAY value, else null (the
     * sink shows "Open"). */
    void navigation(RenderExecutor.Decision decision);

    /** An object already on the current path; its caption as for {@link #navigation}. */
    void backReference(RenderExecutor.Decision decision);

    /** {@code label (size)}; members follow only when {@code expanded}. */
    void beginCollection(RenderExecutor.Decision decision);

    void endCollection(RenderExecutor.Decision decision);

    /** A text, link or media value. */
    void leaf(RenderExecutor.Decision decision);

    void skip(RenderExecutor.Decision decision);

    void defer(Occurrence at, DeferReason reason);
}
