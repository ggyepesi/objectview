package objectview.plan;

import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.ViewableContractFieldSet;
import objectview.viewconfig.ViewConfig;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The only reader of ViewConfig shorthand (directive 24). Rewrites {@code allFields},
 * {@code allMinorFields}, absent child configs and the DISPLAY alias into plain ticks,
 * once, where a config enters. Everything downstream sees a literal config.
 *
 * <p>A literal config has both flags off at every level. Its field map lists exactly
 * the ticked fields under their real names, and each ticked object field carries its
 * own literal child config, possibly empty. Shorthand is how a default is written, so
 * what it includes is what a new config ticks. In a View an object it includes gets
 * {@link ViewDefaults#implicitObject}; in a field selection (search, sort,
 * quiz key) it gets an empty child, since a nested value enters a key only when the
 * user ticks it. Ticking an object by hand never ticks its children.
 */
public final class ViewConfigDesugar {

    private ViewConfigDesugar() {}

    /** The child config of an object the shorthand includes, given the type path above
     * it ({@code parent}, ending at the level that holds {@code field}). */
    @FunctionalInterface
    interface Implicit {
        ViewConfig child(FieldRef field, TypeShape nested, ConfigChain<ViewConfig> parent);
    }

    /** The literal form of the View config {@code config} against {@code shape}. A null
     * shape keeps only the explicitly ticked fields, since there is nothing to expand
     * against. A field whose type is already on the path inherits that ancestor's config
     * ({@link ConfigChain}); its own ticks, if any were stored, are not the config. */
    public static ViewConfig literal(ViewConfig config, TypeShape shape) {
        return literal(config, shape, true);
    }

    /**
     * The literal View config consumed by a renderer. A config can already be literal
     * (no shorthand flags) and still predate, or have bypassed, the recursion rule: its
     * recursive field then carries an ordinary empty child instead of the ancestor it
     * inherits. This is the one rendering boundary for that distinction. It preserves an
     * already prepared config by identity and rewrites every other one through
     * {@link #literal(ViewConfig, TypeShape)}.
     */
    public static ViewConfig preparedView(ViewConfig config, TypeShape shape) {
        if (config != null && config.isFinitePaths()) {
            return isLiteral(config) ? config : selection(config, shape);
        }
        return prepared(config, shape, null, null)
                ? config : literal(config, shape);
    }

    /** {@link #literal}, with {@code inherit} false for a View-form config whose ticks
     * under a recursive field are finite paths of their own: a field picker's. */
    public static ViewConfig literal(ViewConfig config, TypeShape shape, boolean inherit) {
        ViewConfig out = literal(
                config, shape, ViewDefaults::implicitObject, null, null, inherit);
        if (!inherit) markFinite(out, Collections.newSetFromMap(new IdentityHashMap<>()));
        return out;
    }

    /** The literal form of a field selection (search, sort, quiz key): an object the
     * shorthand includes has nothing ticked under it. A selection inherits only where
     * its editor wrote it; this never adds an inheritance. */
    public static ViewConfig selection(ViewConfig config, TypeShape shape) {
        ViewConfig out = literal(config, shape,
                (field, nested, parent) -> ViewConfig.leaf(), null, null, false);
        markFinite(out, Collections.newSetFromMap(new IdentityHashMap<>()));
        return out;
    }

    /** A finite field selection ready for projection and rendering. Literal editor
     * output is retained by identity; every level is marked so a nested card cannot
     * later reinterpret that finite path as a recursive View. */
    public static ViewConfig preparedSelection(ViewConfig config, TypeShape shape) {
        if (!isLiteral(config)) return selection(config, shape);
        markFinite(config, Collections.newSetFromMap(new IdentityHashMap<>()));
        return config;
    }

    private static void markFinite(ViewConfig config, Set<ViewConfig> seen) {
        if (config == null || !seen.add(config)) return;
        config.setFinitePaths(true);
        if (config.inheritedFrom() != null) markFinite(config.inheritedFrom(), seen);
        for (ViewConfig child : config.getFields().values()) markFinite(child, seen);
    }

    /**
     * The literal form with {@code implicit} deciding the child of an object the
     * shorthand includes. {@code parent} is the type path above this level and
     * {@code via} the field reaching it (both null at the root); {@code inherit} says
     * whether a recursive field is made to inherit here (the View form) — an inheritance
     * already written into {@code config} is kept either way.
     */
    static ViewConfig literal(ViewConfig config, TypeShape shape, Implicit implicit,
                              ConfigChain<ViewConfig> parent, FieldRef via,
                              boolean inherit) {
        return literal(config, shape, implicit, parent, via, inherit,
                new java.util.IdentityHashMap<>());
    }

    private static ViewConfig literal(ViewConfig config, TypeShape shape, Implicit implicit,
                                      ConfigChain<ViewConfig> parent, FieldRef via,
                                      boolean inherit, Map<ViewConfig, ViewConfig> outs) {
        ViewConfig out = header(config);
        if (config == null) return out;
        outs.put(config, out);
        ConfigChain<ViewConfig> chain = parent == null
                ? ConfigChain.root(shape == null ? null : shape.typeName(), out)
                : parent.push(via, out);

        FieldRef display = shape == null ? null : shape.displayField();
        // Explicit ticks first, in the order the user arranged them.
        for (Map.Entry<String, ViewConfig> entry : config.getFields().entrySet()) {
            String name = entry.getKey();
            if (ViewableContractFieldSet.DISPLAY_KEY.equals(name) && display != null) {
                name = display.name();
            }
            if (out.hasField(name)) continue;
            FieldRef field = shape == null ? null : field(shape, name);
            out.addField(name, child(entry.getValue(), field, shape, implicit, chain,
                    inherit, outs));
        }
        // Then what the shorthand includes, in schema order.
        if (shape != null && (config.isAllFields() || config.isAllMinorFields())) {
            for (FieldRef field : shape.fields()) {
                if (out.hasField(field.name()) || !includedByShorthand(config, field)) continue;
                out.addField(field.name(), child(null, field, shape, implicit, chain,
                        inherit, outs));
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

    /** Whether {@code config} is literal and every recursive field already points at
     * the nearest ancestor selected by {@link ConfigChain}. */
    private static boolean prepared(ViewConfig config, TypeShape shape,
                                    ConfigChain<ViewConfig> parent, FieldRef via) {
        if (config == null || config.isAllFields() || config.isAllMinorFields()) return false;
        ConfigChain<ViewConfig> chain = parent == null
                ? ConfigChain.root(shape == null ? null : shape.typeName(), config)
                : parent.push(via, config);
        FieldRef display = shape == null ? null : shape.displayField();
        for (Map.Entry<String, ViewConfig> entry : config.getFields().entrySet()) {
            String name = entry.getKey();
            if (ViewableContractFieldSet.DISPLAY_KEY.equals(name) && display != null
                    && !ViewableContractFieldSet.DISPLAY_KEY.equals(display.name())) {
                return false; // the alias still has to be rewritten to the real field
            }
            ViewConfig child = entry.getValue();
            if (child == null) return false;
            FieldRef field = shape == null ? null : field(shape, name);
            ViewConfig ancestor = chain.inherited(field);
            if (ancestor != null) {
                if (child.inheritedFrom() != ancestor) return false;
                continue;
            }
            if (child.inheritedFrom() != null) return false;
            TypeShape nested = field == null || shape == null ? null : shape.nested(field);
            if (!prepared(child, nested, chain, field)) return false;
        }
        return true;
    }

    /** What "all fields" and "all minor fields" include: every non-structural,
     * non-IDENTITY field, a minor one only with the minor switch. */
    private static boolean includedByShorthand(ViewConfig config, FieldRef field) {
        if (field.structural() || field.role() == FieldRole.IDENTITY) return false;
        return field.minor() ? config.isAllMinorFields() : config.isAllFields();
    }

    private static ViewConfig child(ViewConfig explicit, FieldRef field, TypeShape shape,
                                    Implicit implicit, ConfigChain<ViewConfig> chain,
                                    boolean inherit, Map<ViewConfig, ViewConfig> outs) {
        ViewConfig ancestor = inherit ? chain.inherited(field) : null;
        if (ancestor != null) return ViewConfig.inheriting(ancestor);
        if (explicit != null && explicit.inheritedFrom() != null) {
            ViewConfig mapped = outs.get(explicit.inheritedFrom());
            return ViewConfig.inheriting(mapped != null ? mapped : explicit.inheritedFrom());
        }
        TypeShape nested = field == null || shape == null ? null : shape.nested(field);
        if (explicit == null) return implicit.child(field, nested, chain);
        return literal(explicit, nested, implicit, chain, field, inherit, outs);
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
