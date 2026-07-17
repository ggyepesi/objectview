package objectview.facet;

import objectview.Viewable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Declares one grouping dimension over members of type {@code T}. A facet maps each
 * member to zero or more bucket {@link FacetKey}s:
 * <ul>
 *   <li>a scalar field -> one key (its value);</li>
 *   <li>a collection/map field -> several keys;</li>
 *   <li>a reference field -> keys carrying the referenced entity (a different type
 *       than {@code T} in general), so a bucket can show that entity's card;</li>
 *   <li>a derived/mapped facet -> whatever the supplied function returns.</li>
 * </ul>
 *
 * @param <T>   member type
 * @param label display name of the dimension
 * @param keys  function producing zero or more keys for one member
 */
public record Facet<T extends Viewable>(
        String label,
        Function<T, ? extends Collection<FacetKey>> keys) {

    public Facet {
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(keys, "keys");
    }

    /** Facet by a field, labelled by the field name. */
    public static <T extends Viewable> Facet<T> field(String fieldName) {
        return field(fieldName, fieldName);
    }

    /** Facet by a (value) field, with a display label. */
    public static <T extends Viewable> Facet<T> field(String fieldName, String label) {
        return new Facet<>(label, q -> values(FacetKeys.fromField(q, fieldName)));
    }

    /** A derived facet: value keys computed from the member (e.g. a predicate). */
    public static <T extends Viewable> Facet<T> derived(String label, Function<T, List<String>> keys) {
        return new Facet<>(label, q -> values(keys.apply(q)));
    }

    /** A facet derived by mapping another field's value(s) — e.g. Country from a State
     *  field. Each key is passed through {@code map}; null/blank dropped, duplicates
     *  collapsed. */
    public static <T extends Viewable> Facet<T> mapped(
            String label, String fieldName, Function<String, String> map) {
        return new Facet<>(label, q -> values(
                FacetKeys.fromField(q, fieldName).stream()
                        .map(map)
                        .filter(s -> s != null && !s.isBlank())
                        .distinct()
                        .toList()));
    }

    /** Facet by a reference field, labelled by the field name. */
    public static <T extends Viewable> Facet<T> reference(String fieldName) {
        return reference(fieldName, fieldName);
    }

    /** Facet by a field whose value(s) are {@link Viewable} references — the member is
     *  bucketed under each referenced entity, and the bucket carries that entity. */
    public static <T extends Viewable> Facet<T> reference(String fieldName, String label) {
        return new Facet<>(label, q ->
                FacetKeys.refsFromField(q, fieldName).stream()
                        .map(FacetKey::of)
                        .filter(k -> k != null && k.isUsable())
                        .toList());
    }

    /** Two-bucket facet by whether a field is PRESENT on the member (present / missing),
     *  surfacing an EXPECTED field's coverage gap. */
    public static <T extends Viewable> Facet<T> presence(String fieldName, String label) {
        return new Facet<>(label, q -> values(List.of(
                FacetKeys.fromField(q, fieldName).stream()
                        .anyMatch(s -> s != null && !s.isBlank())
                        ? "present" : "missing")));
    }

    private static Collection<FacetKey> values(List<String> names) {
        return names.stream().map(FacetKey::of).filter(FacetKey::isUsable).toList();
    }
}
