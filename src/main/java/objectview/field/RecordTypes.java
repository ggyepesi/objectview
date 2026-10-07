package objectview.field;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The declared fields of record types that code builds at runtime — review cards,
 * findings, summaries — and that no domain models. A producer states its type's fields
 * once, by type name; a record of that type reports them as its schema, and a field
 * leading to such a type nests into its declaration. The fields of a type are never
 * read off the values its records hold (#363).
 */
public final class RecordTypes {

    private static final Map<String, FieldSchema> DECLARED = new ConcurrentHashMap<>();

    private RecordTypes() {}

    /** Declares (or redeclares) {@code type}'s fields, in order. */
    public static FieldSchema declare(String type, List<FieldRef> fields) {
        List<FieldRef> declared = List.copyOf(fields);
        FieldSchema schema = () -> declared;
        if (type != null && !type.isBlank()) DECLARED.put(type, schema);
        return schema;
    }

    public static FieldSchema declare(String type, FieldRef... fields) {
        return declare(type, List.of(fields));
    }

    /** The declared fields of {@code type}, or null. */
    public static FieldSchema schema(String type) {
        return type == null ? null : DECLARED.get(type);
    }

    /** A text value. */
    public static FieldRef text(String name) {
        return field(name, FieldKind.TEXT, FieldKind.TEXT, "String", false, false, null);
    }

    /** A number, ordered as one. */
    public static FieldRef number(String name) {
        return field(name, FieldKind.ORDERED, FieldKind.ORDERED, "Number", false, false, null);
    }

    /** A scalar whose kind its value decides: text, a link or a picture. */
    public static FieldRef value(String name) {
        return field(name, FieldKind.UNKNOWN, FieldKind.UNKNOWN, "Object", false, false, null);
    }

    /** A reference to an object of any type. */
    public static FieldRef reference(String name) {
        return field(name, FieldKind.REFERENCE, FieldKind.REFERENCE, "Object", true, false, null);
    }

    /** A collection of objects of any type. */
    public static FieldRef references(String name) {
        return field(name, FieldKind.COLLECTION, FieldKind.REFERENCE, "List", true, true, null);
    }

    /** A record of the declared type {@code type}. */
    public static FieldRef record(String name, String type) {
        return field(name, FieldKind.REFERENCE, FieldKind.REFERENCE, type, true, false, type);
    }

    /** A collection of records of the declared type {@code type}. */
    public static FieldRef records(String name, String type) {
        return field(name, FieldKind.COLLECTION, FieldKind.REFERENCE,
                "List<" + type + ">", true, true, type);
    }

    private static FieldRef field(String name, FieldKind kind, FieldKind valueKind,
                                  String typeLabel, boolean reference, boolean collection,
                                  String target) {
        return FieldRef.described(name, name, FieldRole.NONE, kind, valueKind, typeLabel,
                reference, collection, target, false, false, false, false, false, "", false);
    }
}
