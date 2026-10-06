package objectview.plan;

import objectview.field.FieldKind;
import objectview.field.FieldPath;
import objectview.field.FieldRef;
import objectview.field.FieldRole;
import objectview.field.ViewableFieldPaths.PathInfo;
import objectview.viewconfig.ViewConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The leaf paths of a literal config: what a flat layout (a table's columns) shows.
 * A ticked field with nothing ticked under it is a leaf, an object field alone
 * included (its column shows the field name, rule 3); a ticked object with ticked
 * children contributes its children instead. Read straight off the ticks, so the
 * columns and the cards agree on what is ticked.
 */
public final class LiteralPaths {

    private LiteralPaths() {}

    public static List<PathInfo> leaves(ViewConfig literal, TypeShape shape) {
        if (!ViewConfigDesugar.isLiteral(literal)) {
            throw new IllegalArgumentException(
                    "not a literal config; desugar it where it enters: " + literal);
        }
        List<PathInfo> out = new ArrayList<>();
        collect(literal, shape, FieldPath.ROOT, "", out);
        return out;
    }

    private static void collect(ViewConfig config, TypeShape shape, FieldPath prefix,
                                String titlePrefix, List<PathInfo> out) {
        for (Map.Entry<String, ViewConfig> ticked : config.getFields().entrySet()) {
            String name = ticked.getKey();
            FieldRef field = shape == null ? null : field(shape, name);
            if (field != null && field.role() == FieldRole.IDENTITY) continue;
            String label = field == null ? name : field.label();
            FieldPath path = prefix.append(name);
            String title = titlePrefix.isEmpty() ? label : titlePrefix + "." + label;
            ViewConfig child = ticked.getValue();
            if (child != null && !child.getFields().isEmpty()) {
                collect(child, shape == null || field == null ? null : shape.nested(field),
                        path, title, out);
            } else {
                out.add(new PathInfo(title, path, null,
                        field == null ? FieldKind.UNKNOWN : field.valueKind(),
                        field == null ? FieldRole.NONE : field.role()));
            }
        }
    }

    private static FieldRef field(TypeShape shape, String name) {
        for (FieldRef field : shape.fields()) {
            if (field.name().equals(name)) return field;
        }
        return null;
    }
}
