package objectview.table;

import objectview.Viewable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Sparse per-object/per-field cursor state for collection and map cells. The
 * common zero position is not stored, so table memory does not grow by rows ×
 * columns merely because multi-valued fields exist.
 */
final class CellCursorState {
    private final Map<Viewable, Map<String, Integer>> positions =
            new IdentityHashMap<>();

    int index(Viewable row, String path, int size) {
        if (row == null || size <= 0) return 0;
        int stored = positions.getOrDefault(row, Map.of()).getOrDefault(path, 0);
        return Math.max(0, Math.min(size - 1, stored));
    }

    void move(Viewable row, String path, int delta, int size) {
        if (row == null || size <= 1) return;
        set(row, path, Math.floorMod(index(row, path, size) + delta, size));
    }

    void set(Viewable row, String path, int index) {
        if (row == null || path == null) return;
        if (index <= 0) {
            Map<String, Integer> rowPositions = positions.get(row);
            if (rowPositions != null) {
                rowPositions.remove(path);
                if (rowPositions.isEmpty()) positions.remove(row);
            }
            return;
        }
        positions.computeIfAbsent(row, ignored -> new HashMap<>()).put(path, index);
    }

    void retain(Collection<? extends Viewable> rows) {
        Set<Viewable> retained = Collections.newSetFromMap(new IdentityHashMap<>());
        if (rows != null) retained.addAll(rows);
        positions.keySet().removeIf(row -> !retained.contains(row));
    }
}
