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

    /** Rule 6 (#398): each expand opens exactly one level. Below an open object its
     * plain values show, and every nested object and collection starts folded under its
     * caption or field name. Opening a collection shows its members folded under their
     * captions; a member with no caption has nothing to say while folded, so it starts
     * open. Singleton media starts open: it is content, not structure. {@code @Inline}
     * decides placement (in place rather than a reference chip), never this. */
    static boolean initiallyOpen(Object value) {
        return initiallyOpen(value, false, true);
    }

    /** {@link #initiallyOpen} for an object occurrence: a {@code member} of an open
     * collection, and whether it has a caption to be folded under. */
    static boolean initiallyOpen(Object value, boolean member, boolean captioned) {
        if (value instanceof Collection<?> || value instanceof java.util.Map<?, ?>) {
            Collection<?> members = ValueShape.members(value);
            return members.size() == 1 && members.iterator().next() instanceof MediaValue;
        }
        return member && !captioned;
    }

    /** A view's own setting (#398): an occurrence at {@code path} opens together with
     *  its owner — e.g. the query log opens a run's step list with the run. An input to
     *  {@link #initiallyOpen}, never an exception inside it. */
    default boolean opensWithOwner(objectview.field.FieldPath path) {
        return false;
    }

    /** No reader choices: every occurrence takes its initial state. */
    Disclosure INITIAL = (key, initiallyOpen) -> initiallyOpen;
}
