package objectview.table;

import objectview.Viewable;
import objectview.annotations.Link;
import objectview.field.ViewableFieldPaths;
import objectview.media.MediaValue;
import objectview.utils.BrowserLauncher;
import objectview.viewconfig.ViewConfig;
import objectview.virtual.ConfigurableVirtualizedContainer;
import objectview.virtual.SearchNavigableContainer;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A lightweight JTable presentation of Viewables. JTable reuses one renderer for
 * all visible cells; the model stores raw values, never Swing components.
 */
public final class ViewableTable extends JTable
        implements ConfigurableVirtualizedContainer, SearchNavigableContainer {
    private static final int NAV_WIDTH = 82;
    private static final Color SEARCH_HIT = new Color(255, 188, 120);

    private final ViewableTableModel viewableModel;
    private final CellCursorState cursors = new CellCursorState();
    private final Consumer<Object> selectionListener;
    private int searchRow = -1;
    private int searchColumn = -1;

    public ViewableTable(
            List<? extends Viewable> rows,
            BiFunction<Viewable, ViewConfig,
                    List<ViewableFieldPaths.FieldPath>> pathResolver,
            Consumer<Object> selectionListener) {
        super(new ViewableTableModel(rows, pathResolver));
        this.viewableModel = (ViewableTableModel) getModel();
        this.selectionListener = selectionListener;

        setAutoCreateRowSorter(false); // SearchPanel owns the shared sort semantics.
        setAutoResizeMode(AUTO_RESIZE_OFF);
        setRowHeight(38);
        setShowVerticalLines(true);
        setGridColor(new Color(225, 225, 225));
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setDefaultRenderer(Object.class, new ValueCellRenderer());
        getTableHeader().setReorderingAllowed(true);
        addMouseListener(new CellMouseHandler());
    }

    @Override
    public void setViewConfigResolver(Function<Viewable, ViewConfig> resolver) {
        viewableModel.setViewConfigResolver(resolver);
        configureColumnWidths();
    }

    @Override public List<Viewable> items() { return viewableModel.rows(); }

    @Override
    public Viewable topVisibleItem() {
        if (getRowCount() == 0) return null;
        int row = rowAtPoint(new Point(0, getVisibleRect().y));
        return viewableModel.row(row < 0 ? 0 : row);
    }

    @Override
    public javax.swing.JComponent navigateToTop(Viewable item) {
        int row = identityRow(item);
        if (row < 0) return null;
        Rectangle cell = getCellRect(row, Math.max(0, getSelectedColumn()), true);
        scrollRectToVisible(cell);
        setRowSelectionInterval(row, row);
        return this;
    }

    @Override
    public void setItems(List<Viewable> orderedItems) {
        viewableModel.setRows(orderedItems);
        cursors.retain(orderedItems);
        searchRow = -1;
        searchColumn = -1;
        configureColumnWidths();
    }

    @Override
    public javax.swing.JComponent revealSearchHit(
            Viewable item,
            ViewableFieldPaths.FieldPath fieldPath,
            List<String> queryTokens) {
        int row = identityRow(item);
        int modelColumn = viewableModel.columnIndex(fieldPath);
        if (row < 0) return null;
        if (modelColumn < 0) modelColumn = getColumnCount() == 0 ? -1 : 0;
        int column = modelColumn < 0 ? -1 : convertColumnIndexToView(modelColumn);

        if (column >= 0) {
            Object raw = getValueAt(row, column);
            List<CellEntry> entries = entries(raw);
            for (int i = 0; i < entries.size(); i++) {
                if (matches(entries.get(i), queryTokens)) {
                    cursors.set(item, viewableModel.column(modelColumn).dotted(), i);
                    break;
                }
            }
            searchRow = row;
            searchColumn = column;
            setRowSelectionInterval(row, row);
            setColumnSelectionInterval(column, column);
            scrollRectToVisible(getCellRect(row, column, true));
        }
        repaint();
        return this;
    }

    @Override
    public void clearSearchHighlight() {
        searchRow = -1;
        searchColumn = -1;
        repaint();
    }

    public ViewableTableModel viewableModel() { return viewableModel; }

    private int identityRow(Viewable item) {
        for (int i = 0; i < viewableModel.getRowCount(); i++) {
            if (viewableModel.row(i) == item) return i;
        }
        return -1;
    }

    private void configureColumnWidths() {
        for (int i = 0; i < getColumnModel().getColumnCount(); i++) {
            getColumnModel().getColumn(i).setPreferredWidth(190);
            getColumnModel().getColumn(i).setMinWidth(80);
        }
    }

    private CellEntry selectedEntry(int row, int column, Object raw) {
        List<CellEntry> values = entries(raw);
        if (values.isEmpty()) return new CellEntry(null, null);
        Viewable item = viewableModel.row(row);
        String path = viewableModel.column(modelColumn(column)).dotted();
        return values.get(cursors.index(item, path, values.size()));
    }

    private static List<CellEntry> entries(Object raw) {
        List<CellEntry> result = new ArrayList<>();
        if (raw instanceof Map<?, ?> map) {
            map.forEach((key, value) -> result.add(new CellEntry(key, value)));
        } else if (raw instanceof Collection<?> collection) {
            collection.forEach(value -> result.add(new CellEntry(null, value)));
        } else if (raw != null && raw.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(raw); i++) {
                result.add(new CellEntry(null, Array.get(raw, i)));
            }
        } else {
            result.add(new CellEntry(null, raw));
        }
        return result;
    }

    private static boolean matches(CellEntry entry, List<String> tokens) {
        String text = (display(entry.key()) + " " + flatten(entry.value())).toLowerCase();
        for (String token : tokens == null ? List.<String>of() : tokens) {
            if (!text.contains(token.toLowerCase())) return false;
        }
        return true;
    }

    private static String flatten(Object value) {
        if (value instanceof Viewable viewable) return viewable.getReferenceLabel();
        if (value instanceof Map<?, ?> map) {
            StringBuilder text = new StringBuilder();
            map.forEach((key, nested) -> text.append(key).append(' ')
                    .append(flatten(nested)).append(' '));
            return text.toString();
        }
        if (value instanceof Collection<?> collection) {
            StringBuilder text = new StringBuilder();
            collection.forEach(nested -> text.append(flatten(nested)).append(' '));
            return text.toString();
        }
        return display(value);
    }

    private static String display(Object value) {
        if (value == null) return "";
        if (value instanceof Viewable viewable) return viewable.getReferenceLabel();
        if (value instanceof MediaValue media) {
            return media.mediaLabel() == null || media.mediaLabel().isBlank()
                    ? media.mediaUrl() : media.mediaLabel();
        }
        return String.valueOf(value);
    }

    private static String linkUrl(Object value) {
        if (!(value instanceof String string)) return null;
        int separator = string.indexOf('|');
        String candidate = separator >= 0 ? string.substring(separator + 1).trim() : string.trim();
        return candidate.startsWith("http://") || candidate.startsWith("https://")
                ? candidate : null;
    }

    private final class CellMouseHandler extends MouseAdapter {
        @Override public void mouseClicked(MouseEvent event) {
            int row = rowAtPoint(event.getPoint());
            int column = columnAtPoint(event.getPoint());
            if (row < 0 || column < 0) return;
            Object raw = viewableModel.getValueAt(row, column);
            List<CellEntry> values = entries(raw);
            Rectangle bounds = getCellRect(row, column, true);
            if (values.size() > 1 && event.getX() >= bounds.x + bounds.width - NAV_WIDTH) {
                int midpoint = bounds.x + bounds.width - NAV_WIDTH / 2;
                cursors.move(viewableModel.row(row),
                        viewableModel.column(modelColumn(column)).dotted(),
                        event.getX() < midpoint ? -1 : 1, values.size());
                repaint(bounds);
                return;
            }

            CellEntry entry = selectedEntry(row, column, raw);
            Object selected = entry.value();
            if (selectionListener != null) {
                selectionListener.accept(event.getClickCount() >= 2
                        && selected instanceof Viewable
                        ? selected : viewableModel.row(row));
            }
            if (event.getClickCount() >= 2 && isLinkColumn(column)) {
                String url = linkUrl(selected);
                if (url != null) BrowserLauncher.open(url);
            }
        }
    }

    private boolean isLinkColumn(int column) {
        java.lang.reflect.Field field = viewableModel.column(modelColumn(column)).leafField();
        return field != null && field.isAnnotationPresent(Link.class);
    }

    private int modelColumn(int viewColumn) {
        return convertColumnIndexToModel(viewColumn);
    }

    private final class ValueCellRenderer extends JPanel implements TableCellRenderer {
        private final JLabel value = new JLabel();
        private final JLabel navigation = new JLabel();

        private ValueCellRenderer() {
            super(new BorderLayout(5, 0));
            setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 4));
            value.setVerticalAlignment(SwingConstants.CENTER);
            navigation.setHorizontalAlignment(SwingConstants.RIGHT);
            navigation.setForeground(new Color(90, 90, 90));
            add(value, BorderLayout.CENTER);
            add(navigation, BorderLayout.EAST);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object raw, boolean selected, boolean focused,
                int row, int column) {
            List<CellEntry> values = entries(raw);
            CellEntry entry = selectedEntry(row, column, raw);
            int index = values.isEmpty() ? 0 : cursors.index(
                    viewableModel.row(row),
                    viewableModel.column(modelColumn(column)).dotted(), values.size());
            String key = display(entry.key());
            String text = display(entry.value());
            value.setText(key.isBlank() ? (text.isBlank() ? "—" : text) : key + ": " + text);
            navigation.setText(values.size() > 1
                    ? "▲ " + (index + 1) + "/" + values.size() + " ▼" : "");
            value.setForeground(isLinkColumn(column) ? new Color(0, 80, 200)
                    : selected ? table.getSelectionForeground() : table.getForeground());
            value.setCursor(isLinkColumn(column)
                    ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
            boolean hit = row == searchRow && column == searchColumn;
            setBackground(hit ? SEARCH_HIT
                    : selected ? table.getSelectionBackground() : table.getBackground());
            value.setToolTipText(value.getText());
            return this;
        }
    }

    private record CellEntry(Object key, Object value) {}
}
