package objectview.viewconfig;

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

    private final List<String> paths;
    private final Set<String> hiddenTop;
    private final Function<String, String> typeLabelForPath;

    public PathFieldRowSource(List<String> paths,
                              Set<String> hiddenTop,
                              Function<String, String> typeLabelForPath) {
        this.paths = paths == null ? List.of() : List.copyOf(paths);
        this.hiddenTop = hiddenTop == null ? Set.of() : Set.copyOf(hiddenTop);
        this.typeLabelForPath = typeLabelForPath;
    }

    @Override
    public List<FieldRow> rows(FieldRowContext context) {
        Set<String> real = new LinkedHashSet<>();

        for (String path : paths) {
            if (path == null || path.isBlank()) {
                continue;
            }

            String top = path.split("\\.", 2)[0];
            if (!hiddenTop.contains(top)) {
                real.add(path);
            }
        }

        // Include every prefix so a parent appears directly above descendants.
        Set<String> prefixes = new TreeSet<>();
        for (String path : real) {
            String[] segments = path.split("\\.");
            StringBuilder prefix = new StringBuilder();

            for (int i = 0; i < segments.length; i++) {
                if (i > 0) {
                    prefix.append('.');
                }
                prefix.append(segments[i]);
                prefixes.add(prefix.toString());
            }
        }

        List<FieldRow> result = new ArrayList<>();
        for (String path : prefixes) {
            String[] segments = path.split("\\.");
            result.add(FieldRow.path(
                    segments[segments.length - 1],
                    path,
                    segments.length - 1,
                    !real.contains(path),
                    typeLabelForPath == null
                            ? null
                            : typeLabelForPath.apply(path)));
        }

        return List.copyOf(result);
    }
}
