package objectview.plan;

import objectview.field.FieldRef;
import objectview.viewconfig.ViewConfig;

/**
 * The one default for a new View config: every top-level field, minor ones included,
 * and the DISPLAY of each object they lead to, so a reference reads as its target's
 * name rather than "Open"; an {@code @Inline} object also shows its own fields. Nothing
 * deeper is ticked; a field of a type already on the path inherits that ancestor's
 * config instead, folded, so a self-reference opens one level per expand (#368). It is expressed in the shorthand it replaces and desugared at once, so
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
    static ViewConfig implicitObject(FieldRef field, TypeShape nested,
                                     ConfigChain<ViewConfig> parent) {
        if (field != null && field.embedded() && nested != null) {
            ViewConfig everything = ViewConfig.leaf();
            everything.setAllFields(true);
            everything.setAllMinorFields(true);
            return ViewConfigDesugar.literal(everything, nested, ViewDefaults::display,
                    parent, field, true);
        }
        return display(field, nested, parent);
    }

    /** The DISPLAY of the object {@code field} leads to: its declared type's DISPLAY,
     * or the contract alias when the target type is not declared (it may be any
     * object). */
    private static ViewConfig display(FieldRef field, TypeShape nested,
                                      ConfigChain<ViewConfig> parent) {
        if (nested == null && field != null && (field.reference()
                || field.kind() == objectview.field.FieldKind.REFERENCE
                || field.valueKind() == objectview.field.FieldKind.REFERENCE)) {
            return anyObjectDisplay();
        }
        return displayOnly(nested);
    }

    private static ViewConfig displayOnly(TypeShape nested) {
        ViewConfig child = ViewConfig.leaf();
        FieldRef display = nested == null ? null : nested.displayField();
        if (display != null) child.addField(display.name(), ViewConfig.leaf());
        return child;
    }

    /** The child of a reference whose target type is not declared (it may be any
     * object): every object has a DISPLAY, named by the contract alias. */
    private static ViewConfig anyObjectDisplay() {
        ViewConfig child = ViewConfig.leaf();
        child.addField(objectview.field.ViewableContractFieldSet.DISPLAY_KEY, ViewConfig.leaf());
        return child;
    }
}
