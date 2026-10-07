package objectview.plan;

import objectview.field.FieldKind;
import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.FieldSet;
import objectview.viewconfig.ViewConfig;

import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compiles a literal config and one object's fields into its {@link ObjectPlan}. The
 * only owner of how a ticked field is represented. It refuses a config that still
 * carries shorthand: desugaring is {@link ViewConfigDesugar}'s job, at the boundary.
 */
public final class PlanResolver {

    // A render context can outlive many literal configs produced by editor changes.
    // ViewConfig uses identity equality, so a weak map preserves the original cache key
    // rule without retaining every obsolete config for the lifetime of the context.
    private final Map<ViewConfig, Map<String, ObjectPlan>> memo = new WeakHashMap<>();

    /** The plan for an object of {@code logicalType} whose fields are {@code fields}.
     * Plans are shared per (config, logical type) when the fields come from a schema. */
    public ObjectPlan resolve(ViewConfig literal, FieldSet fields, String logicalType,
                              boolean schemaBacked) {
        if (!ViewConfigDesugar.isLiteral(literal)) {
            throw new IllegalArgumentException(
                    "not a literal config; desugar it where it enters: " + literal);
        }
        if (!schemaBacked) return compile(literal, fields, logicalType);
        synchronized (memo) {
            return memo.computeIfAbsent(literal, ignored -> new java.util.HashMap<>())
                       .computeIfAbsent(logicalType, type -> compile(literal, fields, type));
        }
    }

    private static ObjectPlan compile(ViewConfig literal, FieldSet fields, String logicalType) {
        ObjectPlan.FieldPlan caption = null;
        List<ObjectPlan.FieldPlan> body = new ArrayList<>();
        for (Map.Entry<String, ViewConfig> ticked : literal.getFields().entrySet()) {
            FieldRef field = fields.field(ticked.getKey());
            if (field == null) {
                // Ticked but not described here: its value decides its shape.
                field = FieldRef.of(ticked.getKey(), FieldKind.UNKNOWN, null,
                        false, false, false);
            }
            if (field.role() == FieldRole.IDENTITY) continue;
            ObjectPlan.FieldPlan plan = new ObjectPlan.FieldPlan(
                    field, representation(field), ticked.getValue());
            if (plan.representation() == Representation.CAPTION) {
                caption = plan;
            } else {
                body.add(plan);
            }
        }
        List<FieldRef> unticked = new ArrayList<>();
        for (FieldRef field : fields.fields()) {
            if (field.role() == FieldRole.IDENTITY) continue;   // never a body field
            if (!literal.hasField(field.name())) unticked.add(field);
        }
        return new ObjectPlan(logicalType, caption, List.copyOf(body), List.copyOf(unticked));
    }

    /** The one place a field's representation is chosen from its schema hints. */
    public static Representation representation(FieldRef field) {
        if (field.role() == FieldRole.DISPLAY) return Representation.CAPTION;
        if (field.collection() || field.kind() == FieldKind.COLLECTION) {
            return Representation.COLLECTION;
        }
        if (field.embedded()) return Representation.OBJECT;
        if (field.reference() || field.annotatedReference()
                || field.kind() == FieldKind.REFERENCE) {
            return Representation.REFERENCE;
        }
        if (field.kind() == FieldKind.MEDIA) return Representation.MEDIA;
        if (field.link()) return Representation.LINK;
        if (field.kind() == FieldKind.UNKNOWN) return Representation.VALUE;
        return Representation.TEXT;
    }
}
