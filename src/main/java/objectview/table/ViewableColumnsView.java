package objectview.table;

import objectview.Viewable;
import objectview.field.FieldAccess;
import objectview.field.FieldKind;
import objectview.field.FieldPath;
import objectview.field.FieldRef;
import objectview.field.FieldSet;
import objectview.field.ReflectionFieldSet;
import objectview.field.ViewableContractFieldSet;
import objectview.field.ViewableFieldPaths.PathInfo;
import objectview.render.Card;
import objectview.render.RenderedInstanceHost;
import objectview.render.RenderContext;
import objectview.viewconfig.ViewConfig;
import objectview.virtual.ConfigurableVirtualizedContainer;
import objectview.virtual.SearchNavigableContainer;
import objectview.virtual.VirtualizedCardList;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * TABLE mode as a <em>layout</em> of the card path — not a bespoke JTable.
 *
 * <p>Each cell enters the SAME semantic field pipeline as a card: annotations,
 * child configuration, reference/collection policy and cycle context are applied
 * before the shared terminal value components are arranged as equal-width columns
 * under a sticky header. Vertical virtualization, sort and search-navigation are
 * reused from {@link VirtualizedCardList}.
 */
public final class ViewableColumnsView
        implements ConfigurableVirtualizedContainer, SearchNavigableContainer {

    // Rows size to their rendered content. Row-height policy is deliberately
    // independent of field kind and never truncates a value.
    private static final int MIN_ROW_HEIGHT = 32;
    // VirtualizedCardList lays each row out at max(viewportWidth, 380); the header must span
    // exactly that width so its equal columns line up with the rows' equal columns.
    private static final int CONTENT_WIDTH_FLOOR = 380;
    // Each column keeps at least this width; when the columns need more than the viewport, the
    // list widens past it and scrolls horizontally instead of squeezing them thinner.
    private static final int MIN_COLUMN_WIDTH = 160;
    private static final Color GRID = new Color(224, 224, 224);
    private static final Color SELECTION_TINT = new Color(30, 110, 210, 28);
    private static final Color SELECTION_BORDER = new Color(30, 110, 210);
    private static final Color HEADER_BACKGROUND = new Color(245, 245, 245);
    private static final int CELL_PAD_X = 6;
    private static final int CELL_PAD_Y = 3;

    private final RenderContext context;
    private final java.util.function.Supplier<List<PathInfo>> columnResolver;
    private final VirtualizedCardList list;
    private final JScrollPane scroll;
    private final ColumnHeader header;

    private List<Viewable> items;
    private List<PathInfo> columns = List.of();
    private boolean columnsStale = true;
    private Function<Viewable, ViewConfig> configResolver;

    public ViewableColumnsView(
            List<? extends Viewable> items,
            RenderContext context,
            java.util.function.Supplier<List<PathInfo>> columnResolver) {
        this.context = context == null ? new RenderContext() : context;
        this.columnResolver = columnResolver;
        this.items = new ArrayList<>(items == null ? List.of() : items);
        // A reference chip navigates to the target row (isTopLevel is data-based, so it
        // resolves even to a row not yet built) — same contract as the card list.
        this.context.addTopLevels(this.items);

        this.list = new VirtualizedCardList(this::buildRow);
        // setViewConfigResolver only fires when a consumer is present; this view owns the
        // config itself, so the consumer is a no-op and the discard/rebuild is what matters.
        this.list.setCardConfigConsumer(resolver -> {});
        // Give every column at least MIN_COLUMN_WIDTH: the list widens past the viewport (and
        // scrolls horizontally) rather than shrinking columns below it. Reads the current
        // column count on each layout.
        this.list.setContentWidthPolicy(
                viewportWidth -> Math.max(
                        viewportWidth, ensureColumns().size() * MIN_COLUMN_WIDTH));
        this.context.addTopLevelResolver(o ->
                o instanceof Viewable q ? list.buildIfNeeded(q) : null);

        this.scroll = new JScrollPane();
        // The header spans the same width the list gives its rows, so equal columns align and
        // the header scrolls horizontally in step with the content.
        this.header = new ColumnHeader();

        this.list.install(scroll);
        this.scroll.setColumnHeaderView(header);
        // Keep the header width tracking the viewport as it resizes.
        this.scroll.getViewport().addChangeListener(e -> {
            header.revalidate();
            header.repaint();
        });
        // A sensible default until the SearchPanel applies the real view config, so the
        // table shows columns immediately (mirrors the card list rendering before config).
        // The union itself is DEFERRED: it is O(items), and a SearchPanel installs the
        // real resolver before the first paint, so building it here would compute — and
        // throw away — a whole pass over every item.
        this.configResolver = q -> ViewConfig.all(asViewableClass(q.getClass()));
        this.columnsStale = true;
        this.list.setItems(this.items);
    }

    public JScrollPane scrollPane() { return scroll; }

    /** The current column projection (title + path per column). */
    public List<PathInfo> columns() { return ensureColumns(); }

    /** The column union, computed on first use and after any change that can widen or
     *  narrow it. Deferring it keeps the O(items) pass from running twice at startup —
     *  once for the placeholder config and again when the real one is applied. */
    private List<PathInfo> ensureColumns() {
        if (columnsStale) {
            columnsStale = false;   // set first: rebuildColumns repaints, which reads back
            rebuildColumns();
        }
        return columns;
    }

    /** Force-build a row (used by tests / navigation) so its cells can be inspected. */
    public JComponent row(Viewable q) { return list.buildIfNeeded(q); }

    // ---- VirtualizedContainer / ConfigurableVirtualizedContainer (delegate + columns) ----

    @Override public List<Viewable> items() { return list.items(); }

    @Override
    public void forEachMaterialized(BiConsumer<Viewable, JComponent> visitor) {
        list.forEachMaterialized(visitor);
    }

    @Override
    public void setMaterializationListener(
            java.util.function.Consumer<JComponent> listener) {
        list.setMaterializationListener(listener);
    }

    @Override public Viewable topVisibleItem() { return list.topVisibleItem(); }

    @Override public JComponent navigateToTop(Viewable item) { return list.navigateToTop(item); }

    @Override
    public void setItems(List<Viewable> ordered) {
        items = new ArrayList<>(ordered == null ? List.of() : ordered);
        context.addTopLevels(items);
        // Columns come from the declared shape, so a changed member set cannot widen
        // or narrow them — no invalidation, and no rescan.
        list.setItems(items);
    }

    @Override
    public void setViewConfigResolver(Function<Viewable, ViewConfig> resolver) {
        if (resolver == null) return;
        configResolver = resolver;
        columnsStale = true;
        list.setViewConfigResolver(resolver);   // discards + rebuilds rows via the factory
    }

    // ---- SearchNavigableContainer: REVEAL only. The hit tint and the field/text
    // highlights are applied by SearchPanel through the shared RenderedInstanceHost
    // contract, exactly as for cards — this view has no highlighting of its own.

    @Override
    public JComponent revealSearchHit(Viewable item, PathInfo fieldPath, List<String> tokens) {
        // Search must reveal a value hidden by the shared Card semantics, not
        // merely scroll to its collapsed header/reference chip.
        if (item != null && fieldPath != null) {
            Object value = read(item, fieldPath);
            boolean stateChanged = false;
            if (value instanceof java.util.Collection<?> || value instanceof Map<?, ?>) {
                context.setCollectionExpanded(value, true);
                stateChanged = true;
            } else if (value instanceof Viewable reference
                    && !context.isTopLevel(reference)) {
                context.setExpanded(reference, true);
                stateChanged = true;
            }
            if (stateChanged) list.invalidateCard(item);
        }
        JComponent revealed = list.navigateToTop(item);
        list.repaint();
        return revealed;
    }

    // ---- columns + header ----

    /**
     * Asks for the column projection. It is derived from the DECLARED shape, so this
     * costs the same for ten rows and for five hundred thousand — the view never
     * scans its members to find out what columns exist.
     */
    private void rebuildColumns() {
        List<PathInfo> projected =
                columnResolver == null ? null : columnResolver.get();
        columns = projected == null ? List.of() : List.copyOf(projected);
        rebuildHeader();
        // A changed column count changes the desired content width — re-lay the list so its
        // width (and the horizontal scrollbar) follows.
        list.revalidate();
        list.repaint();
    }

    /** The left edge of column {@code index} (or the right edge of the last, at
     *  {@code index == count}). THE column geometry: rows, their cell bounds and
     *  the header all read it here, so the header cannot drift out of alignment
     *  with the cells beneath it. */
    private static int columnLeft(int index, int width, int count) {
        return index * width / Math.max(1, count);
    }

    /** The width the list lays its rows out at, mirrored so the header spans exactly the rows
     *  (equal columns line up). Matches VirtualizedCardList's effectiveWidth for this view. */
    private int contentWidth() {
        int viewportWidth = scroll.getViewport() == null ? 0 : scroll.getViewport().getWidth();
        return Math.max(CONTENT_WIDTH_FLOOR,
                Math.max(viewportWidth, ensureColumns().size() * MIN_COLUMN_WIDTH));
    }

    private void rebuildHeader() {
        header.revalidate();
        header.repaint();
    }

    // ---- row factory ----

    private JComponent buildRow(Viewable q) {
        List<PathInfo> rowColumns = ensureColumns();
        ColumnRow row = new ColumnRow(q, rowColumns);
        ViewConfig config = configResolver == null
                ? ViewConfig.all(asViewableClass(q.getClass())) : configResolver.apply(q);
        for (int columnIndex = 0; columnIndex < rowColumns.size(); columnIndex++) {
            PathInfo column = rowColumns.get(columnIndex);
            Object value = read(q, column);
            FieldRef field = fieldAtPath(q, column);
            ViewConfig childConfig = configAtPath(config, column.path());
            JComponent rendered = value == null || field == null ? null
                    : Card.renderFieldComponent(
                            q, field, column.path(), value, config, childConfig,
                            context, false, false);
            if (rendered != null) {
                row.setCell(columnIndex, rendered);
            }
        }
        installSelectionListener(row);
        context.registerTopLevel(q, row);
        return row;
    }

    /** Resolves leaf metadata through the same FieldSet/schema bridge Card uses. */
    private FieldRef fieldAtPath(Viewable root, PathInfo column) {
        Object current = root;
        FieldRef resolved = null;
        for (String segment : column.path().segments()) {
            current = representative(current);
            if (!(current instanceof Viewable viewable)) break;
            FieldSet fields = FieldSet.of(viewable, context.fieldSchema(viewable));
            resolved = fields.field(segment);
            current = fields.read(segment);
        }
        if (resolved != null) return resolved;
        if (column.leafField() != null) {
            return ReflectionFieldSet.describe(
                    column.leafField(), column.leafField().getDeclaringClass());
        }
        Object value = read(root, column);
        FieldKind kind = FieldKind.ofValue(value);
        FieldKind valueKind = column.valueKind() == FieldKind.UNKNOWN
                ? kind : column.valueKind();
        boolean collection = kind == FieldKind.COLLECTION;
        return FieldRef.described(
                column.leaf(), column.title(), objectview.field.FieldRole.NONE,
                kind, valueKind, null,
                valueKind == FieldKind.REFERENCE, collection, null,
                false, false, false, false, "", false);
    }

    private static Object representative(Object value) {
        if (value instanceof java.util.Collection<?> collection) {
            return collection.stream().filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
        }
        if (value != null && value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                Object item = Array.get(value, i);
                if (item != null) return item;
            }
            return null;
        }
        return value;
    }

    private static ViewConfig configAtPath(ViewConfig root, FieldPath path) {
        ViewConfig current = root;
        if (current == null || path == null) return null;
        for (String segment : path.segments()) {
            current = current.getFieldConfig(segment);
            if (current == null) return null;
        }
        return current;
    }

    private static Object read(Viewable q, PathInfo column) {
        try {
            return FieldAccess.getPathValues(q, column.path());
        } catch (RuntimeException ignored) {
            // A heterogeneous row may not implement a subtype-only column: an empty cell,
            // not a broken row.
            return null;
        }
    }

    /**
     * One lightweight component per materialized instance. Existing ObjectView
     * value components are its direct children; empty cells have no component.
     * Its private layout and painting replace the former row panel + N cell
     * panels + GridLayout/BorderLayout hierarchy.
     */
    private final class ColumnRow extends JComponent implements RenderedInstanceHost {
        private final Viewable q;
        private final List<PathInfo> rowColumns;
        private final JComponent[] cells;
        private Color highlightColor;
        private int measuredWidth = -1;
        private int measuredHeight = MIN_ROW_HEIGHT;

        ColumnRow(Viewable q, List<PathInfo> rowColumns) {
            this.q = q;
            this.rowColumns = List.copyOf(rowColumns);
            this.cells = new JComponent[rowColumns.size()];
            setLayout(null);
            // The row fills its whole bounds in paintComponent, so declaring it
            // opaque lets Swing skip painting the ancestors behind every row.
            setOpaque(true);
        }

        void setCell(int index, JComponent component) {
            if (index < 0 || index >= cells.length || component == null) return;
            cells[index] = component;
            add(component);
        }

        @Override public Viewable renderedInstance() {
            return q;
        }

        @Override public void setHighlightColor(Color color) {
            this.highlightColor = color;
            repaint();
        }

        @Override public boolean isHighlighted() {
            return highlightColor != null;
        }

        @Override public void doLayout() {
            measuredWidth = getWidth();
            measuredHeight = layoutCells(measuredWidth);
            layoutOverlays(measuredWidth);
        }

        @Override public Dimension getPreferredSize() {
            int width = getWidth() > 0 ? getWidth() : contentWidth();
            // VirtualizedCardList explicitly lays out the subtree at its assigned
            // width before asking for preferred height. Returning that cached
            // measurement keeps this query pure: no child sizing, layout or
            // bounds changes from getPreferredSize().
            return new Dimension(width,
                    measuredWidth == width ? measuredHeight : MIN_ROW_HEIGHT);
        }

        /**
         * Places every cell at its column and returns the row height. Height
         * depends on width (text wraps), and Swing has no measure-at-width call,
         * so the children ARE laid out to be measured — done in one named place
         * rather than as a side effect of the size getter.
         */
        private int layoutCells(int width) {
            int count = Math.max(1, cells.length);
            int height = MIN_ROW_HEIGHT;
            for (int i = 0; i < cells.length; i++) {
                JComponent child = cells[i];
                if (child == null) continue;
                int left = columnLeft(i, width, count);
                int cellWidth = Math.max(0,
                        columnLeft(i + 1, width, count) - left - 2 * CELL_PAD_X);
                child.setSize(Math.max(1, cellWidth),
                        Math.max(1, child.getPreferredSize().height));
                layoutSubtree(child);
                int cellHeight = child.getPreferredSize().height;
                child.setBounds(left + CELL_PAD_X, CELL_PAD_Y, cellWidth,
                        Math.max(0, getHeight() - 2 * CELL_PAD_Y));
                height = Math.max(height, cellHeight + 2 * CELL_PAD_Y);
            }
            return height;
        }

        /** Positions transient children that are not field cells, currently the
         *  search "hidden hit" badge. They float at the top-right of the existing
         *  row height and therefore need neither a cell wrapper nor a second
         *  table-specific layout mechanism. */
        private void layoutOverlays(int width) {
            for (Component child : getComponents()) {
                if (isCell(child)) continue;
                Dimension preferred = child.getPreferredSize();
                int overlayWidth = Math.min(
                        Math.max(0, width - 2 * CELL_PAD_X), preferred.width);
                child.setBounds(
                        Math.max(CELL_PAD_X, width - overlayWidth - CELL_PAD_X),
                        CELL_PAD_Y,
                        overlayWidth,
                        Math.min(Math.max(0, getHeight() - 2 * CELL_PAD_Y),
                                preferred.height));
            }
        }

        private boolean isCell(Component component) {
            for (JComponent cell : cells) {
                if (cell == component) return true;
            }
            return false;
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(context.isSelected(q)
                    ? new Color(237, 244, 252) : Color.WHITE);
            g.fillRect(0, 0, getWidth(), getHeight());

            // The search-hit tint composites UNDER the selection tint painted in
            // paintChildren — the same order Card uses, so a selected hit reads
            // the same in both rendering modes.
            if (highlightColor != null) {
                g.setColor(highlightColor);
                g.fillRect(0, 0, getWidth(), getHeight());
            }

            int count = Math.max(1, rowColumns.size());
            g.setColor(GRID);
            g.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
            for (int i = 1; i <= rowColumns.size(); i++) {
                g.drawLine(columnLeft(i, getWidth(), count) - 1, 0,
                        columnLeft(i, getWidth(), count) - 1, getHeight());
            }
        }

        @Override protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            if (context.isSelected(q)) {
                // Paint after cell children so opaque media cannot hide the
                // selection tint or border.
                g.setColor(SELECTION_TINT);
                g.fillRect(0, 0, getWidth(), getHeight());
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(SELECTION_BORDER);
                g2.setStroke(new BasicStroke(2f));
                g2.drawRect(1, 1, getWidth() - 3, getHeight() - 3);
                g2.dispose();
            }
        }

        @Override public void refreshRenderedContent() {
            SwingUtilities.invokeLater(() -> list.invalidateCard(q));
        }
    }

    /** One painted sticky header: no JPanel, JLabel or per-column component. */
    private final class ColumnHeader extends JComponent {
        ColumnHeader() {
            setOpaque(false);
            Color foreground = javax.swing.UIManager.getColor("Label.foreground");
            setForeground(foreground == null ? Color.BLACK : foreground);
        }

        private Font headerFont() {
            Font base = getFont();
            if (base == null) base = javax.swing.UIManager.getFont("Label.font");
            if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
            return base.deriveFont(Font.BOLD);
        }

        @Override public Dimension getPreferredSize() {
            FontMetrics metrics = getFontMetrics(headerFont());
            return new Dimension(contentWidth(), metrics.getHeight() + 8);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(HEADER_BACKGROUND);
                g2.fillRect(0, 0, getWidth(), getHeight());
                Font font = headerFont();
                FontMetrics metrics = g2.getFontMetrics(font);
                g2.setFont(font);
                List<PathInfo> painted = ensureColumns();
                int count = Math.max(1, painted.size());
                int baseline = (getHeight() - metrics.getHeight()) / 2
                        + metrics.getAscent();
                for (int i = 0; i < painted.size(); i++) {
                    int left = columnLeft(i, getWidth(), count);
                    int right = columnLeft(i + 1, getWidth(), count);
                    Graphics2D cell = (Graphics2D) g2.create(
                            left, 0, Math.max(0, right - left), getHeight());
                    try {
                        cell.setColor(getForeground());
                        cell.drawString(painted.get(i).title(), CELL_PAD_X, baseline);
                    } finally {
                        cell.dispose();
                    }
                }
                g2.setColor(GRID);
                g2.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1);
                for (int i = 1; i <= painted.size(); i++) {
                    int x = columnLeft(i, getWidth(), count) - 1;
                    g2.drawLine(x, 0, x, getHeight());
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /** Lays out nested existing ObjectView components at the width assigned by
     *  the private column layout before their preferred height is measured. */
    private static void layoutSubtree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layoutSubtree(nested);
        }
    }

    /** Mouse events do not bubble in Swing, so the listener is registered on every
     *  component in the row — but it is ONE instance for the whole view, resolving
     *  the clicked instance from its row (the {@link TextCopyMouseHandler} idiom),
     *  not a fresh adapter closing over an owner per row. It never consumes the
     *  event, so links, reference toggles and image double-clicks keep working. */
    private final MouseAdapter selectionListener = new MouseAdapter() {
        @Override public void mousePressed(MouseEvent event) {
            if (!context.selectionEnabled()
                    || !SwingUtilities.isLeftMouseButton(event)) {
                return;
            }
            RenderedInstanceHost host =
                    RenderedInstanceHost.hostOf(event.getComponent());
            if (host != null && host.renderedInstance() != null) {
                context.select(host.renderedInstance());
            }
        }
    };

    private void installSelectionListener(Component component) {
        component.addMouseListener(selectionListener);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                installSelectionListener(child);
            }
        }
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
