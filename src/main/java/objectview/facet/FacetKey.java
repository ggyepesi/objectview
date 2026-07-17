package objectview.facet;

import objectview.Viewable;

/**
 * One value (bucket) produced by a facet for a member.
 *
 * @param name bucket identifier and display name
 * @param ref  the entity the bucket stands for (any {@link Viewable} — for a REFERENCE
 *             facet this is generally a different entity than the member), or null for
 *             a plain value bucket
 */
public record FacetKey(String name, Viewable ref) {

    public boolean isUsable() {
        return name != null && !name.isBlank();
    }

    /** A value bucket (no referenced entity). */
    public static FacetKey of(String name) {
        return new FacetKey(name, null);
    }

    /** A reference bucket: it stands for {@code ref}, named by its display name. */
    public static FacetKey of(Viewable ref) {
        return ref == null ? null : new FacetKey(ref.getDisplayName(), ref);
    }
}
