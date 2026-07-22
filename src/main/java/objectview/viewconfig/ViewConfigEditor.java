package objectview.viewconfig;

import objectview.field.DynamicFields;
import objectview.field.FieldKind;
import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.ViewableFieldPaths;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class ViewConfigEditor extends JPanel {

    private final ViewConfig sourceConfig;
    private final List<Row> rows = new ArrayList<>();

    private final RowTableModel tableModel = new RowTableModel();
    private final JTable table = new JTable(tableModel);
    private final boolean nestedDefaultNameOnly;
    private final boolean minorOnly;
    // A sample instance for a DYNAMIC (map-held) type — enumerate its fields from
    // the property map rather than declared Java fields. Null = reflection type.
    private final Viewable sample;

    private final JCheckBox allMinorFieldsBox = new JCheckBox("All minor fields");
    private Runnable changeListener;

    // Top-level field names the caller doesn't want offered (e.g. structural /
    // provenance plumbing a domain marks). Purely mechanical — the editor has no
    // idea WHY a field is hidden.
    private java.util.Set<String> hiddenFields = java.util.Set.of();

    // Optional authoritative field types for a DYNAMIC sample — overrides the
    // sample-reflected type label / structural-ness / nested source. Null = reflect
    // the sample as before. See FieldTypeSource.
    private FieldTypeSource typeSource;

    // The plugin that shapes the columns / actions / selection (see
    // FieldRowContributor). DEFAULT reproduces the classic card-config table.
    private final FieldRowContributor contributor;
    // The concrete, ordered columns for this contributor, computed once.
    private final List<Col> cols;

    // Flat PATH-ROW mode: rows come from a precomputed list of dotted paths (the
    // fold-in of the old single-select FieldTreePanel), not sample reflection.
    private final boolean pathMode;

    // Drop MEDIA-kind fields (a renderable image) from the row list — set on the
    // search / sort config editors, where searching or ordering an image is
    // meaningless; the VIEW editor leaves it off, so a portrait can be shown/hidden.
    private boolean hideMedia;

    public ViewConfigEditor(ViewConfig config) {
        this(config, false, false, null, FieldRowContributor.DEFAULT);
    }

    public ViewConfigEditor(ViewConfig config,
                            boolean nestedDefaultNameOnly) {
        this(config, nestedDefaultNameOnly, false, null, FieldRowContributor.DEFAULT);
    }

    /** Dynamic: enumerate {@code sample}'s map-held fields (a a map-backed Viewable). */
    public ViewConfigEditor(ViewConfig config, Viewable sample) {
        this(config, false, false, sample, FieldRowContributor.DEFAULT);
    }

    public ViewConfigEditor(ViewConfig config,
                            boolean nestedDefaultNameOnly, Viewable sample) {
        this(config, nestedDefaultNameOnly, false, sample, FieldRowContributor.DEFAULT);
    }

    /**
     * A contributor-driven editor with NO reflective source — rows are supplied later
     * via {@link #setPathRows}. This is the single-select field picker / coverage-table
     * face of the component (the old {@code FieldTreePanel} / bespoke Validation table).
     */
    public ViewConfigEditor(FieldRowContributor contributor) {
        this(new ViewConfig(), false, false, null, contributor);
    }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    /** Hides the given top-level fields from the row list (and rebuilds). */
    public void setHiddenFields(java.util.Set<String> fieldNames) {
        this.hiddenFields = fieldNames == null ? java.util.Set.of() : fieldNames;
        buildRows();
        tableModel.fireTableDataChanged();
    }

    /** Supplies authoritative field types for a dynamic sample (overrides sample
     *  reflection); null restores reflection. Rebuilds. */
    public void setFieldTypes(FieldTypeSource source) {
        this.typeSource = source;
        buildRows();
        tableModel.fireTableDataChanged();
    }

    /** Hide MEDIA-kind fields (images) — for the search / sort editors. Rebuilds. */
    public void setHideMedia(boolean hideMedia) {
        this.hideMedia = hideMedia;
        buildRows();
        tableModel.fireTableDataChanged();
    }

    private void fireConfigChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    private ViewConfigEditor(ViewConfig config,
                             boolean nestedDefaultNameOnly,
                             boolean minorOnly) {
        this(config, nestedDefaultNameOnly, minorOnly, null, FieldRowContributor.DEFAULT);
    }

    private ViewConfigEditor(ViewConfig config,
                             boolean nestedDefaultNameOnly,
                             boolean minorOnly,
                             Viewable sample,
                             FieldRowContributor contributor) {
        this.sourceConfig = config == null
                ? new ViewConfig()
                : config.copy();

        this.nestedDefaultNameOnly = nestedDefaultNameOnly;
        this.minorOnly = minorOnly;
        this.sample = sample;
        this.contributor = contributor == null ? FieldRowContributor.DEFAULT : contributor;
        this.pathMode = this.contributor.selectionMode() == FieldRowContributor.SelectionMode.SINGLE;
        this.cols = buildColumns();
        // The JTable field initializer already asked the model for its columns while
        // `cols` was still null (guarded to 0); now that it's built, let the table
        // create its TableColumns before we style them.
        tableModel.fireTableStructureChanged();

        setLayout(new BorderLayout(8, 8));

        // A dynamic (map-held) type has no minor-field concept — that's a card
        // rendering distinction; here every field is shown, so no checkbox. The flat
        // path-row / single-select face has no minor concept either.
        if (!minorOnly && !pathMode && !(sample instanceof DynamicFields)) {
            allMinorFieldsBox.setSelected(sourceConfig.isAllMinorFields());
            allMinorFieldsBox.addActionListener(e -> {
                tableModel.fireTableDataChanged();
                fireConfigChanged();
            });
            JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
            top.add(allMinorFieldsBox);
            add(top, BorderLayout.NORTH);
        }

        buildRows();

        table.setRowHeight(28);
        table.setFillsViewportHeight(true);

        // Single-select picker: the chosen field is the table's selected row.
        if (pathMode) {
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.getSelectionModel().addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    fireConfigChanged();
                }
            });
        }

        installColumns();

        table.setDefaultRenderer(Object.class,
                new RowRenderer(table.getDefaultRenderer(Object.class)));
        table.setDefaultRenderer(Boolean.class,
                new RowRenderer(table.getDefaultRenderer(Boolean.class)));

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    // ---- columns -----------------------------------------------------------

    private enum ColKind { FIELD, TYPE, EXTRA, USE, UP, DOWN, ACTION, EXPAND }

    private static final class Col {
        final ColKind kind;
        final String header;
        final int width;
        final FieldRowContributor.ExtraColumn extra;   // EXTRA only
        final FieldRowContributor.RowAction action;    // ACTION only

        Col(ColKind kind, String header, int width) {
            this(kind, header, width, null, null);
        }

        Col(ColKind kind, String header, int width,
            FieldRowContributor.ExtraColumn extra,
            FieldRowContributor.RowAction action) {
            this.kind = kind;
            this.header = header;
            this.width = width;
            this.extra = extra;
            this.action = action;
        }

        boolean button() {
            return kind == ColKind.UP || kind == ColKind.DOWN
                    || kind == ColKind.ACTION || kind == ColKind.EXPAND;
        }

        boolean fixedWidth() {
            return kind == ColKind.UP || kind == ColKind.DOWN;
        }
    }

    private List<Col> buildColumns() {
        List<Col> c = new ArrayList<>();
        c.add(new Col(ColKind.FIELD, "Field", 260));
        c.add(new Col(ColKind.TYPE, "Type", 160));
        for (FieldRowContributor.ExtraColumn ec : contributor.columns()) {
            c.add(new Col(ColKind.EXTRA, ec.header(), ec.width(), ec, null));
        }
        if (contributor.showUse()) {
            c.add(new Col(ColKind.USE, "Use", 48));
        }
        if (contributor.showReorder()) {
            c.add(new Col(ColKind.UP, "", 30));
            c.add(new Col(ColKind.DOWN, "", 30));
        }
        for (FieldRowContributor.RowAction a : contributor.actions()) {
            c.add(new Col(ColKind.ACTION, "", 120, null, a));
        }
        if (contributor.showExpand()) {
            c.add(new Col(ColKind.EXPAND, "Expand", 72));
        }
        return c;
    }

    private void installColumns() {
        for (int i = 0; i < cols.size(); i++) {
            Col col = cols.get(i);
            javax.swing.table.TableColumn tc = table.getColumnModel().getColumn(i);
            if (col.fixedWidth()) {
                setFixedWidth(tc, col.width);
            } else {
                tc.setPreferredWidth(col.width);
            }
            if (col.button()) {
                tc.setCellRenderer(new ButtonRenderer());
                tc.setCellEditor(new ButtonEditor());
            }
        }
    }

    private int columnOf(ColKind kind) {
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i).kind == kind) {
                return i;
            }
        }
        return -1;
    }

    // ---- flat path-row source (folds in FieldTreePanel) --------------------

    /**
     * Populates the editor from a flat list of dotted field paths (the single-select
     * picker face). Nested paths render indented, a parent immediately above its
     * children; a synthesized ancestor that isn't itself a selectable path shows as a
     * dim container. {@code hiddenTop} drops paths by their top segment;
     * {@code typeLabelForPath} (nullable) supplies the Type-column label per path — the
     * caller owns it, since a reflection domain's shape/kind lives outside this lib.
     * Clears the selection.
     */
    public void setPathRows(List<String> paths, Set<String> hiddenTop,
                            java.util.function.Function<String, String> typeLabelForPath) {
        rows.clear();

        Set<String> real = new LinkedHashSet<>();
        for (String p : paths) {
            if (p != null && !p.isBlank()
                    && (hiddenTop == null || !hiddenTop.contains(p.split("\\.")[0]))) {
                real.add(p);
            }
        }
        // Every prefix of every real path, sorted as a string so a parent sorts
        // directly above its descendants and siblings group together (a flat DFS).
        Set<String> prefixes = new TreeSet<>();
        for (String p : real) {
            String[] seg = p.split("\\.");
            StringBuilder prefix = new StringBuilder();
            for (int i = 0; i < seg.length; i++) {
                if (i > 0) {
                    prefix.append('.');
                }
                prefix.append(seg[i]);
                prefixes.add(prefix.toString());
            }
        }
        for (String p : prefixes) {
            String[] seg = p.split("\\.");
            Row r = Row.path(seg[seg.length - 1], p, seg.length - 1, !real.contains(p),
                    typeLabelForPath == null ? null : typeLabelForPath.apply(p));
            rows.add(r);
        }

        tableModel.fireTableDataChanged();
        if (table != null) {
            table.clearSelection();
        }
    }

    /** The selected field path in single-select mode (null if nothing, or a container
     *  row, is selected). */
    public String selectedPath() {
        int view = table.getSelectedRow();
        if (view < 0) {
            return null;
        }
        Row r = rows.get(table.convertRowIndexToModel(view));
        return r.container ? null : r.path;
    }

    /** Selects the row at {@code dottedPath} (reflecting an external choice). */
    public void setSelectedPath(String dottedPath) {
        if (dottedPath == null) {
            table.clearSelection();
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            if (dottedPath.equals(rows.get(i).path)) {
                int view = table.convertRowIndexToView(i);
                if (view >= 0) {
                    table.setRowSelectionInterval(view, view);
                    table.scrollRectToVisible(table.getCellRect(view, 0, true));
                }
                return;
            }
        }
        table.clearSelection();
    }

    // ---- reflective row source (classic) -----------------------------------

    private void buildRows() {
        rows.clear();

        // The flat path-row source builds its own rows via setPathRows.
        if (pathMode) {
            return;
        }

        // A dynamic sample: enumerate its map-held fields (no declared Java fields,
        // no minor-field concept). Reflection types keep the original path exactly.
        if (sample instanceof DynamicFields) {
            addDynamicFieldRows(sample);
            return;
        }

        Class<? extends Viewable> cls = sourceConfig.getCls();
        if (cls == null) {
            return;
        }

        if (minorOnly) {
            addFieldRows(cls, true);
        } else {
            addFieldRows(cls, false);

            if (hasMinorFields(cls)) {
                rows.add(Row.minorBlock());
            }
        }
    }

    private void addDynamicFieldRows(Viewable dynamicSample) {
        DynamicFields dyn = (DynamicFields) dynamicSample;
        // Identity row first: `name` isn't in the property map (it's the display
        // name), but search/sort/view configs must be able to include/exclude it
        // like any other field — otherwise it's invisible yet always searched.
        if (!hiddenFields.contains("name")
                && !dyn.dynamicFieldValues().containsKey("name")) {
            Row nameRow = Row.dynamic("name", "String", null, null);
            nameRow.use = sourceConfig.showsFieldByName("name");
            rows.add(nameRow);
        }
        for (Map.Entry<String, Object> e : dyn.dynamicFieldValues().entrySet()) {
            String name = e.getKey();
            FieldTypeSource.FieldTypeInfo info =
                    typeSource == null ? null : typeSource.field(name);
            // The model (via the type source) can mark a field structural — the
            // reify `source` back-ref, the auto-seeded `wikidata` link — so it's
            // hidden here just like the explicit hiddenFields set.
            if (hiddenFields.contains(name) || (info != null && info.structural())) {
                continue;
            }
            Object value = e.getValue();
            if (hideMedia && FieldKind.ofValue(value) == FieldKind.MEDIA) {
                continue;
            }
            Viewable child = firstViewable(value);
            // Only offer expand (nested) when the referenced value actually has
            // fields — a bare reference (e.g. a WDO with no dynamic fields) would
            // otherwise open an empty child editor. When the model (type source)
            // is present it decides: a name-only reference gives no nested source,
            // so it stays a reference chip with no dead-end "+fields" expansion.
            boolean modelExpandable = info == null || info.nested() != null;
            Class<? extends Viewable> nested =
                    modelExpandable && child != null && hasFields(child)
                            ? asViewableClass(child.getClass()) : null;

            String typeLabel = info != null ? info.typeLabel() : dynamicTypeLabel(value, child);
            Row row = Row.dynamic(name, typeLabel, nested, child);
            row.nestedTypeSource = info != null ? info.nested() : null;
            row.nestedLabel = info != null ? info.nestedClassName() : null;
            row.use = sourceConfig.showsFieldByName(name);
            rows.add(row);
        }
    }

    private static boolean hasFields(Viewable q) {
        if (q instanceof DynamicFields d) {
            return !d.dynamicFieldValues().isEmpty();
        }
        return !ViewableAdapter.getAllFields(q.getClass()).isEmpty();
    }

    private static Viewable firstViewable(Object v) {
        if (v instanceof Viewable q) {
            return q;
        }
        if (v instanceof Collection<?> c) {
            for (Object i : c) {
                if (i instanceof Viewable q) return q;
            }
        }
        if (v instanceof Map<?, ?> m) {
            for (Object i : m.values()) {
                if (i instanceof Viewable q) return q;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Viewable> asViewableClass(Class<?> cls) {
        return (Class<? extends Viewable>) cls;
    }

    private static String dynamicTypeLabel(Object value, Viewable child) {
        if (child != null) {
            return value instanceof Collection<?>
                    ? "Collection<" + child.typeName() + ">"
                    : child.typeName();
        }
        if (value instanceof Collection<?>) {
            return "Collection";
        }
        return value == null ? "" : value.getClass().getSimpleName();
    }

    private boolean hasMinorFields(Class<? extends Viewable> cls) {
        for (Field field : ViewableAdapter.getAllFields(cls)) {
            if (!Modifier.isStatic(field.getModifiers())
                    && ViewableAdapter.isMinorField(field)) {
                return true;
            }
        }
        return false;
    }

    private void addFieldRows(Class<? extends Viewable> cls, boolean minor) {
        for (Field field : ViewableAdapter.getConfigurableFields(cls)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            if (ViewableAdapter.isMinorField(field) != minor) {
                continue;
            }

            if (hiddenFields.contains(field.getName())) {
                continue;
            }

            if (hideMedia && FieldKind.ofClass(field.getType()) == FieldKind.MEDIA) {
                continue;
            }

            String fieldName = field.getName();
            ViewConfig selectedChild = sourceConfig.getFieldConfig(fieldName);
            Class<? extends Viewable> nestedClass =
                    ViewableFieldPaths.nestedViewableClass(field);

            Row row = Row.field(field, describeFieldType(field), nestedClass);
            row.use = sourceConfig.showsField(field);

            if (selectedChild != null
                    && nestedClass != null
                    && !selectedChild.getFields().isEmpty()) {
                row.childEditor = new ViewConfigEditor(
                        selectedChild,
                        nestedDefaultNameOnly);
            }

            rows.add(row);
        }
    }

    /**
     * The dotted paths of the currently CHECKED fields — for reusing this panel as
     * a field PICKER (e.g. selecting operation arguments). A checked reference not
     * expanded yields the reference itself; an expanded nested selection yields the
     * nested path. Works for reflection and dynamic rows alike.
     */
    public List<String> selectedFieldPaths() {
        List<String> out = new ArrayList<>();
        collectSelected("", out);
        return out;
    }

    private void collectSelected(String prefix, List<String> out) {
        for (Row row : rows) {
            if (row.special || !row.use) {
                continue;
            }
            String path = prefix.isEmpty() ? row.fieldName : prefix + "." + row.fieldName;
            if (row.childEditor != null) {
                row.childEditor.collectSelected(path, out);
            } else {
                out.add(path);
            }
        }
    }

    public ViewConfig getConfig() {
        ViewConfig out = copyHeader(sourceConfig);

        out.setAllFields(false);
        out.setAllMinorFields(!minorOnly && allMinorFieldsBox.isSelected());
        out.getFields().clear();

        for (Row row : rows) {
            if (row.special || !row.use) {
                continue;
            }

            // If "all minor fields" is checked, no need to explicitly store every minor field.
            if (!minorOnly
                    && out.isAllMinorFields()
                    && row.isMinor()
                    && sourceConfig.getFieldConfig(row.fieldName) == null) {
                continue;
            }

            out.addField(row.fieldName, childConfigFor(row));
        }

        if (!minorOnly) {
            Row minorBlock = findMinorBlock();
            if (minorBlock != null && minorBlock.childEditor != null) {
                ViewConfig minorConfig = minorBlock.childEditor.getConfig();
                out.setAllMinorFields(minorConfig.isAllMinorFields() || out.isAllMinorFields());

                for (var e : minorConfig.getFields().entrySet()) {
                    out.addField(e.getKey(), e.getValue());
                }
            }
        }

        return out;
    }

    private Row findMinorBlock() {
        for (Row row : rows) {
            if (row.minorBlock) {
                return row;
            }
        }
        return null;
    }

    private ViewConfig childConfigFor(Row row) {
        if (row.childEditor != null) {
            return row.childEditor.getConfig();
        }

        if (row.nestedClass != null) {
            return ViewConfig.of(row.nestedClass);
        }

        return ViewConfig.leaf();
    }

    private ViewConfig copyHeader(ViewConfig src) {
        ViewConfig out = new ViewConfig();

        if (src == null) {
            return out;
        }

        out.setCls(src.getCls());
        out.setAllFields(src.isAllFields());
        out.setAllMinorFields(src.isAllMinorFields());
        out.setAddListener(src.isAddListener());
        out.setThumb(src.isThumb());
        out.setAnswerType(src.getAnswerType());
        out.setBlurImages(src.isBlurImages());

        return out;
    }

    private void moveRow(Row row, int delta) {
        if (row.special) {
            return;
        }

        int from = rows.indexOf(row);
        if (from < 0) {
            return;
        }

        int to = from + delta;
        if (to < 0 || to >= rows.size() || rows.get(to).special) {
            return;
        }

        rows.remove(from);
        rows.add(to, row);

        tableModel.fireTableDataChanged();

        int viewRow = table.convertRowIndexToView(to);
        if (viewRow >= 0) {
            table.getSelectionModel().setSelectionInterval(viewRow, viewRow);
        }
        fireConfigChanged();
    }

    private void openMinorEditor(Row row) {
        if (!row.minorBlock) {
            return;
        }

        if (row.childEditor == null) {
            ViewConfig cfg = sourceConfig.copy();
            cfg.setAllMinorFields(allMinorFieldsBox.isSelected());
            row.childEditor = new ViewConfigEditor(cfg, nestedDefaultNameOnly, true);
        }

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                "Minor fields : " + sourceConfig.getCls().getSimpleName(),
                Dialog.ModalityType.APPLICATION_MODAL);

        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(row.childEditor, BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            ViewConfig cfg = row.childEditor.getConfig();
            allMinorFieldsBox.setSelected(cfg.isAllMinorFields());
            dialog.dispose();
            tableModel.fireTableDataChanged();
            fireConfigChanged();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(okButton);

        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void openChildEditor(Row row) {
        if (row.minorBlock) {
            openMinorEditor(row);
            return;
        }

        if (row.special || row.nestedClass == null) {
            return;
        }

        row.use = true;

        if (row.childEditor == null) {
            ViewConfig childConfig =
                    sourceConfig.getFieldConfig(row.fieldName);

            if (childConfig == null || childConfig.getCls() == null) {
                childConfig = nestedDefaultNameOnly
                        ? nameOnlyConfig(row.nestedClass)
                        : ViewConfig.all(row.nestedClass);
            } else {
                childConfig = childConfig.copy();
            }

            // A dynamic reference carries a nested SAMPLE so the child editor can
            // enumerate the referenced object's map-held fields.
            row.childEditor = new ViewConfigEditor(
                    childConfig, nestedDefaultNameOnly, false, row.nestedSample,
                    FieldRowContributor.DEFAULT);
            // Carry the authoritative types down so the nested level hides its own
            // structural fields (e.g. a Category's `wikidata`) and labels correctly.
            if (row.nestedTypeSource != null) {
                row.childEditor.setFieldTypes(row.nestedTypeSource);
            }
        }

        // Prefer the model class name (e.g. "Nomination") over the raw sample Java
        // class ("WikidataDynamicObject") for the caption.
        String nestedName = row.nestedLabel != null && !row.nestedLabel.isBlank()
                ? row.nestedLabel : row.nestedClass.getSimpleName();
        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                row.fieldName + " : " + nestedName,
                Dialog.ModalityType.APPLICATION_MODAL);

        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(row.childEditor, BorderLayout.CENTER);

        JButton clearButton = new JButton("Clear custom config");
        JButton okButton = new JButton("OK");

        clearButton.addActionListener(e -> {
            row.childEditor = null;
            dialog.dispose();
            tableModel.fireTableDataChanged();
            fireConfigChanged();
        });

        okButton.addActionListener(e -> {
            dialog.dispose();
            tableModel.fireTableDataChanged();
            fireConfigChanged();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(clearButton);
        buttons.add(okButton);

        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        tableModel.fireTableDataChanged();
    }

    private ViewConfig nameOnlyConfig(Class<? extends Viewable> cls) {
        ViewConfig cfg = ViewConfig.of(cls);
        cfg.setAllFields(false);
        cfg.setAllMinorFields(false);
        cfg.setAddListener(false);
        cfg.setThumb(false);
        cfg.addField("name", ViewConfig.leaf());
        return cfg;
    }

    private String describeFieldType(Field field) {
        Class<?> type = field.getType();

        if (Viewable.class.isAssignableFrom(type)) {
            return type.getSimpleName();
        }

        if (java.util.Collection.class.isAssignableFrom(type)) {
            Class<? extends Viewable> nested =
                    ViewableFieldPaths.nestedViewableClass(field);

            return nested != null
                    ? "Collection<" + nested.getSimpleName() + ">"
                    : "Collection";
        }

        if (java.util.Map.class.isAssignableFrom(type)) {
            Class<? extends Viewable> nested =
                    ViewableFieldPaths.nestedViewableClass(field);

            return nested != null
                    ? "Map<?, " + nested.getSimpleName() + ">"
                    : "Map";
        }

        return type.getSimpleName();
    }

    private int countSelectedMinorFields() {
        if (sourceConfig.isAllMinorFields()) {
            return -1;
        }

        int count = 0;
        Class<? extends Viewable> cls = sourceConfig.getCls();

        if (cls == null) {
            return 0;
        }

        for (Field f : ViewableAdapter.getAllFields(cls)) {
            if (ViewableAdapter.isMinorField(f)
                    && sourceConfig.getFieldConfig(f.getName()) != null) {
                count++;
            }
        }

        Row minorBlock = findMinorBlock();
        if (minorBlock != null && minorBlock.childEditor != null) {
            ViewConfig cfg = minorBlock.childEditor.getConfig();
            count = 0;
            for (Field f : ViewableAdapter.getAllFields(cls)) {
                if (ViewableAdapter.isMinorField(f)
                        && cfg.getFieldConfig(f.getName()) != null) {
                    count++;
                }
            }
        }

        return count;
    }

    private static void setFixedWidth(javax.swing.table.TableColumn col, int w) {
        col.setMinWidth(w);
        col.setMaxWidth(w);
        col.setPreferredWidth(w);
    }

    /** Style a table button as a compact, flat chip. */
    private static void chipify(JButton b) {
        b.setFont(b.getFont().deriveFont(Font.PLAIN, 11f));
        b.setMargin(new Insets(1, 6, 1, 6));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
    }

    private class RowTableModel extends AbstractTableModel {

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return cols == null ? 0 : cols.size(); }
        @Override public String getColumnName(int column) {
            return cols == null ? "" : cols.get(column).header;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            Col col = cols.get(columnIndex);

            if (row.minorBlock) {
                return switch (col.kind) {
                    case FIELD -> "Minor fields";
                    case TYPE -> allMinorFieldsBox.isSelected() ? "all"
                            : countSelectedMinorFields() + " selected";
                    case USE -> allMinorFieldsBox.isSelected();
                    case EXPAND -> "Open...";
                    default -> "";
                };
            }

            return switch (col.kind) {
                case FIELD -> row.depth > 0 ? "    ".repeat(row.depth) + row.fieldName
                        : row.fieldName;
                case TYPE -> row.typeLabel;
                case EXTRA -> row.container ? "" : col.extra.value(row.path);
                case USE -> row.use;
                case UP -> "▴";
                case DOWN -> "▾";
                case ACTION -> row.container ? "" : col.action.label(row.path);
                case EXPAND -> row.nestedClass == null ? ""
                        : row.childEditor == null ? "＋ fields" : "✎ edit";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            Col col = cols.get(columnIndex);

            if (row.minorBlock) {
                return col.kind == ColKind.USE || col.kind == ColKind.EXPAND;
            }

            return switch (col.kind) {
                case USE, UP, DOWN -> true;
                case ACTION -> !row.container && col.action.enabled(row.path);
                case EXPAND -> row.nestedClass != null;
                default -> false;
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            Row row = rows.get(rowIndex);
            Col col = cols.get(columnIndex);

            if (col.kind != ColKind.USE) {
                return;
            }

            if (row.minorBlock) {
                allMinorFieldsBox.setSelected(Boolean.TRUE.equals(value));
                fireTableRowsUpdated(rowIndex, rowIndex);
                fireConfigChanged();
                return;
            }

            row.use = Boolean.TRUE.equals(value);
            if (!row.use) {
                row.childEditor = null;
            }

            fireTableRowsUpdated(rowIndex, rowIndex);
            fireConfigChanged();
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return cols != null && cols.get(columnIndex).kind == ColKind.USE
                    ? Boolean.class : String.class;
        }
    }

    private class RowRenderer implements TableCellRenderer {
        private final TableCellRenderer delegate;

        RowRenderer(TableCellRenderer delegate) {
            this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
            int modelRow = table.convertRowIndexToModel(row);
            Row r = rows.get(modelRow);

            boolean useCol = cols.get(table.convertColumnIndexToModel(column)).kind == ColKind.USE;
            if (r.special && !useCol) {
                JLabel label = new JLabel(value == null ? "" : value.toString());
                label.setOpaque(true);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                label.setBackground(new Color(235, 235, 235));
                label.setForeground(Color.DARK_GRAY);
                return label;
            }

            Component c = delegate.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            // A synthesized container (an ancestor that isn't itself a pickable field)
            // reads dim, so it's visibly not a choice.
            if (r.container && !isSelected) {
                c.setForeground(Color.GRAY);
            }
            return c;
        }
    }

    private class ButtonRenderer extends JButton implements TableCellRenderer {
        ButtonRenderer() {
            setOpaque(true);
            chipify(this);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table,
                                                       Object value,
                                                       boolean isSelected,
                                                       boolean hasFocus,
                                                       int row,
                                                       int column) {
            int modelRow = table.convertRowIndexToModel(row);
            Row r = rows.get(modelRow);

            setText(value == null ? "" : value.toString());
            setEnabled(value != null
                    && !value.toString().isEmpty()
                    && (!r.special || r.minorBlock));

            return this;
        }
    }

    private class ButtonEditor extends AbstractCellEditor implements TableCellEditor {
        private final JButton button = new JButton();
        private Row currentRow;
        private Col currentCol;
        private boolean opening = false;

        ButtonEditor() {
            chipify(button);
            button.addActionListener(e -> {
                if (opening) return;

                Row row = currentRow;
                Col col = currentCol;

                fireEditingStopped();

                if (row == null || col == null) return;

                if (row.minorBlock && col.kind == ColKind.EXPAND) {
                    opening = true;
                    SwingUtilities.invokeLater(() -> {
                        try {
                            openMinorEditor(row);
                        } finally {
                            opening = false;
                        }
                    });
                    return;
                }

                if (row.special) return;

                switch (col.kind) {
                    case UP -> moveRow(row, -1);
                    case DOWN -> moveRow(row, 1);
                    case ACTION -> {
                        if (!row.container) {
                            col.action.run(row.path);
                        }
                    }
                    case EXPAND -> {
                        opening = true;
                        SwingUtilities.invokeLater(() -> {
                            try {
                                openChildEditor(row);
                            } finally {
                                opening = false;
                            }
                        });
                    }
                    default -> { }
                }
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table,
                                                     Object value,
                                                     boolean isSelected,
                                                     int viewRow,
                                                     int viewColumn) {
            int modelRow = table.convertRowIndexToModel(viewRow);

            currentRow = rows.get(modelRow);
            currentCol = cols.get(table.convertColumnIndexToModel(viewColumn));

            button.setText(value == null ? "" : value.toString());
            button.setEnabled(value != null && !value.toString().isEmpty());

            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }
    }

    private static class Row {
        final boolean special;
        final boolean minorBlock;
        final Field field;                 // null for a dynamic (map-held) field
        final String fieldName;
        final String typeLabel;
        final Class<? extends Viewable> nestedClass;
        final Viewable nestedSample;        // a sample of the referenced value (dynamic)

        // Flat path-row (single-select picker) attributes; for reflective rows the
        // path IS the field name, depth 0, never a container.
        final String path;
        final int depth;
        final boolean container;            // a synthesized ancestor, not pickable

        boolean use;
        ViewConfigEditor childEditor;
        FieldTypeSource nestedTypeSource;   // authoritative types for the child (dynamic)
        String nestedLabel;                 // model class name for the expand caption

        private Row(boolean special, boolean minorBlock, Field field, String fieldName,
                    String typeLabel, Class<? extends Viewable> nestedClass,
                    Viewable nestedSample, String path, int depth, boolean container) {
            this.special = special;
            this.minorBlock = minorBlock;
            this.field = field;
            this.fieldName = fieldName;
            this.typeLabel = typeLabel;
            this.nestedClass = nestedClass;
            this.nestedSample = nestedSample;
            this.path = path;
            this.depth = depth;
            this.container = container;
        }

        boolean isMinor() {
            return field != null && ViewableAdapter.isMinorField(field);
        }

        static Row minorBlock() {
            return new Row(true, true, null, "Minor fields", "", null, null,
                    "Minor fields", 0, false);
        }

        static Row field(Field field, String typeLabel, Class<? extends Viewable> nestedClass) {
            return new Row(false, false, field, field.getName(), typeLabel, nestedClass, null,
                    field.getName(), 0, false);
        }

        static Row dynamic(String name, String typeLabel,
                           Class<? extends Viewable> nestedClass, Viewable nestedSample) {
            return new Row(false, false, null, name, typeLabel, nestedClass, nestedSample,
                    name, 0, false);
        }

        static Row path(String label, String path, int depth, boolean container,
                        String typeLabel) {
            return new Row(false, false, null, label, typeLabel, null, null,
                    path, depth, container);
        }
    }
}
