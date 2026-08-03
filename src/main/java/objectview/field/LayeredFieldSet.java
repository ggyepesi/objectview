package objectview.field;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** A primary backing plus selected fields from a secondary backing. */
final class LayeredFieldSet implements FieldSet {
    private final FieldSet primary;
    private final FieldSet secondary;
    private final Predicate<FieldRef> includeSecondary;
    private final boolean secondaryMetadataWins;

    LayeredFieldSet(FieldSet primary, FieldSet secondary,
                    Predicate<FieldRef> includeSecondary) {
        this(primary, secondary, includeSecondary, true);
    }

    LayeredFieldSet(FieldSet primary, FieldSet secondary,
                    Predicate<FieldRef> includeSecondary,
                    boolean secondaryMetadataWins) {
        this.primary = primary;
        this.secondary = secondary;
        this.includeSecondary = includeSecondary;
        this.secondaryMetadataWins = secondaryMetadataWins;
    }

    @Override public List<FieldRef> fields() {
        Map<String, FieldRef> combined = new LinkedHashMap<>();
        primary.fields().forEach(field -> combined.put(field.name(), field));
        // A declared adapter field owns its annotations/render semantics even when
        // a persisted dynamic map supplies the value under the same name.
        secondary.fields().stream().filter(includeSecondary)
                .forEach(field -> {
                    if (secondaryMetadataWins) combined.put(field.name(), field);
                    else combined.putIfAbsent(field.name(), field);
                });
        return new ArrayList<>(combined.values());
    }

    @Override public Object read(String name) {
        if (primary.has(name)) return primary.read(name);
        FieldRef field = secondary.field(name);
        return field != null && includeSecondary.test(field)
                ? secondary.read(name) : null;
    }

    @Override public boolean has(String name) {
        if (primary.has(name)) return true;
        FieldRef field = secondary.field(name);
        return field != null && includeSecondary.test(field);
    }

    @Override public void write(String name, Object value) {
        FieldRef field = secondary.field(name);
        if (!primary.has(name) && field != null && includeSecondary.test(field)) {
            secondary.write(name, value);
        } else {
            primary.write(name, value);
        }
    }
}
