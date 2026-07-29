package objectview.field;

import java.util.List;

/**
 * Authoritative field metadata for a type — names, kinds, cardinality, type labels —
 * independent of any one instance's values. It is applied equally to reflected and
 * dynamic representations through {@link SchemaFieldSet}, so changing the backing
 * cannot change cardinality, nested targets or structural roles. Without a schema,
 * a backing falls back to its own declared fields or observed values.
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
