package objectview.viewconfig;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.DynamicFields;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Swing orchestration layer for editing a {@link ViewConfig} or selecting fields.
 *
 * <p>Field discovery is delegated to {@link FieldRowSource}; presentation and
 * interaction are delegated to {@link FieldTableContributor}. This class owns only
 * editor state, JTable behavior, nested editor dialogs and conversion back to
 * {@link ViewConfig}.
 */
public class ViewConfigEditor extends JPanel {

    private ViewConfig sourceConfig;
    private final boolean nestedDefaultNameOnly;
    private final boolean minorOnly;
    private Viewable sample;
    private final FieldTableContributor contributor;

    private FieldRowSource rowSource;
    private Set<String> hiddenFields = Set.of();
    private FieldTypeSource typeSource;
    private boolean hideMedia;

    // Inline collapsible nesting: references expand in place instead of a dialog.
    // On for a single-select ConfigFieldRowSource table (pick-one / coverage); the
    // multi-check config editors keep the Expand dialog until stage 2.
    private boolean treeMode;
    private final Set<String> expandedPaths = new java.util.HashSet<>();
    // The whole discovered tree; `rows` is the currently VISIBLE subset of it.
    private final List<RowState> allRows = new ArrayList<>();
    private static final int MAX_TREE_DEPTH = 6;

    private final List<RowState> rows = new ArrayList<>();
    private final RowTableModel tableModel = new RowTableModel();
    private final JTable table = new JTable(tableModel) {
        @Override
        public void changeSelection(int rowIndex, int columnIndex,
                                    boolean toggle, boolean extend) {
            // Clicking a move-target must NOT move the highlight: the highlighted row
            // is the move SOURCE, and it has to survive the click that places it.
            if (columnIndex >= 0 && cols != null) {
                int m = convertColumnIndexToModel(columnIndex);
                if (m >= 0 && m < cols.size()) {
                    ColKind k = cols.get(m).kind;
                    if (k == ColKind.MOVE_BEFORE || k == ColKind.MOVE_AFTER) {
                        return;
                    }
                }
            }
            super.changeSelection(rowIndex, columnIndex, toggle, extend);
        }
    };
    private final JCheckBox allMinorFieldsBox =
            new JCheckBox("All minor fields");
    private final JPanel minorFieldsBar =
            new JPanel(new FlowLayout(FlowLayout.LEFT));

    private List<Col> cols;
    private Runnable changeListener;

    // ---- construction ------------------------------------------------------

    public ViewConfigEditor(ViewConfig config) {
        this(config, false, false, null,
                FieldTableContributor.DEFAULT,
                ConfigFieldRowSource.INSTANCE);
    }

    public ViewConfigEditor(ViewConfig config,
                            boolean nestedDefaultNameOnly) {
        this(config, nestedDefaultNameOnly, false, null,
                FieldTableContributor.DEFAULT,
                ConfigFieldRowSource.INSTANCE);
    }

    /** Dynamic: enumerate {@code sample}'s map-held fields. */
    public ViewConfigEditor(ViewConfig config,
                            Viewable sample) {
        this(config, false, false, sample,
                FieldTableContributor.DEFAULT,
                ConfigFieldRowSource.INSTANCE);
    }

    public ViewConfigEditor(ViewConfig config,
                            boolean nestedDefaultNameOnly,
                            Viewable sample) {
        this(config, nestedDefaultNameOnly, false, sample,
                FieldTableContributor.DEFAULT,
                ConfigFieldRowSource.INSTANCE);
    }

    public ViewConfigEditor(ViewConfig config,
                            FieldTableContributor contributor) {
        this(config, false, false, null,
                contributor,
                ConfigFieldRowSource.INSTANCE);
    }

    public ViewConfigEditor(ViewConfig config,
                            Viewable sample,
                            FieldTableContributor contributor) {
        this(config, false, false, sample,
                contributor,
                ConfigFieldRowSource.INSTANCE);
    }

    public ViewConfigEditor(ViewConfig config,
                            boolean nestedDefaultNameOnly,
                            Viewable sample,
                            FieldTableContributor contributor) {
        this(config, nestedDefaultNameOnly, false, sample,
                contributor,
                ConfigFieldRowSource.INSTANCE);
    }

    /**
     * Creates a path-row table. Call {@link #setPathRows} to supply its rows.
     */
    public ViewConfigEditor(FieldTableContributor contributor) {
        this(new ViewConfig(), false, false, null,
                contributor,
                new PathFieldRowSource(List.of(), Set.of(), null));
    }

    /**
     * Advanced constructor allowing a custom row source.
     */
    public ViewConfigEditor(ViewConfig config,
                            boolean nestedDefaultNameOnly,
                            Viewable sample,
                            FieldTableContributor contributor,
                            FieldRowSource rowSource) {
        this(config, nestedDefaultNameOnly, false, sample,
                contributor, rowSource);
    }

    private ViewConfigEditor(ViewConfig config,
                             boolean nestedDefaultNameOnly,
                             boolean minorOnly,
                             Viewable sample,
                             FieldTableContributor contributor,
                             FieldRowSource rowSource) {
        this.sourceConfig =
                config == null ? new ViewConfig() : config.copy();
        this.nestedDefaultNameOnly = nestedDefaultNameOnly;
        this.minorOnly = minorOnly;
        this.sample = sample;
        this.contributor = contributor == null
                ? FieldTableContributor.DEFAULT
                : contributor;
        this.rowSource = rowSource == null
                ? ConfigFieldRowSource.INSTANCE
                : rowSource;
        // Inline tree for every config-source table — multi-check (search/sort/view)
        // and single-select (pick-one/coverage) alike, so a field looks the same in
        // all of them. A path-row table stays flat.
        this.treeMode = this.rowSource instanceof ConfigFieldRowSource;
        this.cols = buildColumns();

        // JTable was constructed before `cols` existed.
        tableModel.fireTableStructureChanged();

        setLayout(new BorderLayout(8, 8));

        // Always install the "All minor fields" bar + wire it once; its VISIBILITY and
        // checked state track the current row source (updated on every switch).
        allMinorFieldsBox.addActionListener(e -> {
            tableModel.fireTableDataChanged();
            fireConfigChanged();
        });
        minorFieldsBar.add(allMinorFieldsBox);
        add(minorFieldsBar, BorderLayout.NORTH);
        updateMinorFieldsBar();

        rebuildRows(false);

        table.setRowHeight(28);
        table.setFillsViewportHeight(true);

        boolean singleMode = this.contributor.selectionMode()
                == FieldTableContributor.SelectionMode.SINGLE;
        // Reorder makes the highlighted row the move source, so its highlight must be
        // single-row too (checkboxes still drive multi 'use' independently).
        if (singleMode || this.contributor.showReorder()) {
            table.setSelectionMode(
                    ListSelectionModel.SINGLE_SELECTION);
            table.getSelectionModel()
                    .addListSelectionListener(e -> {
                        if (e.getValueIsAdjusting()) {
                            return;
                        }
                        if (this.contributor.showReorder()) {
                            // The before/after targets follow the selected source.
                            table.repaint();
                        }
                        if (singleMode) {
                            fireConfigChanged();
                        }
                    });
        }

        installColumns();

        table.setDefaultRenderer(
                Object.class,
                new RowRenderer(
                        table.getDefaultRenderer(Object.class)));
        table.setDefaultRenderer(
                Boolean.class,
                new RowRenderer(
                        table.getDefaultRenderer(Boolean.class)));

        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    // ---- row-source configuration -----------------------------------------

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    public void setHiddenFields(Set<String> fieldNames) {
        hiddenFields = fieldNames == null
                ? Set.of()
                : Set.copyOf(fieldNames);
        rebuildRows(true);
    }

    public void setFieldTypes(FieldTypeSource source) {
        typeSource = source;
        rebuildRows(true);
    }

    public void setHideMedia(boolean hideMedia) {
        this.hideMedia = hideMedia;
        rebuildRows(true);
    }

    /**
     * Replaces the row source with an explicit dotted-path source.
     */
    public void setPathRows(List<String> paths,
                            Set<String> hiddenTop,
                            Function<String, String> typeLabelForPath) {
        rowSource = new PathFieldRowSource(
                paths,
                hiddenTop,
                typeLabelForPath);
        refreshForRowSource();
        rebuildRows(false);
        table.clearSelection();
    }

    /**
     * Switches to config-based enumeration for a new type — the shared reflective /
     * dynamic discovery ({@link ConfigFieldRowSource}) so the field set, order and
     * type labels match the search/sort/view editors. For the single-select coverage /
     * picker table, which then renders references as an inline collapsible tree.
     */
    public void setConfigRows(ViewConfig config,
                              Viewable sample,
                              FieldTypeSource types,
                              Set<String> hidden) {
        this.sourceConfig = config == null ? new ViewConfig() : config.copy();
        this.sample = sample;
        this.typeSource = types;
        this.hiddenFields = hidden == null ? Set.of() : Set.copyOf(hidden);
        this.rowSource = ConfigFieldRowSource.INSTANCE;
        this.expandedPaths.clear();
        refreshForRowSource();
        rebuildRows(false);
        table.clearSelection();
    }

    /** Re-derive everything that depends on the row-source TYPE after a switch: tree
     *  vs flat mode, the column set (a tree adds the disclosure gutter and drops the
     *  Expand column), and the table's structure — otherwise {@link #rebuildRows}
     *  branches on a stale {@code treeMode} and the columns stay stale. */
    private void refreshForRowSource() {
        this.treeMode = rowSource instanceof ConfigFieldRowSource;
        this.cols = buildColumns();
        tableModel.fireTableStructureChanged();
        installColumns();
        updateMinorFieldsBar();
    }

    /** The "All minor fields" bar is meaningful only for a non-minor config table over
     *  a reflected (not dynamic) sample; show/hide it AND refresh its checked state so
     *  both follow a row-source switch, not just the value seeded at construction. */
    private void updateMinorFieldsBar() {
        boolean applicable = !minorOnly && usesConfigRows()
                && !(sample instanceof DynamicFields);
        minorFieldsBar.setVisible(applicable);
        // When it doesn't apply, force OFF so getConfig() can't read a stale 'selected'.
        allMinorFieldsBox.setSelected(
                applicable && sourceConfig.isAllMinorFields());
        revalidate();
        repaint();
    }

    private boolean usesConfigRows() {
        return rowSource instanceof ConfigFieldRowSource;
    }

    /** Visible for tests: whether the current row source renders as an inline tree
     *  (recomputed on every row-source switch). */
    boolean inTreeMode() {
        return treeMode;
    }

    /** Visible for tests: whether the "All minor fields" bar is currently shown. */
    boolean minorFieldsBarVisible() {
        return minorFieldsBar.isVisible();
    }

    private FieldRowContext rowContext() {
        return new FieldRowContext(
                sourceConfig,
                sample,
                minorOnly,
                hideMedia,
                hiddenFields,
                typeSource);
    }

    /**
     * Rebuilds immutable row descriptors while preserving mutable state by path.
     */
    private void rebuildRows(boolean preserveState) {
        Map<String, RowSnapshot> old = preserveState
                ? snapshotRows()
                : Map.of();

        if (treeMode) {
            allRows.clear();
            buildTree("", 0, rowContext(), new java.util.HashSet<>());
            for (RowState state : allRows) {
                RowSnapshot snapshot = old.get(state.row.path());
                if (snapshot != null) {
                    state.use = snapshot.use;
                    state.childEditor = snapshot.childEditor;
                    state.customConfigured = snapshot.customConfigured;
                }
            }
            rebuildVisible();
            return;
        }

        rows.clear();
        for (FieldRow row : rowSource.rows(rowContext())) {
            RowState state = createState(row);
            RowSnapshot snapshot = old.get(row.path());

            if (snapshot != null) {
                state.use = snapshot.use;
                state.childEditor = snapshot.childEditor;
                state.customConfigured =
                        snapshot.customConfigured;
            }

            rows.add(state);
        }

        tableModel.fireTableDataChanged();
    }

    // ---- inline tree (treeMode) -------------------------------------------

    /** Discovers the whole tree into {@link #allRows}: top-level rows from the source,
     *  then each reference's children (re-pathed under it) recursively, bounded by a
     *  depth cap and a cycle guard (a nested type already on the chain stops). */
    private void buildTree(String parentPath, int depth,
                           FieldRowContext context, Set<Class<?>> chain) {
        for (FieldRow raw : rowSource.rows(context)) {
            if (raw.isMinorBlock()) {
                // Multi-check reflection keeps the minor-fields block as a top-level row
                // (its own small dialog); single-select tables omit it.
                if (depth == 0 && contributor.selectionMode()
                        == FieldTableContributor.SelectionMode.MULTI_CHECK) {
                    allRows.add(new RowState(raw));
                }
                continue;
            }
            if (!raw.isField()) {
                continue;   // no synthesized containers in a config-source tree
            }
            String full = parentPath.isEmpty()
                    ? raw.path()
                    : parentPath + "." + raw.path();
            FieldRow placed = raw.at(full, depth);
            RowState state = new RowState(placed);
            state.use = checkedInSource(placed);
            allRows.add(state);

            NestedFieldSource nested = placed.nested();
            if (nested != null
                    && depth < MAX_TREE_DEPTH
                    && !chain.contains(nested.type())) {
                Set<Class<?>> next = new java.util.HashSet<>(chain);
                next.add(nested.type());
                buildTree(full, depth + 1, childContext(nested), next);
            }
        }
    }

    /** Whether {@code row}'s field is currently selected in {@link #sourceConfig},
     *  walking nested configs down its full path (so an existing/saved config restores
     *  checked state at every level). */
    private boolean checkedInSource(FieldRow row) {
        String[] segments = row.path().split("\\.");
        ViewConfig config = sourceConfig;
        for (int i = 0; i < segments.length - 1; i++) {
            ViewConfig child = config.getFieldConfig(segments[i]);
            if (child == null) {
                return config.isAllFields();
            }
            config = child;
        }
        return config.showsFieldByName(segments[segments.length - 1]);
    }

    private FieldRowContext childContext(NestedFieldSource nested) {
        return new FieldRowContext(
                ViewConfig.all(nested.type()),
                nested.sample(),
                false,
                hideMedia,
                Set.of(),
                nested.fieldTypes());
    }

    /** Recomputes the visible rows from {@link #allRows}: a row shows only when every
     *  ancestor on its path is expanded. */
    private void rebuildVisible() {
        rows.clear();
        for (RowState state : allRows) {
            if (isVisible(state)) {
                rows.add(state);
            }
        }
        tableModel.fireTableDataChanged();
    }

    private boolean isVisible(RowState state) {
        String path = state.row.path();
        int dot = path.lastIndexOf('.');
        while (dot >= 0) {
            String ancestor = path.substring(0, dot);
            if (!expandedPaths.contains(ancestor)) {
                return false;
            }
            dot = ancestor.lastIndexOf('.');
        }
        return true;
    }

    private void toggleExpand(String path) {
        if (!expandedPaths.remove(path)) {
            expandedPaths.add(path);
        }
        rebuildVisible();
    }

    private Map<String, RowSnapshot> snapshotRows() {
        Map<String, RowSnapshot> result =
                new LinkedHashMap<>();

        for (RowState state : (treeMode ? allRows : rows)) {
            result.put(
                    state.row.path(),
                    new RowSnapshot(
                            state.use,
                            state.childEditor,
                            state.customConfigured));
        }

        return result;
    }

    private RowState createState(FieldRow row) {
        RowState state = new RowState(row);

        if (row.isField()) {
            state.use = row.field() != null
                    ? sourceConfig.showsField(row.field())
                    : sourceConfig.showsFieldByName(row.path());

            ViewConfig selected =
                    sourceConfig.getFieldConfig(row.path());
            state.customConfigured =
                    selected != null
                            && row.nested() != null
                            && !selected.getFields().isEmpty();
        }

        return state;
    }

    private void fireConfigChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    // ---- public selection/config API --------------------------------------

    public String selectedPath() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }

        RowState state = rows.get(
                table.convertRowIndexToModel(viewRow));
        return state.row.isField()
                ? state.row.path()
                : null;
    }

    /** The selected row's {@link FieldRow} (carrying its leaf {@code Field} / nested
     *  source), so a caller can build its own model object from the chosen field. */
    public FieldRow selectedRow() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        RowState state = rows.get(
                table.convertRowIndexToModel(viewRow));
        return state.row.isField() ? state.row : null;
    }

    public void setSelectedPath(String dottedPath) {
        if (dottedPath == null) {
            table.clearSelection();
            return;
        }

        // In the tree, the target may be under collapsed ancestors — open them first.
        if (treeMode) {
            boolean changed = false;
            String path = dottedPath;
            int dot = path.lastIndexOf('.');
            while (dot >= 0) {
                String ancestor = path.substring(0, dot);
                if (expandedPaths.add(ancestor)) {
                    changed = true;
                }
                dot = ancestor.lastIndexOf('.');
            }
            if (changed) {
                rebuildVisible();
            }
        }

        for (int modelRow = 0;
             modelRow < rows.size();
             modelRow++) {
            if (!dottedPath.equals(
                    rows.get(modelRow).row.path())) {
                continue;
            }

            int viewRow =
                    table.convertRowIndexToView(modelRow);
            if (viewRow >= 0) {
                table.setRowSelectionInterval(
                        viewRow,
                        viewRow);
                table.scrollRectToVisible(
                        table.getCellRect(
                                viewRow,
                                0,
                                true));
            }
            return;
        }

        table.clearSelection();
    }

    public List<String> selectedFieldPaths() {
        List<String> result = new ArrayList<>();
        collectSelected("", result);
        return result;
    }

    private void collectSelected(String prefix,
                                 List<String> result) {
        if (treeMode) {
            for (RowState state : allRows) {
                if (state.row.isField() && state.use) {
                    result.add(prefix.isEmpty()
                            ? state.row.path()
                            : prefix + "." + state.row.path());
                }
            }
            return;
        }
        for (RowState state : rows) {
            if (!state.row.isField() || !state.use) {
                continue;
            }

            String path = prefix.isEmpty()
                    ? state.row.path()
                    : prefix + "." + state.row.path();

            if (state.childEditor != null) {
                state.childEditor.collectSelected(
                        path,
                        result);
            } else {
                result.add(path);
            }
        }
    }

    public ViewConfig getConfig() {
        if (treeMode) {
            return buildTreeConfig();
        }
        ViewConfig result = copyHeader(sourceConfig);
        result.setAllFields(false);
        result.setAllMinorFields(
                !minorOnly
                        && allMinorFieldsBox.isSelected());
        result.getFields().clear();

        for (RowState state : rows) {
            FieldRow row = state.row;
            if (!row.isField() || !state.use) {
                continue;
            }

            if (!minorOnly
                    && result.isAllMinorFields()
                    && row.isMinor()
                    && sourceConfig.getFieldConfig(
                            row.path()) == null) {
                continue;
            }

            result.addField(
                    row.path(),
                    childConfigFor(state));
        }

        if (!minorOnly) {
            RowState minorBlock = findMinorBlock();
            if (minorBlock != null
                    && minorBlock.childEditor != null) {
                ViewConfig minorConfig =
                        minorBlock.childEditor.getConfig();

                result.setAllMinorFields(
                        result.isAllMinorFields()
                                || minorConfig
                                .isAllMinorFields());

                for (Map.Entry<String, ViewConfig> entry
                        : minorConfig.getFields()
                        .entrySet()) {
                    result.addField(
                            entry.getKey(),
                            entry.getValue());
                }
            }
        }

        return result;
    }

    /** Folds the checked rows of the inline tree ({@link #allRows}) into a nested
     *  {@link ViewConfig}: leaves add a leaf field to their parent; a reference is
     *  included when it is checked OR any descendant is, carrying its checked children
     *  (or all its fields when checked but not drilled into). References attach
     *  deepest-first so a parent sees its already-attached descendants. */
    private ViewConfig buildTreeConfig() {
        ViewConfig result = copyHeader(sourceConfig);
        result.setAllFields(false);
        result.setAllMinorFields(
                !minorOnly && allMinorFieldsBox.isSelected());
        result.getFields().clear();

        int maxDepth = 0;
        for (RowState state : allRows) {
            maxDepth = Math.max(maxDepth, state.row.depth());
        }
        // parents[d] = the config that a depth-d field attaches to (pre-order fills it).
        ViewConfig[] parents = new ViewConfig[maxDepth + 2];
        parents[0] = result;

        List<RefEntry> refs = new ArrayList<>();
        for (RowState state : allRows) {
            FieldRow row = state.row;
            if (row.isMinorBlock()) {
                continue;
            }
            int depth = row.depth();
            String name = row.label();
            if (row.nested() != null) {
                // Seed from the reference's saved config HEADER so its own metadata
                // (thumb / answerType / display flags) survives even when we then list
                // its checked children explicitly.
                ViewConfig explicit = explicitConfigFor(state, row.path());
                ViewConfig cfg = explicit != null
                        ? copyHeader(explicit)
                        : new ViewConfig();
                cfg.setCls(row.nested().type());
                cfg.setAllFields(false);
                parents[depth + 1] = cfg;
                refs.add(new RefEntry(
                        parents[depth], name, cfg, state.use, row.nested().type(),
                        row.path(), state, explicit));
            } else if (state.use) {
                // A checked leaf carries its explicit config (nested editor or saved
                // per-field config) so display flags survive; else a bare leaf.
                ViewConfig explicit = explicitConfigFor(state, row.path());
                parents[depth].addField(
                        name, explicit != null ? explicit : ViewConfig.leaf());
            }
        }

        // Deepest-first: a reference's checked descendants are already in its cfg.
        for (int i = refs.size() - 1; i >= 0; i--) {
            RefEntry ref = refs.get(i);
            boolean hasChild = !ref.cfg.getFields().isEmpty();
            if (!ref.use && !hasChild) {
                continue;
            }
            ViewConfig attach;
            if (hasChild) {
                attach = ref.cfg;   // header (from explicit) + inline-checked children
            } else {
                // Checked with no checked children: honor an explicit config — a nested
                // editor, or a saved config at this path even when EXPLICITLY EMPTY —
                // instead of blowing it up to all-fields. Only a brand-new, never-
                // configured reference defaults to all-fields for convenience.
                attach = ref.explicit != null ? ref.explicit : ViewConfig.of(ref.type);
            }
            ref.parent.addField(ref.name, attach);
        }

        if (!minorOnly) {
            RowState minorBlock = findMinorBlock();
            if (minorBlock != null && minorBlock.childEditor != null) {
                ViewConfig minorConfig = minorBlock.childEditor.getConfig();
                result.setAllMinorFields(
                        result.isAllMinorFields()
                                || minorConfig.isAllMinorFields());
                for (Map.Entry<String, ViewConfig> entry
                        : minorConfig.getFields().entrySet()) {
                    result.addField(entry.getKey(), entry.getValue());
                }
            }
        }

        return result;
    }

    private record RefEntry(
            ViewConfig parent,
            String name,
            ViewConfig cfg,
            boolean use,
            Class<? extends Viewable> type,
            String fullPath,
            RowState state,
            ViewConfig explicit) {
    }

    /** The config a tree row should serialize with when it is checked but not drilled:
     *  a live nested editor wins (bug: was dropped), else the saved config at this path
     *  in {@code sourceConfig} — returned even when EMPTY so an explicit empty survives
     *  the round-trip. {@code null} means "no explicit config" (a brand-new field). */
    private ViewConfig explicitConfigFor(RowState state, String fullPath) {
        if (state != null && state.childEditor != null) {
            return state.childEditor.getConfig();
        }
        return sourceConfigAt(fullPath);
    }

    /** Walk {@code sourceConfig} down a dotted path to the field config at its leaf, or
     *  {@code null} if no config is declared at any segment. */
    private ViewConfig sourceConfigAt(String dottedPath) {
        ViewConfig cfg = sourceConfig;
        for (String seg : dottedPath.split("\\.")) {
            if (cfg == null) {
                return null;
            }
            cfg = cfg.getFieldConfig(seg);
        }
        return cfg == null ? null : cfg.copy();
    }

    private ViewConfig childConfigFor(RowState state) {
        if (state.childEditor != null) {
            return state.childEditor.getConfig();
        }

        ViewConfig existing =
                sourceConfig.getFieldConfig(
                        state.row.path());

        if (state.customConfigured && existing != null) {
            return existing.copy();
        }

        if (state.row.nested() != null) {
            return ViewConfig.of(
                    state.row.nested().type());
        }

        return ViewConfig.leaf();
    }

    private ViewConfig copyHeader(ViewConfig source) {
        ViewConfig result = new ViewConfig();
        if (source == null) {
            return result;
        }

        result.setCls(source.getCls());
        result.setAllFields(source.isAllFields());
        result.setAllMinorFields(source.isAllMinorFields());
        result.setAddListener(source.isAddListener());
        result.setThumb(source.isThumb());
        result.setAnswerType(source.getAnswerType());
        result.setBlurImages(source.isBlurImages());
        return result;
    }

    // ---- columns -----------------------------------------------------------

    private enum ColKind {
        TREE, FIELD, TYPE, EXTRA, USE, MOVE_BEFORE, MOVE_AFTER, ACTION, EXPAND
    }

    private static final class Col {
        final ColKind kind;
        final String header;
        final int width;
        final FieldTableContributor.ExtraColumn extra;
        final FieldTableContributor.RowAction action;

        Col(ColKind kind,
            String header,
            int width) {
            this(kind, header, width, null, null);
        }

        Col(ColKind kind,
            String header,
            int width,
            FieldTableContributor.ExtraColumn extra,
            FieldTableContributor.RowAction action) {
            this.kind = kind;
            this.header = header;
            this.width = width;
            this.extra = extra;
            this.action = action;
        }

        boolean button() {
            return kind == ColKind.TREE
                    || kind == ColKind.MOVE_BEFORE
                    || kind == ColKind.MOVE_AFTER
                    || kind == ColKind.ACTION
                    || kind == ColKind.EXPAND;
        }

        boolean fixedWidth() {
            return kind == ColKind.TREE
                    || kind == ColKind.MOVE_BEFORE
                    || kind == ColKind.MOVE_AFTER;
        }
    }

    private List<Col> buildColumns() {
        List<Col> result = new ArrayList<>();
        if (treeMode) {
            // The leftmost gutter: a ▸/▾ disclosure toggle for reference rows.
            result.add(new Col(ColKind.TREE, "", 26));
        }
        result.add(new Col(
                ColKind.FIELD,
                "Field",
                260));
        result.add(new Col(
                ColKind.TYPE,
                "Type",
                160));

        for (FieldTableContributor.ExtraColumn extra
                : contributor.columns()) {
            result.add(new Col(
                    ColKind.EXTRA,
                    extra.header(),
                    extra.width(),
                    extra,
                    null));
        }

        if (contributor.showUse()) {
            result.add(new Col(
                    ColKind.USE,
                    "Use",
                    48));
        }

        if (contributor.showReorder()) {
            result.add(new Col(
                    ColKind.MOVE_BEFORE,
                    "",
                    30));
            result.add(new Col(
                    ColKind.MOVE_AFTER,
                    "",
                    30));
        }

        for (FieldTableContributor.RowAction action
                : contributor.actions()) {
            result.add(new Col(
                    ColKind.ACTION,
                    "",
                    120,
                    null,
                    action));
        }

        if (!treeMode && contributor.showExpand()) {
            result.add(new Col(
                    ColKind.EXPAND,
                    "Expand",
                    72));
        }

        return result;
    }

    private void installColumns() {
        for (int i = 0; i < cols.size(); i++) {
            Col col = cols.get(i);
            javax.swing.table.TableColumn tableColumn =
                    table.getColumnModel().getColumn(i);

            if (col.fixedWidth()) {
                setFixedWidth(
                        tableColumn,
                        col.width);
            } else {
                tableColumn.setPreferredWidth(
                        col.width);
            }

            if (col.button()) {
                tableColumn.setCellRenderer(
                        new ButtonRenderer());
                tableColumn.setCellEditor(
                        new ButtonEditor());
            }
        }
    }

    // ---- interaction -------------------------------------------------------

    /** Click-to-target reorder: move the SELECTED row (the source) to just before or
     *  after {@code target} — a sibling revealed by selecting the source. The whole
     *  subtree travels in tree mode; selection stays on the moved row so moves chain. */
    private void moveSelectedTo(RowState target, boolean before) {
        RowState source = selectedRowState();
        if (source == null
                || source == target
                || !isReorderTarget(target)) {
            return;
        }

        if (treeMode) {
            moveSubtree(source, target, before);
            rebuildVisible();
        } else {
            moveFlatRow(source, target, before);
            tableModel.fireTableDataChanged();
        }

        setSelectedPath(source.row.path());
        fireConfigChanged();
    }

    /** Reparent-free move within {@link #allRows}: lift the source's contiguous subtree
     *  and drop it at the target's leading (before) or trailing (after) edge. */
    private void moveSubtree(RowState source, RowState target, boolean before) {
        int si = allRows.indexOf(source);
        if (si < 0) {
            return;
        }
        List<RowState> block =
                new ArrayList<>(allRows.subList(si, subtreeEnd(si)));
        allRows.subList(si, si + block.size()).clear();

        int ti = allRows.indexOf(target);
        if (ti < 0) {          // target vanished (shouldn't happen) — put it back
            allRows.addAll(Math.min(si, allRows.size()), block);
            return;
        }
        allRows.addAll(before ? ti : subtreeEnd(ti), block);
    }

    private void moveFlatRow(RowState source, RowState target, boolean before) {
        rows.remove(source);
        int ti = rows.indexOf(target);
        rows.add(ti < 0 ? rows.size() : (before ? ti : ti + 1), source);
    }

    /** End (exclusive) of the contiguous subtree rooted at {@code allRows[i]}: the run
     *  of following rows deeper than it. */
    private int subtreeEnd(int i) {
        int depth = allRows.get(i).row.depth();
        int e = i + 1;
        while (e < allRows.size()
                && allRows.get(e).row.depth() > depth) {
            e++;
        }
        return e;
    }

    /** The highlighted row — the move source. */
    private RowState selectedRowState() {
        int viewRow = table.getSelectedRow();
        if (viewRow < 0) {
            return null;
        }
        int m = table.convertRowIndexToModel(viewRow);
        return m >= 0 && m < rows.size() ? rows.get(m) : null;
    }

    /** {@code candidate} is a drop target iff a source is selected and this is a
     *  DIFFERENT sibling of it (same parent) — reorder never re-parents. In flat mode
     *  any other field row is a valid slot. */
    private boolean isReorderTarget(RowState candidate) {
        if (!contributor.showReorder()) {
            return false;
        }
        RowState src = selectedRowState();
        if (src == null) {
            return false;
        }
        FieldRow s = src.row;
        FieldRow t = candidate.row;
        if (!s.isField() || !t.isField() || s.path().equals(t.path())) {
            return false;
        }
        return !treeMode
                || parentPath(s.path()).equals(parentPath(t.path()));
    }

    private static String parentPath(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "" : path.substring(0, dot);
    }

    private void openMinorEditor(RowState state) {
        if (!state.row.isMinorBlock()) {
            return;
        }

        if (state.childEditor == null) {
            ViewConfig config = sourceConfig.copy();
            config.setAllMinorFields(
                    allMinorFieldsBox.isSelected());

            state.childEditor = new ViewConfigEditor(
                    config,
                    nestedDefaultNameOnly,
                    true,
                    null,
                    nestedContributorFor(state.row),
                    ConfigFieldRowSource.INSTANCE);
        }

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                "Minor fields : "
                        + sourceConfig.getCls().getSimpleName(),
                Dialog.ModalityType.APPLICATION_MODAL);

        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(
                state.childEditor,
                BorderLayout.CENTER);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            ViewConfig config =
                    state.childEditor.getConfig();
            allMinorFieldsBox.setSelected(
                    config.isAllMinorFields());
            dialog.dispose();
            tableModel.fireTableDataChanged();
            fireConfigChanged();
        });

        JPanel buttons =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT));
        buttons.add(okButton);

        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void openChildEditor(RowState state) {
        FieldRow row = state.row;

        if (row.isMinorBlock()) {
            openMinorEditor(state);
            return;
        }
        if (!row.isField() || row.nested() == null) {
            return;
        }

        state.use = true;

        if (state.childEditor == null) {
            ViewConfig childConfig =
                    sourceConfig.getFieldConfig(row.path());

            if (childConfig == null
                    || childConfig.getCls() == null) {
                childConfig = nestedDefaultNameOnly
                        ? nameOnlyConfig(
                                row.nested().type())
                        : ViewConfig.all(
                                row.nested().type());
            } else {
                childConfig = childConfig.copy();
            }

            state.childEditor =
                    createChildEditor(
                            row,
                            childConfig);
        }

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                row.label()
                        + " : "
                        + row.nested()
                        .effectiveDisplayName(),
                Dialog.ModalityType.APPLICATION_MODAL);

        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(
                state.childEditor,
                BorderLayout.CENTER);

        JButton clearButton =
                new JButton("Clear custom config");
        JButton okButton = new JButton("OK");

        clearButton.addActionListener(e -> {
            state.childEditor = null;
            state.customConfigured = false;
            dialog.dispose();
            tableModel.fireTableDataChanged();
            fireConfigChanged();
        });

        okButton.addActionListener(e -> {
            state.customConfigured = true;
            dialog.dispose();
            tableModel.fireTableDataChanged();
            fireConfigChanged();
        });

        JPanel buttons =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT));
        buttons.add(clearButton);
        buttons.add(okButton);

        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private ViewConfigEditor createChildEditor(
            FieldRow row,
            ViewConfig childConfig) {
        NestedFieldSource nested = row.nested();

        ViewConfigEditor childEditor =
                new ViewConfigEditor(
                        childConfig,
                        nestedDefaultNameOnly,
                        false,
                        nested.sample(),
                        nestedContributorFor(row),
                        ConfigFieldRowSource.INSTANCE);

        if (nested.fieldTypes() != null) {
            childEditor.setFieldTypes(
                    nested.fieldTypes());
        }

        childEditor.setHideMedia(hideMedia);
        return childEditor;
    }

    private FieldTableContributor nestedContributorFor(
            FieldRow row) {
        FieldTableContributor nested =
                contributor.nestedContributor(row);
        return nested == null
                ? FieldTableContributor.DEFAULT
                : nested;
    }

    private ViewConfig nameOnlyConfig(
            Class<? extends Viewable> cls) {
        ViewConfig config = ViewConfig.of(cls);
        config.setAllFields(false);
        config.setAllMinorFields(false);
        config.setAddListener(false);
        config.setThumb(false);
        config.addField(
                "name",
                ViewConfig.leaf());
        return config;
    }

    private RowState findMinorBlock() {
        for (RowState state : rows) {
            if (state.row.isMinorBlock()) {
                return state;
            }
        }
        return null;
    }

    private int countSelectedMinorFields() {
        if (sourceConfig.isAllMinorFields()) {
            return -1;
        }

        int count = 0;
        Class<? extends Viewable> cls =
                sourceConfig.getCls();
        if (cls == null) {
            return 0;
        }

        for (Field field
                : ViewableAdapter.getAllFields(cls)) {
            if (ViewableAdapter.isMinorField(field)
                    && sourceConfig.getFieldConfig(
                            field.getName()) != null) {
                count++;
            }
        }

        RowState minorBlock = findMinorBlock();
        if (minorBlock != null
                && minorBlock.childEditor != null) {
            ViewConfig config =
                    minorBlock.childEditor.getConfig();
            count = 0;

            for (Field field
                    : ViewableAdapter.getAllFields(cls)) {
                if (ViewableAdapter.isMinorField(field)
                        && config.getFieldConfig(
                                field.getName()) != null) {
                    count++;
                }
            }
        }

        return count;
    }

    private static void setFixedWidth(
            javax.swing.table.TableColumn column,
            int width) {
        column.setMinWidth(width);
        column.setMaxWidth(width);
        column.setPreferredWidth(width);
    }

    private static void chipify(JButton button) {
        button.setFont(
                button.getFont().deriveFont(
                        Font.PLAIN,
                        11f));
        // Strip the LAF border (on Aqua its insets are large enough to clip a single
        // glyph in a ~30px column to "..."); a tiny empty border + margin leaves room.
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setMargin(new Insets(1, 2, 1, 2));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
    }

    private boolean isButtonEnabled(
            RowState state,
            Col col,
            Object value) {
        if (value == null
                || value.toString().isEmpty()) {
            return false;
        }

        FieldRow row = state.row;
        if (row.isContainer()) {
            return false;
        }

        return switch (col.kind) {
            case TREE ->
                    row.isMinorBlock() || row.nested() != null;
            case ACTION ->
                    row.isField()
                            && col.action.enabled(row);
            case EXPAND ->
                    row.isMinorBlock()
                            || row.nested() != null;
            case MOVE_BEFORE, MOVE_AFTER ->
                    isReorderTarget(state);
            default ->
                    true;
        };
    }

    // ---- table model / rendering ------------------------------------------

    private class RowTableModel extends AbstractTableModel {

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return cols == null ? 0 : cols.size();
        }

        @Override
        public String getColumnName(int column) {
            return cols == null
                    ? ""
                    : cols.get(column).header;
        }

        @Override
        public Object getValueAt(
                int rowIndex,
                int columnIndex) {
            RowState state = rows.get(rowIndex);
            FieldRow row = state.row;
            Col col = cols.get(columnIndex);

            if (row.isMinorBlock()) {
                return switch (col.kind) {
                    case FIELD -> "Minor fields";
                    case TYPE ->
                            allMinorFieldsBox.isSelected()
                                    ? "all"
                                    : countSelectedMinorFields()
                                    + " selected";
                    case USE ->
                            allMinorFieldsBox.isSelected();
                    case TREE, EXPAND -> "Open...";
                    default -> "";
                };
            }

            return switch (col.kind) {
                case TREE -> row.nested() == null
                        ? ""
                        : expandedPaths.contains(row.path())
                                ? "▾"
                                : "▸";
                case FIELD -> row.indentedLabel();
                case TYPE -> row.typeLabel();
                case EXTRA -> row.isContainer()
                        ? null
                        : col.extra.value(row);
                case USE -> state.use;
                case MOVE_BEFORE -> isReorderTarget(state) ? "▴" : "";
                case MOVE_AFTER -> isReorderTarget(state) ? "▾" : "";
                case ACTION -> row.isContainer()
                        ? ""
                        : col.action.label(row);
                case EXPAND -> row.nested() == null
                        ? ""
                        : state.childEditor == null
                                && !state.customConfigured
                                ? "＋ fields"
                                : "✎ edit";
            };
        }

        @Override
        public boolean isCellEditable(
                int rowIndex,
                int columnIndex) {
            RowState state = rows.get(rowIndex);
            FieldRow row = state.row;
            Col col = cols.get(columnIndex);

            if (row.isMinorBlock()) {
                return col.kind == ColKind.USE
                        || col.kind == ColKind.EXPAND
                        || col.kind == ColKind.TREE;
            }

            return switch (col.kind) {
                case TREE ->
                        row.nested() != null;
                case USE ->
                        row.isField();
                case MOVE_BEFORE, MOVE_AFTER ->
                        isReorderTarget(state);
                case ACTION ->
                        row.isField()
                                && col.action.enabled(row);
                case EXPAND ->
                        row.nested() != null;
                default ->
                        false;
            };
        }

        @Override
        public void setValueAt(
                Object value,
                int rowIndex,
                int columnIndex) {
            RowState state = rows.get(rowIndex);
            FieldRow row = state.row;
            Col col = cols.get(columnIndex);

            if (col.kind != ColKind.USE) {
                return;
            }

            if (row.isMinorBlock()) {
                allMinorFieldsBox.setSelected(
                        Boolean.TRUE.equals(value));
                fireTableRowsUpdated(
                        rowIndex,
                        rowIndex);
                fireConfigChanged();
                return;
            }

            state.use = Boolean.TRUE.equals(value);
            if (!state.use) {
                state.childEditor = null;
                state.customConfigured = false;
            }

            fireTableRowsUpdated(
                    rowIndex,
                    rowIndex);
            fireConfigChanged();
        }

        @Override
        public Class<?> getColumnClass(
                int columnIndex) {
            if (cols == null) {
                return Object.class;
            }

            Col col = cols.get(columnIndex);
            return switch (col.kind) {
                case USE -> Boolean.class;
                case EXTRA -> {
                    Class<?> valueClass =
                            col.extra.valueClass();
                    yield valueClass == null
                            ? Object.class
                            : valueClass;
                }
                default -> String.class;
            };
        }
    }

    private class RowRenderer
            implements TableCellRenderer {

        private final TableCellRenderer delegate;

        RowRenderer(TableCellRenderer delegate) {
            this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int viewRow,
                int viewColumn) {
            int modelRow =
                    table.convertRowIndexToModel(viewRow);
            RowState state = rows.get(modelRow);
            FieldRow row = state.row;

            ColKind kind = cols.get(
                    table.convertColumnIndexToModel(
                            viewColumn)).kind;

            if (row.isMinorBlock()
                    && kind != ColKind.USE) {
                JLabel label = new JLabel(
                        value == null
                                ? ""
                                : value.toString());
                label.setOpaque(true);
                label.setFont(
                        label.getFont().deriveFont(
                                Font.BOLD));
                label.setBackground(
                        new Color(235, 235, 235));
                label.setForeground(Color.DARK_GRAY);
                return label;
            }

            Component component =
                    delegate.getTableCellRendererComponent(
                            table,
                            value,
                            isSelected,
                            hasFocus,
                            viewRow,
                            viewColumn);

            if (row.isContainer() && !isSelected) {
                component.setForeground(Color.GRAY);
            }

            return component;
        }
    }

    private class ButtonRenderer
            extends JButton
            implements TableCellRenderer {

        ButtonRenderer() {
            setOpaque(true);
            chipify(this);
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int viewRow,
                int viewColumn) {
            int modelRow =
                    table.convertRowIndexToModel(viewRow);
            int modelColumn =
                    table.convertColumnIndexToModel(
                            viewColumn);

            RowState state = rows.get(modelRow);
            Col col = cols.get(modelColumn);

            setText(value == null
                    ? ""
                    : value.toString());
            setEnabled(
                    isButtonEnabled(
                            state,
                            col,
                            value));
            return this;
        }
    }

    private class ButtonEditor
            extends AbstractCellEditor
            implements TableCellEditor {

        private final JButton button = new JButton();
        private RowState currentState;
        private Col currentCol;
        private boolean opening;

        ButtonEditor() {
            chipify(button);

            button.addActionListener(e -> {
                if (opening) {
                    return;
                }

                RowState state = currentState;
                Col col = currentCol;
                fireEditingStopped();

                if (state == null || col == null) {
                    return;
                }

                FieldRow row = state.row;

                if (row.isMinorBlock()
                        && (col.kind == ColKind.EXPAND
                                || col.kind == ColKind.TREE)) {
                    opening = true;
                    SwingUtilities.invokeLater(() -> {
                        try {
                            openMinorEditor(state);
                        } finally {
                            opening = false;
                        }
                    });
                    return;
                }

                if (!row.isField()) {
                    return;
                }

                switch (col.kind) {
                    case TREE ->
                            SwingUtilities.invokeLater(
                                    () -> toggleExpand(row.path()));
                    case MOVE_BEFORE ->
                            moveSelectedTo(state, true);
                    case MOVE_AFTER ->
                            moveSelectedTo(state, false);
                    case ACTION -> {
                        if (col.action.enabled(row)) {
                            col.action.run(row);
                        }
                    }
                    case EXPAND -> {
                        opening = true;
                        SwingUtilities.invokeLater(() -> {
                            try {
                                openChildEditor(state);
                            } finally {
                                opening = false;
                            }
                        });
                    }
                    default -> {
                    }
                }
            });
        }

        @Override
        public Component getTableCellEditorComponent(
                JTable table,
                Object value,
                boolean isSelected,
                int viewRow,
                int viewColumn) {
            int modelRow =
                    table.convertRowIndexToModel(viewRow);
            int modelColumn =
                    table.convertColumnIndexToModel(
                            viewColumn);

            currentState = rows.get(modelRow);
            currentCol = cols.get(modelColumn);

            button.setText(value == null
                    ? ""
                    : value.toString());
            button.setEnabled(
                    isButtonEnabled(
                            currentState,
                            currentCol,
                            value));
            return button;
        }

        @Override
        public Object getCellEditorValue() {
            return "";
        }
    }

    // ---- mutable editor state ---------------------------------------------

    private static final class RowState {
        final FieldRow row;
        boolean use;
        boolean customConfigured;
        ViewConfigEditor childEditor;

        RowState(FieldRow row) {
            this.row = row;
        }
    }

    private record RowSnapshot(
            boolean use,
            ViewConfigEditor childEditor,
            boolean customConfigured) {
    }
}
