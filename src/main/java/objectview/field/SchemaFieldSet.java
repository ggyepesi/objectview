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

    @Override public FieldRef displayField() {
        for (FieldRef field : schema.fields()) {
            if (field.role() == FieldRole.DISPLAY) return field;
        }
        return backing.displayField();
    }

    @Override public List<FieldRef> fields() {
        Map<String, FieldRef> combined = new LinkedHashMap<>();
        FieldRef schemaDisplay = null;
        for (FieldRef field : schema.fields()) {
            combined.put(field.name(), field);
            if (field.role() == FieldRole.DISPLAY) schemaDisplay = field;
        }
        for (FieldRef field : backing.fields()) {
            // An authoritative schema that binds DISPLAY replaces an adapter's
            // storage/fallback display field. Keeping both made displayField() return
            // the schema answer while displayKey()/render planning picked the later
            // backing answer — two captions for one object and, for linked captions,
            // a silent loss of the link.
            if (schemaDisplay != null && field.role() == FieldRole.DISPLAY
                    && !field.name().equals(schemaDisplay.name())) continue;
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
