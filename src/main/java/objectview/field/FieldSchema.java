package objectview.field;

import java.util.List;

/**
 * Authoritative field metadata for a type — names, kinds, cardinality, type labels —
 * independent of any one instance's values. It exists for the DYNAMIC representation,
 * where types can't be read reliably from values: a null value has no type, and a
 * one-element collection looks like a scalar. A reflected object is self-describing
 * (its declared field types), so it needs no schema; a {@link DynamicFieldSet} takes
 * one optionally and falls back to value inference when it's absent.
 *
 * <p>Suppliers: {@code ProductSchema}/{@code ProductClass} (the compiled model) adapt
 * to this; see #87. Functional — {@link #fields} is the single method.
 */
@FunctionalInterface
public interface FieldSchema {

    /** The declared fields, in a stable order (complete — includes fields that are
     *  null/absent on any given instance). */
    List<FieldRef> fields();

    /** Authoritative metadata for {@code name}, or null when the schema doesn't
     *  describe it (the caller falls back to value inference). */
    default FieldRef field(String name) {
        for (FieldRef f : fields()) {
            if (f.name().equals(name)) {
                return f;
            }
        }
        return null;
    }
}
