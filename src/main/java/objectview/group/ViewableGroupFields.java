package objectview.group;

import objectview.Viewable;
import objectview.field.FieldKind;
import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.FieldSet;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The one ordinary ObjectView representation of a {@link ViewableGroup}.
 *
 * <p>A group is already a {@link Viewable}; this field set makes that contract real
 * for every implementation and backing. Members and subgroups are ordinary visible
 * data; only the back-reference and role are structural bookkeeping. A host may append
 * producer/configuration fields, but it cannot redefine the common group fields.
 */
public final class ViewableGroupFields {

    private static final String MEMBERS = "members";
    private static final String KEY_REF = "keyRef";
    private static final String CHILDREN = "children";
    private static final String PARENT = "parent";
    private static final String ROLE = "role";

    private static final List<FieldRef> COMMON = List.of(
            FieldRef.described(MEMBERS, "Members", FieldRole.NONE,
                    FieldKind.COLLECTION, FieldKind.REFERENCE,
                    "Map<String, Viewable>", true, true, null,
                    false, false, false, false, false, "", true),
            FieldRef.described(KEY_REF, "Group entity", FieldRole.NONE,
                    FieldKind.REFERENCE, FieldKind.REFERENCE,
                    "Viewable", true, false, null,
                    false, false, false, false, false, "", true),
            FieldRef.described(CHILDREN, "Subgroups", FieldRole.NONE,
                    FieldKind.COLLECTION, FieldKind.REFERENCE,
                    "Map<String, ViewableGroup>", true, true, "ViewableGroup",
                    false, false, false, false, false, "", true),
            FieldRef.described(PARENT, "Parent group", FieldRole.NONE,
                    FieldKind.REFERENCE, FieldKind.REFERENCE,
                    "ViewableGroup", true, false, "ViewableGroup",
                    true, true, false, false, false, "", true),
            FieldRef.described(ROLE, "Group role", FieldRole.NONE,
                    FieldKind.TEXT, FieldKind.TEXT,
                    "String", false, false, null,
                    true, true, false, false, false, "", false));

    private ViewableGroupFields() { }

    public static FieldSet of(ViewableGroup<?> group) {
        return of(group, null);
    }

    /** Common group fields followed by non-conflicting host fields. */
    public static FieldSet of(ViewableGroup<?> group, FieldSet additional) {
        if (group == null) throw new IllegalArgumentException("ViewableGroup is required");
        FieldSet backing = new FieldSet() {
            @Override public List<FieldRef> fields() {
                Map<String, FieldRef> fields = new LinkedHashMap<>();
                COMMON.forEach(field -> fields.put(field.name(), field));
                if (additional != null) {
                    additional.fields().forEach(
                            field -> fields.putIfAbsent(field.name(), field));
                }
                return List.copyOf(fields.values());
            }

            @Override public Object read(String name) {
                return switch (name) {
                    case MEMBERS -> group.getMemberMap();
                    case KEY_REF -> group.getKeyRef();
                    case CHILDREN -> group.getChildrenMap();
                    case PARENT -> group.getParent();
                    case ROLE -> group.getRole().name();
                    default -> additional == null ? null : additional.read(name);
                };
            }

            @Override public boolean has(String name) {
                return COMMON.stream().anyMatch(field -> field.name().equals(name))
                        || additional != null && additional.has(name);
            }

            @Override public void write(String name, Object value) {
                if (COMMON.stream().anyMatch(field -> field.name().equals(name))) {
                    throw new UnsupportedOperationException(
                            "read-only group field: " + name);
                }
                if (additional == null) {
                    throw new UnsupportedOperationException(
                            "read-only group fields");
                }
                additional.write(name, value);
            }
        };
        return FieldSet.complete(group, backing);
    }
}
