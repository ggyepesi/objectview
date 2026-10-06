package objectview.plan;

import objectview.Viewable;
import objectview.field.FieldPath;

/**
 * Receives the executor's decisions and paints them: Swing, JSON, or a mock trace. A
 * sink has no other input and makes no selection, representation or disclosure
 * decision of its own.
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

    /** An object occurrence. {@code caption} is its ticked DISPLAY value, else null;
     * with no caption and an empty body the sink shows the field name only. */
    void beginObject(Occurrence at, Viewable target, String caption, boolean expanded);

    void endObject(Occurrence at);

    /** A scalar reference to an object that has its own card. {@code caption} is the
     * target's ticked DISPLAY value, else null (the sink shows "Open"). */
    void navigation(Occurrence at, Viewable target, String caption);

    /** An object already on the current path. */
    void backReference(Occurrence at, Viewable target, String caption);

    /** {@code label (size)}; members follow only when {@code expanded}. */
    void beginCollection(Occurrence at, int size, boolean expanded);

    void endCollection(Occurrence at);

    /** A text, link or media value. */
    void leaf(Occurrence at, Representation representation, Object value);

    void skip(Occurrence at, SkipReason reason);

    void defer(Occurrence at, DeferReason reason);
}
