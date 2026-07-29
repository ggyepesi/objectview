package objectview.field;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A schema-authoritative view over any {@link FieldSet} backing.
 *
 * <p>The schema supplies stable metadata and declares fields even when their value
 * is absent. The backing still supplies reads/writes, and observed fields omitted
 * by an older/incomplete schema are appended instead of being silently dropped.
 * This makes one contract work for reflected and dynamic objects alike.
 */
public final class SchemaFieldSet implements FieldSet {

    private final FieldSet backing;
    private final FieldSchema schema;

    public SchemaFieldSet(FieldSet backing, FieldSchema schema) {
        this.backing = backing;
        this.schema = schema;
    }

    @Override public List<FieldRef> fields() {
        Map<String, FieldRef> combined = new LinkedHashMap<>();
        for (FieldRef field : schema.fields()) {
            combined.put(field.name(), field);
        }
        for (FieldRef field : backing.fields()) {
            combined.putIfAbsent(field.name(), field);
        }
        return new ArrayList<>(combined.values());
    }

    @Override public Object read(String name) {
        return backing.read(name);
    }

    @Override public boolean has(String name) {
        return schema.field(name) != null || backing.has(name);
    }

    @Override public void write(String name, Object value) {
        backing.write(name, value);
    }
}
