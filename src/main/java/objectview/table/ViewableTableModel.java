package objectview.table;

import objectview.Viewable;
import objectview.field.FieldAccess;
import objectview.field.ViewableFieldPaths;
import objectview.viewconfig.ViewConfig;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Data-only model: rows are Viewables, columns are configured field paths. */
public final class ViewableTableModel extends AbstractTableModel {
    private final BiFunction<Viewable, ViewConfig,
            List<ViewableFieldPaths.FieldPath>> pathResolver;
    private List<Viewable> rows = new ArrayList<>();
    private List<ViewableFieldPaths.FieldPath> columns = List.of();
    private Function<Viewable, ViewConfig> configResolver;

    public ViewableTableModel(
            List<? extends Viewable> rows,
            BiFunction<Viewable, ViewConfig,
                    List<ViewableFieldPaths.FieldPath>> pathResolver) {
        this.pathResolver = pathResolver;
        this.rows.addAll(rows == null ? List.of() : rows);
    }

    public void setViewConfigResolver(Function<Viewable, ViewConfig> resolver) {
        configResolver = resolver;
        rebuildColumns();
    }

    public void setRows(List<? extends Viewable> values) {
        List<Viewable> next = new ArrayList<>(values == null ? List.of() : values);
        boolean sameMembers = sameIdentityMembers(rows, next);
        rows = next;
        if (sameMembers) {
            fireTableDataChanged();
        } else {
            rebuildColumns();
        }
    }

    public List<Viewable> rows() {
        return Collections.unmodifiableList(rows);
    }

    public Viewable row(int index) {
        return rows.get(index);
    }

    public ViewableFieldPaths.FieldPath column(int index) {
        return columns.get(index);
    }

    public int columnIndex(ViewableFieldPaths.FieldPath path) {
        if (path == null) return -1;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).path().equals(path.path())) return i;
        }
        return -1;
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return columns.size(); }
    @Override public String getColumnName(int column) { return columns.get(column).title(); }
    @Override public Class<?> getColumnClass(int columnIndex) { return Object.class; }
    @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        try {
            return FieldAccess.getPathValues(
                    rows.get(rowIndex), columns.get(columnIndex).path());
        } catch (RuntimeException ignored) {
            // A heterogeneous row may not implement a subtype-only column. It is
            // an empty cell, not a broken table.
            return null;
        }
    }

    private void rebuildColumns() {
        Map<String, ViewableFieldPaths.FieldPath> union = new LinkedHashMap<>();
        if (configResolver != null && pathResolver != null) {
            for (Viewable row : rows) {
                ViewConfig config = configResolver.apply(row);
                List<ViewableFieldPaths.FieldPath> paths = pathResolver.apply(row, config);
                if (paths == null) continue;
                for (ViewableFieldPaths.FieldPath path : paths) {
                    if (path != null) union.putIfAbsent(path.dotted(), path);
                }
            }
        }
        List<ViewableFieldPaths.FieldPath> ordered = new ArrayList<>(union.values());
        // The display field reads as the row's title, so it leads: move it to the first
        // column. (displayKey resolves the bound @DisplayField, last-wins if several.)
        if (!rows.isEmpty()) {
            String displayKey = objectview.field.ViewableContractFieldSet.displayKey(
                    asViewableClass(rows.get(0).getClass()));
            for (int i = 1; i < ordered.size(); i++) {
                if (ordered.get(i).dotted().equals(displayKey)) {
                    ordered.add(0, ordered.remove(i));
                    break;
                }
            }
        }
        columns = List.copyOf(ordered);
        fireTableStructureChanged();
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Viewable> asViewableClass(Class<?> type) {
        return (Class<? extends Viewable>) type;
    }

    private static boolean sameIdentityMembers(List<Viewable> left, List<Viewable> right) {
        if (left.size() != right.size()) return false;
        Set<Viewable> members = Collections.newSetFromMap(new IdentityHashMap<>());
        members.addAll(left);
        return members.size() == left.size() && members.containsAll(right);
    }
}
