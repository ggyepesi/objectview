package objectview.utils;

import java.util.Set;

/**
 * A value type that publishes named, addressable <em>views</em> of itself — so a
 * path resolver (e.g. {@code objectview.field.FieldAccess}) can read {@code date.year}
 * or {@code birthDate.monthDay} generically, without knowing the concrete type.
 *
 * <p>The type OWNS its view vocabulary; the resolver stays type-agnostic (no
 * {@code instanceof FlexibleDate}). This keeps the "types are addressable,
 * transforms operate on typed paths, nothing implicit" principle: extraction is a
 * path (`.year`), not a convention baked into a construct. Lives in {@code aux}
 * (a leaf package) so value types here can implement it without an upward dep.
 */
public interface Addressable {

    /** The value of the named view, or null when this instance can't provide it
     *  (e.g. {@code monthDay} on a year-precision date). */
    Object view(String name);

    /** The views this type offers. */
    Set<String> viewNames();
}
