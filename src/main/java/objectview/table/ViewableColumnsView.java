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
import objectview.render.RenderRefreshHost;
import objectview.render.RenderContext;
import objectview.viewconfig.ViewConfig;
import objectview.virtual.ConfigurableVirtualizedContainer;
import objectview.virtual.SearchNavigableContainer;
import objectview.virtual.VirtualizedCardList;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
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
public final class ViewableColumnsView extends JPanel
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
    private static final Color SEARCH_HIT = new Color(255, 236, 179);

    private final RenderContext context;
    private final BiFunction<Viewable, ViewConfig, List<PathInfo>> columnResolver;
    private final VirtualizedCardList list;
    private final JScrollPane scroll;
    private final JPanel header;

    private List<Viewable> items;
    private List<PathInfo> columns = List.of();
    private Function<Viewable, ViewConfig> configResolver;
    private Viewable searchHit;
    private FieldPath searchPath = FieldPath.ROOT;

    public ViewableColumnsView(
            List<? extends Viewable> items,
            RenderContext context,
            BiFunction<Viewable, ViewConfig, List<PathInfo>> columnResolver) {
        super(new BorderLayout());
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
                viewportWidth -> Math.max(viewportWidth, columns.size() * MIN_COLUMN_WIDTH));
        this.context.addTopLevelResolver(o ->
                o instanceof Viewable q ? list.buildIfNeeded(q) : null);

        this.scroll = new JScrollPane();
        // The header spans the same width the list gives its rows, so equal columns align and
        // the header scrolls horizontally in step with the content.
        this.header = new JPanel() {
            @Override public Dimension getPreferredSize() {
                return new Dimension(contentWidth(), super.getPreferredSize().height);
            }
        };
        this.header.setBackground(new Color(245, 245, 245));

        this.list.install(scroll);
        this.scroll.setColumnHeaderView(header);
        // Keep the header width tracking the viewport as it resizes.
        this.scroll.getViewport().addChangeListener(e -> {
            header.revalidate();
            header.repaint();
        });
        add(scroll, BorderLayout.CENTER);

        // A sensible default until the SearchPanel applies the real view config, so the
        // table shows columns immediately (mirrors the card list rendering before config).
        this.configResolver = q -> ViewConfig.all(asViewableClass(q.getClass()));
        rebuildColumns();
        this.list.setItems(this.items);
    }

    public JScrollPane scrollPane() { return scroll; }

    /** The current column projection (title + path per column). */
    public List<PathInfo> columns() { return columns; }

    /** Force-build a row (used by tests / navigation) so its cells can be inspected. */
    public JComponent row(Viewable q) { return list.buildIfNeeded(q); }

    // ---- VirtualizedContainer / ConfigurableVirtualizedContainer (delegate + columns) ----

    @Override public List<Viewable> items() { return list.items(); }

    @Override public Viewable topVisibleItem() { return list.topVisibleItem(); }

    @Override public JComponent navigateToTop(Viewable item) { return list.navigateToTop(item); }

    @Override
    public void setItems(List<Viewable> ordered) {
        List<Viewable> next = new ArrayList<>(ordered == null ? List.of() : ordered);
        boolean sameMembers = sameIdentityMembers(items, next);
        items = next;
        context.addTopLevels(items);
        if (!sameMembers) {
            rebuildColumns();   // a changed member set can widen/narrow the column union
        }
        list.setItems(items);
    }

    @Override
    public void setViewConfigResolver(Function<Viewable, ViewConfig> resolver) {
        if (resolver == null) return;
        configResolver = resolver;
        rebuildColumns();
        list.setViewConfigResolver(resolver);   // discards + rebuilds rows via the factory
    }

    // ---- SearchNavigableContainer (data-based highlight survives virtualization) ----

    @Override
    public JComponent revealSearchHit(Viewable item, PathInfo fieldPath, List<String> tokens) {
        searchHit = item;
        searchPath = fieldPath == null ? FieldPath.ROOT : fieldPath.path();
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

    @Override
    public void clearSearchHighlight() {
        searchHit = null;
        searchPath = FieldPath.ROOT;
        list.repaint();
    }

    // ---- columns + header ----

    private void rebuildColumns() {
        Map<String, PathInfo> union = new LinkedHashMap<>();
        if (configResolver != null && columnResolver != null) {
            for (Viewable row : items) {
                List<PathInfo> paths = columnResolver.apply(row, configResolver.apply(row));
                if (paths == null) continue;
                for (PathInfo path : paths) {
                    if (path != null) union.putIfAbsent(path.dotted(), path);
                }
            }
        }
        List<PathInfo> ordered = new ArrayList<>(union.values());
        // The display field reads as the row's title, so it leads the columns (last-wins if
        // several bind @DisplayField) — same ordering the JTable model used.
        if (!items.isEmpty()) {
            String displayKey = ViewableContractFieldSet.displayKey(
                    asViewableClass(items.get(0).getClass()));
            for (int i = 1; i < ordered.size(); i++) {
                if (ordered.get(i).dotted().equals(displayKey)) {
                    ordered.add(0, ordered.remove(i));
                    break;
                }
            }
        }
        columns = List.copyOf(ordered);
        rebuildHeader();
        // A changed column count changes the desired content width — re-lay the list so its
        // width (and the horizontal scrollbar) follows.
        list.revalidate();
        list.repaint();
    }

    /** The width the list lays its rows out at, mirrored so the header spans exactly the rows
     *  (equal columns line up). Matches VirtualizedCardList's effectiveWidth for this view. */
    private int contentWidth() {
        int viewportWidth = scroll.getViewport() == null ? 0 : scroll.getViewport().getWidth();
        return Math.max(CONTENT_WIDTH_FLOOR,
                Math.max(viewportWidth, columns.size() * MIN_COLUMN_WIDTH));
    }

    private void rebuildHeader() {
        header.removeAll();
        header.setLayout(new GridLayout(1, Math.max(1, columns.size())));
        for (PathInfo column : columns) {
            JLabel title = new JLabel(column.title());
            title.setFont(title.getFont().deriveFont(Font.BOLD));
            title.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 1, GRID),
                    BorderFactory.createEmptyBorder(4, 6, 4, 6)));
            header.add(title);
        }
        header.revalidate();
        header.repaint();
    }

    // ---- row factory ----

    private JComponent buildRow(Viewable q) {
        RowPanel row = new RowPanel(q);
        row.setLayout(new GridLayout(1, Math.max(1, columns.size())));
        ViewConfig config = configResolver == null
                ? ViewConfig.all(asViewableClass(q.getClass())) : configResolver.apply(q);
        for (PathInfo column : columns) {
            JPanel cell = new CellPanel(q, column.path());
            cell.setOpaque(false);
            cell.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 1, GRID));
            Object value = read(q, column);
            FieldRef field = fieldAtPath(q, column);
            ViewConfig childConfig = configAtPath(config, column.path());
            JComponent rendered = value == null || field == null ? null
                    : Card.renderFieldComponent(
                            q, field, column.path(), value, config, childConfig,
                            context, false, false);
            if (rendered != null) {
                cell.add(rendered, BorderLayout.CENTER);
            }
            row.add(cell);
        }
        installSelectionListener(row, q);
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

    /** A row that paints selection/search state from the shared context, so both survive the
     *  list's virtualized rebuild (they are read from data, never stored on the component). */
    private final class RowPanel extends JPanel implements RenderRefreshHost {
        private final Viewable q;

        RowPanel(Viewable q) {
            this.q = q;
            setOpaque(true);
            setBackground(Color.WHITE);
        }

        @Override public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            int height = Math.max(MIN_ROW_HEIGHT, d.height);
            return new Dimension(d.width, height);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (context.isSelected(q)) {
                g.setColor(new Color(237, 244, 252));
                g.fillRect(0, 0, getWidth(), getHeight());
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

    /** Cell-local search state: fieldPath is no longer discarded by table mode. */
    private final class CellPanel extends JPanel {
        private final Viewable owner;
        private final FieldPath path;

        CellPanel(Viewable owner, FieldPath path) {
            super(new BorderLayout());
            this.owner = owner;
            this.path = path;
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (owner == searchHit && path.equals(searchPath)) {
                g.setColor(SEARCH_HIT);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        }
    }

    /** Mouse events do not bubble in Swing; attach selection without consuming
     *  events so links, reference toggles and image double-clicks keep working. */
    private void installSelectionListener(java.awt.Component component, Viewable owner) {
        MouseAdapter listener = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (context.selectionEnabled() && SwingUtilities.isLeftMouseButton(event)) {
                    context.select(owner);
                }
            }
        };
        installSelectionListener(component, listener);
    }

    private static void installSelectionListener(
            java.awt.Component component, MouseAdapter listener) {
        component.addMouseListener(listener);
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                installSelectionListener(child, listener);
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
