package objectview.plan;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.DynamicFields;
import objectview.field.FieldKind;
import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.FieldSchema;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * History's shape on one dynamic carrier class, as {@code WikidataDynamicObject} is:
 * Person, OfficeHolding and Position differ only by logical type. An office holding's
 * display label is its holder's name, as in the real data. Every value read goes
 * through a probe, so a test can assert what was never read.
 */
final class HistoryShape {

    /** Reads, as {@code Type:identifier.field}, in order. */
    final List<String> reads = new ArrayList<>();

    final Thing wigmund;
    final Thing kingship;
    final Thing crown;
    final Thing kingOfMercia;
    final Thing monarch;

    HistoryShape() {
        wigmund = new Thing("Person", "Wigmund of Mercia");
        kingOfMercia = new Thing("Position", "King of Mercia");
        monarch = new Thing("Position", "monarch");
        kingOfMercia.put("country", "Mercia");
        kingOfMercia.put("superClasses", new ArrayList<>(List.of(monarch)));
        monarch.put("superClasses", new ArrayList<>());
        kingship = office("Wigmund's kingship", kingOfMercia, "840");
        crown = office("Wigmund's crown", monarch, "839");
        wigmund.put("offices", new ArrayList<>(List.of(kingship, crown)));
    }

    private Thing office(String id, Thing position, String start) {
        Thing office = new Thing("OfficeHolding", id);
        office.put("label", "Wigmund of Mercia");   // the data labels a holding by its holder
        office.put("source", wigmund);
        office.put("position", position);
        office.put("startDate", start);
        return office;
    }

    /** The authoritative schema of an object: its logical type's. */
    static FieldSchema schema(Viewable value) {
        return schemaOf(value.typeName());
    }

    /** The authoritative schema of each logical type, as the domain supplies it. */
    static FieldSchema schemaOf(String type) {
        return switch (type) {
            case "Person" -> () -> List.of(
                    display("name"),
                    objects("offices", "OfficeHolding"),
                    text("epithet"));
            case "OfficeHolding" -> () -> List.of(
                    display("label"),
                    object("source", "Person"),
                    object("position", "Position"),
                    text("startDate"),
                    text("endDate"));
            case "Position" -> () -> List.of(
                    display("name"),
                    text("country"),
                    objects("superClasses", "Position"));
            default -> null;
        };
    }

    TypeShape shape(Thing sample) {
        return TypeShape.of(sample, schema(sample), HistoryShape::schemaOf);
    }

    private static FieldRef display(String name) {
        return FieldRef.described(name, "Display label", FieldRole.DISPLAY,
                FieldKind.TEXT, FieldKind.TEXT, "String", false, false, null,
                false, false, false, false, false, "", false);
    }

    private static FieldRef text(String name) {
        return FieldRef.described(name, name, FieldRole.NONE,
                FieldKind.TEXT, FieldKind.TEXT, "String", false, false, null,
                false, false, false, false, false, "", false);
    }

    private static FieldRef object(String name, String type) {
        return FieldRef.described(name, name, FieldRole.NONE,
                FieldKind.REFERENCE, FieldKind.REFERENCE, type, true, false, type,
                false, false, false, false, false, "", false);
    }

    private static FieldRef objects(String name, String type) {
        return FieldRef.described(name, name, FieldRole.NONE,
                FieldKind.COLLECTION, FieldKind.REFERENCE, "List<" + type + ">", true, true,
                type, false, false, false, false, false, "", false);
    }

    /** One carrier class for every logical type, its values behind a read probe. */
    final class Thing extends ViewableAdapter implements DynamicFields {
        private final String type;
        private final String id;
        private final Map<String, Object> values = new LinkedHashMap<>() {
            @Override public Object get(Object key) {
                reads.add(type + ":" + id + "." + key);
                return super.get(key);
            }
        };

        Thing(String type, String id) {
            this.type = type;
            this.id = id;
            values.put(type.equals("OfficeHolding") ? "label" : "name", id);
        }

        void put(String field, Object value) { values.put(field, value); }

        @Override public Map<String, Object> dynamicFieldValues() { return values; }
        @Override public FieldSchema dynamicFieldSchema() { return HistoryShape.schema(this); }
        @Override public String typeName() { return type; }
        @Override public String getIdentifier() { return id; }
        /** Deliberately different from any DISPLAY value: a fallback would show. */
        @Override public String getDisplayName() { return "getDisplayName(" + id + ")"; }
        @Override public String toString() { return type + ":" + id; }
    }
}
