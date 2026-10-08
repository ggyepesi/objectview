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
 * The paths of a literal config. {@link #leaves}: what a flat layout (a table's
 * columns) shows. {@link #selection}: the value paths a search, sort or quiz key reads.
 * A ticked field with nothing ticked under it is a leaf, an object field alone
 * included (its column shows the field name, rule 3); a ticked object with ticked
 * children contributes its children instead. Read straight off the ticks, so the
 * columns and the cards agree on what is ticked.
 */
public final class LiteralPaths {

    private LiteralPaths() {}

    /**
     * The value paths of a field selection (search, sort, quiz key): its shorthand
     * rewritten by {@link ViewConfigDesugar#selection}, then read straight off the
     * ticks. A ticked field with ticks below it contributes those; a ticked object
     * with nothing ticked below it contributes its caption to what is shown but no
     * value path; any other ticked field is a path. {@code excludeMedia} drops media
     * fields, which cannot be searched or ordered.
     */
    public static List<PathInfo> selection(ViewConfig config, TypeShape shape,
                                           boolean excludeMedia) {
        List<PathInfo> out = new ArrayList<>();
        if (config == null) return out;
        ViewConfig literal = ViewConfigDesugar.isLiteral(config)
                ? config : ViewConfigDesugar.selection(config, shape);
        // A selection that inherits somewhere is read by walking it (#368); each of its
        // paths then carries the walk, every other selection reads along its paths.
        values(literal, InheritedSelection.inherits(literal) ? literal : null,
                shape, FieldPath.ROOT, "", excludeMedia, out);
        java.util.LinkedHashMap<FieldPath, PathInfo> unique = new java.util.LinkedHashMap<>();
        for (PathInfo path : out) unique.putIfAbsent(path.path(), path);
        return List.copyOf(unique.values());
    }

    private static void values(ViewConfig literal, ViewConfig walked, TypeShape shape,
                               FieldPath prefix, String titlePrefix, boolean excludeMedia,
                               List<PathInfo> out) {
        for (Map.Entry<String, ViewConfig> ticked : literal.getFields().entrySet()) {
            String name = ticked.getKey();
            FieldRef field = shape == null ? null : field(shape, name);
            if (excludeMedia && field != null && (field.kind() == FieldKind.MEDIA
                    || field.valueKind() == FieldKind.MEDIA)) continue;
            TypeShape nested = field == null ? null : shape.nested(field);
            FieldPath path = prefix.append(name);
            String label = field == null
                    ? objectview.field.ViewableContractFieldSet.label(name) : field.label();
            String title = titlePrefix.isEmpty() ? label : titlePrefix + "." + label;
            ViewConfig child = ticked.getValue();
            if (child != null && !child.getFields().isEmpty()) {
                values(child, walked, nested, path, title, excludeMedia, out);
            } else if (nested == null && !objectField(field, child)) {
                objectview.field.PathWalk walk = walked == null
                        ? null : InheritedSelection.walk(walked, literal, name);
                out.add(field == null
                        ? new PathInfo(title, path, null, FieldKind.UNKNOWN,
                                objectview.field.ViewableContractFieldSet.DISPLAY_KEY
                                        .equals(name) ? FieldRole.DISPLAY : FieldRole.NONE,
                                walk)
                        : new PathInfo(title, path, shape.javaField(field),
                                field.valueKind(), field.role(), walk));
            }
        }
    }

    /** Whether a ticked field is an object even where its shape could not follow it:
     * its schema says so, or its child config names an object class. */
    private static boolean objectField(FieldRef field, ViewConfig child) {
        if (field != null && (field.reference() || field.embedded()
                || field.kind() == FieldKind.REFERENCE)) return true;
        return child != null && child.getCls() != null
                && objectview.Viewable.class.isAssignableFrom(child.getCls());
    }

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
