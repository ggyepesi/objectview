package objectview.viewconfig;

import objectview.field.FieldPath;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Row source for a precomputed collection of dotted field paths.
 */
public final class PathFieldRowSource implements FieldRowSource {

    private final List<FieldPath> paths;
    private final Set<String> hiddenTop;
    private final Function<FieldPath, String> typeLabelForPath;

    public PathFieldRowSource(List<FieldPath> paths,
                              Set<String> hiddenTop,
                              Function<FieldPath, String> typeLabelForPath) {
        this.paths = paths == null ? List.of() : List.copyOf(paths);
        this.hiddenTop = hiddenTop == null ? Set.of() : Set.copyOf(hiddenTop);
        this.typeLabelForPath = typeLabelForPath;
    }

    @Override
    public List<FieldRow> rows(FieldRowContext context) {
        Set<FieldPath> real = new LinkedHashSet<>();

        for (FieldPath path : paths) {
            if (path == null || path.isRoot()) {
                continue;
            }

            String top = path.first();
            if (!hiddenTop.contains(top)) {
                real.add(path);
            }
        }

        // Include every prefix so a parent appears directly above descendants.
        Set<FieldPath> prefixes = new TreeSet<>();
        for (FieldPath path : real) {
            FieldPath prefix = FieldPath.ROOT;

            for (String segment : path.segments()) {
                prefix = prefix.append(segment);
                prefixes.add(prefix);
            }
        }

        List<FieldRow> result = new ArrayList<>();
        for (FieldPath path : prefixes) {
            result.add(FieldRow.path(
                    path.leaf(),
                    path,
                    path.size() - 1,
                    !real.contains(path),
                    typeLabelForPath == null
                            ? null
                            : typeLabelForPath.apply(path)));
        }

        return List.copyOf(result);
    }
}
