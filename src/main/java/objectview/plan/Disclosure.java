package objectview.plan;

import objectview.media.MediaValue;

import java.util.Collection;

/**
 * Whether an object or collection occurrence is open. The reader's toggles are kept by
 * the implementation; the initial state is {@link #initiallyOpen}, the one policy.
 * Disclosure changes timing only, never which fields are ticked.
 */
public interface Disclosure {

    /** The reader's choice for {@code key} (the value's identity), else
     * {@code initiallyOpen}. */
    boolean isExpanded(Object key, boolean initiallyOpen);

    /** Rule 6: a nested object starts open; ordinary collections start folded;
     * singleton media and {@code @Inline} content start open. A field that inherits an
     * ancestor's config starts folded, so each expand opens one level and only the
     * reader's clicks set the depth (#368). */
    static boolean initiallyOpen(ObjectPlan.FieldPlan field, Object value) {
        return initiallyOpen(field, value, false);
    }

    /** {@link #initiallyOpen} for a {@code member} of an open collection: the members
     * of an opened inherited collection are the level that opened, so they start open. */
    static boolean initiallyOpen(ObjectPlan.FieldPlan field, Object value, boolean member) {
        // Explicit inline presentation remains inline even when the recursive field
        // inherits its ancestor's selection. Inheritance decides what is selected;
        // @Inline independently decides that this occurrence starts visible.
        if (field != null && field.field().embedded()) return true;
        if (field != null && field.inherited() && !member) return false;
        if (!(value instanceof Collection<?> || value instanceof java.util.Map<?, ?>)) {
            return true;
        }
        Collection<?> members = ValueShape.members(value);
        return members.size() == 1 && members.iterator().next() instanceof MediaValue;
    }

    /** No reader choices: every occurrence takes its initial state. */
    Disclosure INITIAL = (key, initiallyOpen) -> initiallyOpen;
}
