package objectview.group;

import objectview.Viewable;
import objectview.field.FieldKind;
import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.FieldSet;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
    @SuppressWarnings("unchecked")
    public static FieldSet of(ViewableGroup<?> group, FieldSet additional) {
        if (group == null) throw new IllegalArgumentException("ViewableGroup is required");
        // Computed only when a caller actually lists the fields or reads a field the
        // group itself does not answer: scanning every member's fields on each caption,
        // membership or role read cost 180 ms per read on a 40,000-member root. Memoized
        // for this FieldSet only, so a later FieldSet sees edited member values.
        Map<String, DerivedField>[] memo = new Map[1];
        java.util.function.Supplier<Map<String, DerivedField>> derived = () -> {
            if (memo[0] == null) memo[0] = unanimousMemberFields(group);
            return memo[0];
        };
        FieldSet backing = new FieldSet() {
            @Override public List<FieldRef> fields() {
                Map<String, FieldRef> fields = new LinkedHashMap<>();
                COMMON.forEach(field -> fields.put(field.name(), field));
                if (additional != null) {
                    additional.fields().forEach(field -> {
                        DerivedField unanimous = derived.get().get(field.name());
                        boolean suppliedByMembers = unanimous != null
                                && !present(additional.read(field.name()));
                        fields.putIfAbsent(field.name(), suppliedByMembers
                                ? new MemberDerivedFieldRef(field) : field);
                    });
                }
                derived.get().forEach((name, value) -> fields.putIfAbsent(
                        name, new MemberDerivedFieldRef(value.field())));
                return List.copyOf(fields.values());
            }

            /** The group's own fields answer these lookups; only a name they do not hold
             *  needs the member projection. The default implementations list every
             *  field, which the contract overlay does when the FieldSet is created. */
            @Override public FieldRef field(String name) {
                for (FieldRef field : COMMON) {
                    if (field.name().equals(name)) return field;
                }
                FieldRef own = additional == null ? null : additional.field(name);
                if (own != null) return FieldSet.super.field(name);
                if (contractKey(name)) return null;
                return derived.get().containsKey(name) ? FieldSet.super.field(name) : null;
            }

            @Override public FieldRef displayField() {
                for (FieldRef field : COMMON) {
                    if (field.role() == FieldRole.DISPLAY) return field;
                }
                // A group is never captioned by a value its members happen to share.
                return additional == null ? null : additional.displayField();
            }

            @Override public Object read(String name) {
                return switch (name) {
                    case MEMBERS -> group.getMemberMap();
                    case KEY_REF -> group.getKeyRef();
                    case CHILDREN -> group.getChildrenMap();
                    case PARENT -> group.getParent();
                    case ROLE -> group.getRole().name();
                    default -> {
                        Object authored = additional == null ? null : additional.read(name);
                        if (present(authored)) yield authored;
                        DerivedField unanimous = derived.get().get(name);
                        yield unanimous == null ? authored : unanimous.value();
                    }
                };
            }

            @Override public boolean has(String name) {
                if (COMMON.stream().anyMatch(field -> field.name().equals(name))
                        || additional != null && additional.has(name)) return true;
                return !contractKey(name) && derived.get().containsKey(name);
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

    /** The Viewable contract's own keys, which the contract overlay probes while it is
     *  built; no member field can be one, so answering them needs no member scan. */
    private static boolean contractKey(String name) {
        return objectview.field.ViewableContractFieldSet.IDENTITY_KEY.equals(name)
                || objectview.field.ViewableContractFieldSet.DISPLAY_KEY.equals(name);
    }

    /** Whether this ordinary-looking field is computed from unanimous member values.
     * Persistence skips it and lets the group recompute it from its current members. */
    public static boolean isMemberDerived(FieldRef field) {
        return field instanceof MemberDerivedFieldRef;
    }

    private static Map<String, DerivedField> unanimousMemberFields(
            ViewableGroup<?> group) {
        List<? extends Viewable> members = group.getMembers() == null
                ? List.of() : group.getMembers().stream().filter(Objects::nonNull).toList();
        if (members.isEmpty()) return Map.of();

        Map<String, DerivedField> result = new LinkedHashMap<>();
        FieldSet first = FieldSet.of(members.get(0));
        for (FieldRef candidate : first.fields()) {
            if (!candidate.role().domainValue() || candidate.structural()
                    || common(candidate.name())) continue;
            List<Object> values = new java.util.ArrayList<>(members.size());
            boolean compatible = true;
            for (Viewable member : members) {
                FieldSet fields = FieldSet.of(member);
                FieldRef field = fields.field(candidate.name());
                Object value = field == null ? null : fields.read(candidate.name());
                if (field == null || !compatible(candidate, field) || !present(value)) {
                    compatible = false;
                    break;
                }
                values.add(value);
            }
            if (!compatible) continue;
            Object unanimous = candidate.collection()
                    ? intersection(values) : unanimous(values);
            if (present(unanimous)) {
                result.put(candidate.name(), new DerivedField(candidate, unanimous));
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static boolean common(String name) {
        return COMMON.stream().anyMatch(field -> field.name().equals(name));
    }

    private static boolean compatible(FieldRef left, FieldRef right) {
        return left.kind() == right.kind()
                && left.valueKind() == right.valueKind()
                && left.reference() == right.reference()
                && left.collection() == right.collection()
                && Objects.equals(left.targetType(), right.targetType());
    }

    private static Object unanimous(List<Object> values) {
        if (values.isEmpty()) return null;
        Object first = values.get(0);
        ValueKey key = ValueKey.of(first);
        return values.stream().allMatch(value -> key.equals(ValueKey.of(value)))
                ? first : null;
    }

    private static List<Object> intersection(List<Object> values) {
        if (values.isEmpty()) return List.of();
        List<Object> first = elements(values.get(0));
        Set<ValueKey> common = new LinkedHashSet<>();
        first.stream().map(ValueKey::of).forEach(common::add);
        for (int index = 1; index < values.size() && !common.isEmpty(); index++) {
            Set<ValueKey> next = new LinkedHashSet<>();
            elements(values.get(index)).stream().map(ValueKey::of).forEach(next::add);
            common.retainAll(next);
        }
        List<Object> result = new java.util.ArrayList<>();
        Set<ValueKey> emitted = new LinkedHashSet<>();
        for (Object value : first) {
            ValueKey key = ValueKey.of(value);
            if (common.contains(key) && emitted.add(key)) result.add(value);
        }
        return List.copyOf(result);
    }

    private static List<Object> elements(Object value) {
        if (value instanceof Map<?, ?> map) return new java.util.ArrayList<>(map.values());
        if (value instanceof Collection<?> collection) return new java.util.ArrayList<>(collection);
        if (value != null && value.getClass().isArray()) {
            int size = java.lang.reflect.Array.getLength(value);
            List<Object> result = new java.util.ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                result.add(java.lang.reflect.Array.get(value, index));
            }
            return result;
        }
        return value == null ? List.of() : List.of(value);
    }

    private static boolean present(Object value) {
        if (value == null) return false;
        if (value instanceof CharSequence text) return !text.toString().isBlank();
        if (value instanceof Collection<?> collection) return !collection.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        return !value.getClass().isArray() || java.lang.reflect.Array.getLength(value) > 0;
    }

    private record DerivedField(FieldRef field, Object value) { }

    private record MemberDerivedFieldRef(FieldRef delegate) implements FieldRef {
        @Override public String name() { return delegate.name(); }
        @Override public String label() { return delegate.label(); }
        @Override public FieldRole role() { return delegate.role(); }
        @Override public FieldKind kind() { return delegate.kind(); }
        @Override public FieldKind valueKind() { return delegate.valueKind(); }
        @Override public String typeLabel() { return delegate.typeLabel(); }
        @Override public boolean reference() { return delegate.reference(); }
        @Override public boolean collection() { return delegate.collection(); }
        @Override public String targetType() { return delegate.targetType(); }
        @Override public boolean structural() { return delegate.structural(); }
        @Override public boolean minor() { return delegate.minor(); }
        @Override public boolean inline() { return delegate.inline(); }
        @Override public boolean embedded() { return delegate.embedded(); }
        @Override public boolean link() { return delegate.link(); }
        @Override public String linkText() { return delegate.linkText(); }
        @Override public boolean annotatedReference() {
            return delegate.annotatedReference();
        }
    }

    private record ValueKey(String type, Object value) {
        private static ValueKey of(Object value) {
            if (value instanceof Viewable viewable) {
                String id = viewable.getIdentifier();
                if (id == null || id.isBlank()) {
                    return new ValueKey(viewable.getClass().getName(), viewable);
                }
                return new ValueKey(
                        String.valueOf(viewable.identityTypeName()),
                        id);
            }
            return new ValueKey(value == null ? "" : value.getClass().getName(), value);
        }
    }
}
