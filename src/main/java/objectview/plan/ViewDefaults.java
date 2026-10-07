package objectview.plan;

import objectview.field.FieldRef;
import objectview.viewconfig.ViewConfig;

/**
 * The one default for a new View config: every top-level field, minor ones included,
 * and the DISPLAY of each object they lead to, so a reference reads as its target's
 * name rather than "Open"; an {@code @Inline} object also shows its own fields. Nothing
 * deeper is ticked, so a self-reference never pulls in another graph. It is expressed in the shorthand it replaces and desugared at once, so
 * the default is literal from the start; the DISPLAY ticks are ordinary ticks a reader
 * can untick. Only a default ticks them: an object ticked by hand ticks nothing under it.
 */
public final class ViewDefaults {

    private ViewDefaults() {}

    public static ViewConfig newView(TypeShape shape) {
        ViewConfig everything = ViewConfig.leaf();
        everything.setAllFields(true);
        everything.setAllMinorFields(true);
        return ViewConfigDesugar.literal(everything, shape);
    }

    /** The child config of an object a default includes. A referenced object: its
     * DISPLAY alone, or nothing when its shape is unknown or has no DISPLAY. An
     * {@code @Inline} object is part of its owner and is shown in place, so its own
     * fields are ticked one level deep (agreed 2026-10-07); an object below it gets its
     * DISPLAY alone, so the default stays finite. */
    static ViewConfig implicitObject(FieldRef field, TypeShape nested) {
        if (field != null && field.embedded() && nested != null) {
            ViewConfig everything = ViewConfig.leaf();
            everything.setAllFields(true);
            everything.setAllMinorFields(true);
            return ViewConfigDesugar.literal(everything, nested,
                    (inner, innerShape) -> displayOnly(innerShape));
        }
        return displayOnly(nested);
    }

    private static ViewConfig displayOnly(TypeShape nested) {
        ViewConfig child = ViewConfig.leaf();
        FieldRef display = nested == null ? null : nested.displayField();
        if (display != null) child.addField(display.name(), ViewConfig.leaf());
        return child;
    }
}
