package objectview.plan;

import objectview.field.FieldRef;
import objectview.viewconfig.ViewConfig;

import java.util.List;

/**
 * What one object level renders and how: its caption field, its ticked body fields in
 * order, and its unticked fields for diagnostics only. Produced by {@link PlanResolver}.
 */
public record ObjectPlan(String logicalType, FieldPlan caption,
                         List<FieldPlan> body, List<FieldRef> unticked) {

    /** One ticked field: its representation and the exact literal config of its value.
     * {@code child} is the config its value renders under; for a field whose type is
     * already on the path it is the ancestor's, and {@code inherited} says so (#368). */
    public record FieldPlan(FieldRef field, Representation representation, ViewConfig child,
                            boolean inherited) {
        public FieldPlan(FieldRef field, Representation representation, ViewConfig child) {
            this(field, representation, child == null ? null : child.effective(),
                    child != null && child.inheritedFrom() != null);
        }
        public String name() { return field.name(); }
        public String label() { return field.label(); }
    }

    /** A readable form, e.g. {@code position ON OBJECT}. */
    public String describe() {
        StringBuilder out = new StringBuilder(logicalType).append('\n');
        if (caption != null) {
            out.append("  ").append(caption.name()).append("  ON  CAPTION\n");
        }
        for (FieldPlan field : body) {
            out.append("  ").append(field.name()).append("  ON  ")
               .append(field.representation()).append('\n');
        }
        for (FieldRef field : unticked) {
            out.append("  ").append(field.name()).append("  OFF\n");
        }
        return out.toString();
    }
}
