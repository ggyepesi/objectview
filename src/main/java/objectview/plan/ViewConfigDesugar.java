package objectview.plan;

import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.ViewableContractFieldSet;
import objectview.viewconfig.ConfiguredFieldSelection;
import objectview.viewconfig.ViewConfig;

import java.util.Map;
import java.util.function.Function;

/**
 * The only reader of ViewConfig shorthand (directive 24). Rewrites {@code allFields},
 * {@code allMinorFields}, absent child configs and the DISPLAY alias into plain ticks,
 * once, where a config enters. Everything downstream sees a literal config.
 *
 * <p>A literal config has both flags off at every level. Its field map lists exactly
 * the ticked fields under their real names, and each ticked object field carries its
 * own literal child config, possibly empty. Shorthand is how a default is written, so
 * what it includes is what a new config ticks. In a View an object it includes gets
 * {@link ViewDefaults#implicitObject}, its DISPLAY; in a field selection (search, sort,
 * quiz key) it gets an empty child, since a nested value enters a key only when the
 * user ticks it. Ticking an object by hand never ticks its children.
 */
public final class ViewConfigDesugar {

    private ViewConfigDesugar() {}

    /** The literal form of the View config {@code config} against {@code shape}. A null
     * shape keeps only the explicitly ticked fields, since there is nothing to expand
     * against. */
    public static ViewConfig literal(ViewConfig config, TypeShape shape) {
        return literal(config, shape, ViewDefaults::implicitObject);
    }

    /** The literal form of a field selection (search, sort, quiz key): an object the
     * shorthand includes has nothing ticked under it. */
    public static ViewConfig selection(ViewConfig config, TypeShape shape) {
        return literal(config, shape, nested -> ViewConfig.leaf());
    }

    private static ViewConfig literal(ViewConfig config, TypeShape shape,
                                      Function<TypeShape, ViewConfig> implicit) {
        ViewConfig out = header(config);
        if (config == null) return out;

        FieldRef display = shape == null ? null : shape.displayField();
        // Explicit ticks first, in the order the user arranged them.
        for (Map.Entry<String, ViewConfig> entry : config.getFields().entrySet()) {
            String name = entry.getKey();
            if (ViewableContractFieldSet.DISPLAY_KEY.equals(name) && display != null) {
                name = display.name();
            }
            if (out.hasField(name)) continue;
            FieldRef field = shape == null ? null : field(shape, name);
            out.addField(name, child(entry.getValue(), field, shape, implicit));
        }
        // Then what the shorthand includes, in schema order.
        if (shape != null && (config.isAllFields() || config.isAllMinorFields())) {
            for (FieldRef field : shape.fields()) {
                if (out.hasField(field.name()) || !includedByShorthand(config, field)) continue;
                out.addField(field.name(), child(null, field, shape, implicit));
            }
        }
        for (Map.Entry<String, ViewConfig> remembered
                : config.getRememberedFields().entrySet()) {
            out.rememberField(remembered.getKey(), remembered.getValue().copy());
        }
        return out;
    }

    /** Whether {@code config} is already literal at every level: no shorthand flag
     * anywhere. (The DISPLAY alias is mapped by {@link #literal} where a shape names the
     * real field; without one, the alias IS the field.) */
    public static boolean isLiteral(ViewConfig config) {
        if (config == null) return true;
        if (config.isAllFields() || config.isAllMinorFields()) return false;
        for (Map.Entry<String, ViewConfig> entry : config.getFields().entrySet()) {
            if (!isLiteral(entry.getValue())) return false;
        }
        return true;
    }

    private static boolean includedByShorthand(ViewConfig config, FieldRef field) {
        if (field.structural() || field.role() == FieldRole.IDENTITY) return false;
        return ConfiguredFieldSelection.selected(
                field, config.isAllFields(), config.isAllMinorFields(), java.util.Set.of());
    }

    private static ViewConfig child(ViewConfig explicit, FieldRef field, TypeShape shape,
                                    Function<TypeShape, ViewConfig> implicit) {
        TypeShape nested = field == null || shape == null ? null : shape.nested(field);
        if (explicit == null) return implicit.apply(nested);
        return literal(explicit, nested, implicit);
    }

    private static FieldRef field(TypeShape shape, String name) {
        for (FieldRef field : shape.fields()) {
            if (field.name().equals(name)) return field;
        }
        return null;
    }

    /** A config carrying {@code source}'s presentation flags and no ticks. */
    private static ViewConfig header(ViewConfig source) {
        ViewConfig out = ViewConfig.leaf();
        if (source == null) return out;
        out.setCls(source.getCls());
        out.setAddListener(source.isAddListener());
        out.setThumb(source.isThumb());
        out.setBlurImages(source.isBlurImages());
        out.setAnswerType(source.getAnswerType());
        // "All minor fields" also meant "show the minor-fields switch on". The ticks are
        // expanded below; the switch keeps its own, already existing, setting.
        out.minorFieldsVisible(source.minorFieldsVisible() != null
                ? source.minorFieldsVisible()
                : source.isAllMinorFields() ? Boolean.TRUE : null);
        return out;
    }
}
