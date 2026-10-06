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
     * singleton media and {@code @Inline} content start open. */
    static boolean initiallyOpen(ObjectPlan.FieldPlan field, Object value) {
        if (!(value instanceof Collection<?> || value instanceof java.util.Map<?, ?>)) {
            return true;
        }
        if (field != null && field.field().embedded()) return true;
        Collection<?> members = ValueShape.members(value);
        return members.size() == 1 && members.iterator().next() instanceof MediaValue;
    }

    /** No reader choices: every occurrence takes its initial state. */
    Disclosure INITIAL = (key, initiallyOpen) -> initiallyOpen;
}
