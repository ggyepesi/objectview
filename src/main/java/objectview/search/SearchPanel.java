package objectview.search;

import static objectview.field.FieldProperties.FIELD_NAME_PROPERTY;
import static objectview.field.FieldProperties.FIELD_PATH_PROPERTY;
import static objectview.field.FieldProperties.FIELD_VALUE_PROPERTY;
import objectview.*;
import objectview.field.ViewableFieldPaths;

import objectview.utils.swing.GridBagUtils;
import objectview.media.ImagePane;
import objectview.render.*;
import objectview.viewconfig.FieldTableContributor;
import objectview.viewconfig.FieldTypeSource;
import objectview.viewconfig.ViewConfig;
import objectview.viewconfig.ViewConfigEditor;
import objectview.virtual.CardStackLayout;
import objectview.virtual.ConfigurableVirtualizedContainer;
import objectview.virtual.VirtualizedContainer;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public class SearchPanel extends JPanel
        implements CardListener {

    private static final String OLD_BORDER_PROPERTY =
            "quiz.search.oldBorder";
    private static final String OLD_BACKGROUND_PROPERTY =
            "quiz.search.oldBackground";
    private static final String OLD_OPAQUE_PROPERTY =
            "quiz.search.oldOpaque";
    private static final String OLD_FOREGROUND_PROPERTY =
            "quiz.search.oldForeground";
    private static final String OLD_LABEL_TEXT_PROPERTY =
            "quiz.search.oldLabelText";
    private static final String HIDDEN_HIT_BADGE_PROPERTY =
            "quiz.search.hiddenHitBadge";

    private static final Color CARD_HIT_BACKGROUND =
            new Color(255, 248, 200);
    // The actual hit (field / text) is red-ish so it stands out clearly from
    // the card's pale-yellow tint.
    private static final Color FIELD_HIT_BACKGROUND =
            new Color(255, 188, 170);
    private static final Color TEXT_HIGHLIGHT_BACKGROUND =
            new Color(255, 150, 130);
    private static final Color HIDDEN_HIT_BADGE_COLOR =
            new Color(120, 80, 0);

    private final ViewConfigEditor searchEditor;
    private final ViewConfigEditor sortEditor;
    private final ViewConfigEditor viewEditor;

    /** Hides the given top-level fields from all three editors (search / sort /
     *  view config) — e.g. a domain's structural fields. Mechanical, no policy. */
    public void setHiddenFields(java.util.Set<String> fieldNames) {
        searchEditor.setHiddenFields(fieldNames);
        sortEditor.setHiddenFields(fieldNames);
        viewEditor.setHiddenFields(fieldNames);
    }

    /** Supplies authoritative field types (labels/cardinality/structural) to all
     *  three editors — e.g. a compiled model schema. Null reflects the sample. */
    public void setFieldTypes(FieldTypeSource source) {
        searchEditor.setFieldTypes(source);
        sortEditor.setFieldTypes(source);
        viewEditor.setFieldTypes(source);
    }

    private final JTextField searchField =
            new JTextField();

    private final JCheckBox fieldHighlightBox =
            new JCheckBox("Highlight Fields", false);

    // Returns to the spot before the last "jump to its card" navigation.
    private final JButton backButton = new JButton("← Back");
    private RenderContext renderContext;

    private final JPanel resultsPanel =
            new JPanel();

    private final List<Viewable> originalViewables =
            new ArrayList<>();

    private final List<Component> originalTargetOrder =
            new ArrayList<>();

    private final SearchAndSort searchAndSort =
            new SearchAndSort();

    private final Set<JComponent> rememberedSearchComponents =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final Set<Card> previousMatchedCards =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private final javax.swing.Timer debounceTimer;

    private JComponent targetPanel;
    private VirtualizedContainer virtualList;   // non-null when the target is data-backed/virtualized
    // Viewables matching the current query in a virtualized view, so a card rebuilt
    // on scroll-back can be re-highlighted (see cardMaterialized).
    private final java.util.Set<objectview.Viewable> virtualHits =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    private JScrollPane targetScrollPane;
    private JDialog searchDialog;
    private JDialog sortDialog;
    private JDialog viewDialog;
    private JComponent currentHit;

    // Coordinated mode: this panel is one section's search ENGINE driven by a
    // shared MultiSearchBar (shared input + config), while it keeps its own
    // per-field results panel + per-panel navigation + highlighting.
    private boolean suppressSearchEvents = false;

    // The class this section searches (for the shared config's per-class tab).
    private Class<? extends Viewable> searchClass;

    // The top toolbar (search input + config buttons); hidden in coordinated
    // mode, where the shared MultiSearchBar owns the input + config and each
    // section keeps only its own per-field results/navigation.
    private JComponent topControls;

    private int cachedColumnCount = 1;

    // True once the user has applied a sort and not yet restored order, so
    // live-added cards can be slotted into the sorted order rather than
    // appended at the end.
    private boolean sorted = false;
    private Consumer<ConfigState> configListener;
    private final java.util.List<SubtypeConfig> subtypeConfigs;
    private final java.util.Map<String, ViewConfigEditor> subtypeSearchEditors =
            new java.util.LinkedHashMap<>();
    private final java.util.Map<String, ViewConfigEditor> subtypeSortEditors =
            new java.util.LinkedHashMap<>();
    private final java.util.Map<String, ViewConfigEditor> subtypeViewEditors =
            new java.util.LinkedHashMap<>();

    /** Field choices retained by an owning browser when its instance scope changes. */
    public record ConfigState(
            ViewConfig search, ViewConfig sort, ViewConfig view,
            java.util.Map<String, ViewConfig> subtypeSearch,
            java.util.Map<String, ViewConfig> subtypeSort,
            java.util.Map<String, ViewConfig> subtypeView) {
        public ConfigState(ViewConfig search, ViewConfig sort, ViewConfig view) {
            this(search, sort, view, java.util.Map.of(), java.util.Map.of(),
                    java.util.Map.of());
        }
        public ConfigState {
            search = search == null ? null : search.copy();
            sort = sort == null ? null : sort.copy();
            view = view == null ? null : view.copy();
            subtypeSearch = copyConfigs(subtypeSearch);
            subtypeSort = copyConfigs(subtypeSort);
            subtypeView = copyConfigs(subtypeView);
        }

        private static java.util.Map<String, ViewConfig> copyConfigs(
                java.util.Map<String, ViewConfig> source) {
            java.util.Map<String, ViewConfig> copy = new java.util.LinkedHashMap<>();
            if (source != null) source.forEach((name, config) -> {
                if (name != null && config != null) copy.put(name, config.copy());
            });
            return java.util.Map.copyOf(copy);
        }
    }

    /** One known subtype section in the common class-hierarchy configuration. */
    public record SubtypeConfig(
            String typeName, String baseType,
            Viewable sample, FieldTypeSource fieldTypes,
            java.util.Set<String> additionalFields,
            java.util.function.Predicate<Viewable> applies) {}

    public void setTarget(
            JComponent targetPanel,
            JScrollPane targetScrollPane) {

        setTarget(targetPanel, targetScrollPane, false);
    }

    /** Wires the Back button to the view's navigation context, so jumping to a
     *  referenced card (a top-level reference link) can be undone. */
    public void setRenderContext(RenderContext context) {
        this.renderContext = context;
        if (context == null) {
            backButton.setEnabled(false);
            return;
        }
        context.setNavChangeListener(() -> backButton.setEnabled(context.canGoBack()));
        backButton.setEnabled(context.canGoBack());
    }

    public void setTargetAndApplyViewConfig(
            JComponent targetPanel,
            JScrollPane targetScrollPane) {

        setTarget(targetPanel, targetScrollPane, true);
    }

    private void setTarget(
            JComponent targetPanel,
            JScrollPane targetScrollPane,
            boolean applyViewConfig) {

        this.targetPanel = targetPanel;
        this.virtualList = targetPanel instanceof VirtualizedContainer container
                ? container
                : null;
        this.targetScrollPane = targetScrollPane;

        cachedColumnCount = detectColumnCount();

        rememberOriginalTargetsFromCurrentPanel();
        rebuildSearchIndex();
        clearResults();

        if (applyViewConfig) {
            applyViewConfig(false);
        }
    }

    /**
     * Re-syncs after the controlled view added cards live: refreshes the
     * column count, extends the original-order baseline with the new cards
     * (appending rather than re-snapshotting, which would otherwise capture
     * a sorted order as the baseline and break Restore Order), rebuilds the
     * search index, re-applies the active sort so new cards land in sorted
     * position, and re-applies the active query so they get highlighted.
     */
    @Override
    public void cardsAdded(List<Card> added) {
        if (targetPanel == null) {
            return;
        }

        cachedColumnCount = detectColumnCount();

        for (Card qp : added) {
            if (qp == null) {
                continue;
            }
            originalViewables.add(qp.getViewable());
            originalTargetOrder.add(qp);
        }

        rebuildSearchIndex();

        if (sorted) {
            sortTargetPanels();
        }

        maybeRefreshSearch();
    }

    /**
     * Re-syncs after the controlled view re-rendered cards in place (their
     * backing viewables changed). The card instances are unchanged, but
     * their child components — and possibly a sort-key field's value — are
     * new, so the search index is rebuilt, an active sort is re-applied
     * (the changed value may move the card), and the active query is
     * re-run so highlights match the new content.
     */
    @Override
    public void cardsUpdated(List<Card> updated) {
        if (targetPanel == null) {
            return;
        }

        rebuildSearchIndex();

        if (sorted) {
            sortTargetPanels();
        }

        maybeRefreshSearch();
    }

    @Override
    public void cardMaterialized(Card card) {
        // A card rebuilt on scroll-back is fresh; re-apply the highlight if it's a
        // current hit (otherwise the highlight is lost when you scroll away and back).
        if (card != null && virtualHits.contains(card.getViewable())) {
            highlightCard(card);
        }
    }

    private void sortTargetPanels() {
        if (targetPanel == null) {
            return;
        }

        List<ViewableFieldPaths.FieldPath> sortPaths =
                ViewableFieldPaths.collect(
                        effectiveConfig(sortEditor, subtypeSortEditors, null),
                        ViewableFieldPaths.NOT_MEDIA_FIELDS);

        if (sortPaths.isEmpty()) {
            return;
        }

        // Virtualized: sort the DATA (all fields of every viewable), then re-render
        // the visible window in the new order — O(N) on data + O(visible) render,
        // no component shuffle.
        if (virtualList != null) {
            // Keep your place across the resort: anchor on the highlighted search
            // hit if any, else the card currently at the top of the viewport, and
            // re-pin it at the top in the new order.
            Viewable anchor = currentHit instanceof Card qp
                    ? qp.getViewable()
                    : virtualList.topVisibleItem();

            List<Viewable> ordered =
                    searchAndSort.sortViewables(virtualList.items(), sortPaths);
            virtualList.setItems(ordered);
            if (anchor != null) {
                virtualList.navigateToTop(anchor);
            }
            sorted = true;
            return;
        }

        List<Card> panels =
                new ArrayList<>();

        for (Component c : targetPanel.getComponents()) {
            if (c instanceof Card qp) {
                panels.add(qp);
            }
        }

        panels = searchAndSort.sortPanels(panels, sortPaths);
        applyTargetOrder(panels);
        sorted = true;

        // Don't call maybeRefreshSearch() — highlights are still valid
        // because no panels were recreated, only repositioned.
    }

    // CardStackLayout lays out in component order, so reordering for sort is just
    // re-adding the cards in the new order (cheap — the layout is O(n) arithmetic).
    private void applyTargetOrder(List<? extends Component> order) {
        if (targetPanel == null) {
            return;
        }

        List<Component> filtered = new ArrayList<>();
        for (Component c : order) {
            if (c instanceof Card) {
                filtered.add(c);
            }
        }

        // Skip if the order hasn't changed (avoid a needless removeAll/relayout).
        Component[] current = targetPanel.getComponents();
        if (current.length == filtered.size()) {
            boolean same = true;
            for (int i = 0; i < current.length; i++) {
                if (current[i] != filtered.get(i)) {
                    same = false;
                    break;
                }
            }
            if (same) {
                return;
            }
        }

        targetPanel.removeAll();
        for (Component c : filtered) {
            targetPanel.add(c);
        }

        targetPanel.revalidate();
        targetPanel.repaint();
    }


    public SearchPanel(Class<? extends Viewable> cls) {
        this(cls, null, null, java.util.List.of());
    }

    /** @param sample a sample instance for a DYNAMIC type (a map-backed Viewable), so
     *                the search/sort/view-config editors enumerate its map-held
     *                fields; null for a reflection type. */
    public SearchPanel(Class<? extends Viewable> cls, Viewable sample) {
        this(cls, sample, null, java.util.List.of());
    }

    public SearchPanel(
            Class<? extends Viewable> cls,
            Viewable sample,
            ConfigState initialConfigs) {
        this(cls, sample, initialConfigs, java.util.List.of());
    }

    public SearchPanel(
            Class<? extends Viewable> cls,
            Viewable sample,
            ConfigState initialConfigs,
            java.util.List<SubtypeConfig> subtypeConfigs) {
        this.searchClass = cls;
        this.subtypeConfigs = subtypeConfigs == null
                ? java.util.List.of() : java.util.List.copyOf(subtypeConfigs);
        setLayout(new BorderLayout(6, 6));

        ViewConfig nameOnly =
                nameOnlyConfig(cls);

        ViewConfig viewBase = ViewConfig.all(cls)
                .setAddListener(true)
                .setThumb(true);

        ViewConfig initialSearch = initialConfigs == null
                || initialConfigs.search() == null
                ? nameOnly : initialConfigs.search();
        ViewConfig initialSort = initialConfigs == null
                || initialConfigs.sort() == null
                ? nameOnly : initialConfigs.sort();
        ViewConfig initialView = initialConfigs == null
                || initialConfigs.view() == null
                ? viewBase : initialConfigs.view();

        searchEditor =
                new ViewConfigEditor(initialSearch.copy(), true, sample,
                        FieldTableContributor.REORDERABLE);

        sortEditor =
                new ViewConfigEditor(initialSort.copy(), true, sample,
                        FieldTableContributor.REORDERABLE);

        viewEditor =
                new ViewConfigEditor(initialView.copy(), false, sample,
                        FieldTableContributor.REORDERABLE);

        for (SubtypeConfig subtype : this.subtypeConfigs) {
            if (subtype == null || subtype.typeName() == null) continue;
            ViewConfig defaultAdditional = additionalConfig(
                    cls, subtype.additionalFields());
            ViewConfig noAdditional = additionalConfig(cls, java.util.Set.of());
            ViewConfigEditor se = subtypeEditor(initialConfigs == null ? null
                    : initialConfigs.subtypeSearch().get(subtype.typeName()),
                    noAdditional, true, subtype);
            ViewConfigEditor so = subtypeEditor(initialConfigs == null ? null
                    : initialConfigs.subtypeSort().get(subtype.typeName()),
                    noAdditional, true, subtype);
            ViewConfigEditor ve = subtypeEditor(initialConfigs == null ? null
                    : initialConfigs.subtypeView().get(subtype.typeName()),
                    defaultAdditional, false, subtype);
            se.setHideMedia(true);
            so.setHideMedia(true);
            subtypeSearchEditors.put(subtype.typeName(), se);
            subtypeSortEditors.put(subtype.typeName(), so);
            subtypeViewEditors.put(subtype.typeName(), ve);
        }

        installClassBranches(searchEditor, subtypeSearchEditors);
        installClassBranches(sortEditor, subtypeSortEditors);
        installClassBranches(viewEditor, subtypeViewEditors);

        // Search / sort over an image is meaningless — hide MEDIA fields there. The
        // VIEW editor keeps them, so a portrait / flag can be shown or hidden on cards.
        searchEditor.setHideMedia(true);
        sortEditor.setHideMedia(true);

        debounceTimer =
                new javax.swing.Timer(
                        150,
                        e -> searchSync(searchField.getText()));

        debounceTimer.setRepeats(false);

        searchField.setBorder(
                BorderFactory.createTitledBorder("Search"));
        searchField.setColumns(32);
        searchField.setPreferredSize(new Dimension(420, searchField.getPreferredSize().height));

        searchField.getDocument().addDocumentListener(
                new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        asyncSearch();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        asyncSearch();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        asyncSearch();
                    }
                });

        JButton viewConfigButton = new JButton("View Config...");
        JButton sortConfigButton = new JButton("Sort Config...");
        JButton sortButton = new JButton("Sort");
        JButton restoreOrderButton = new JButton("Restore Order");
        JButton searchConfigButton = new JButton("Search Config...");

        backButton.setEnabled(false);
        backButton.setToolTipText("Back to where you jumped from");
        backButton.addActionListener(e -> {
            if (renderContext != null) {
                renderContext.back();
            }
        });

        JPanel top =
                new SearchToolBar(
                        backButton,
                        searchField,
                        searchConfigButton,
                        sortConfigButton,
                        sortButton,
                        restoreOrderButton,
                        viewConfigButton,
                        fieldHighlightBox);

        // In a narrow panel (e.g. the "Generated instances" split pane) the
        // toolbar's second row of buttons would otherwise be clipped and
        // unreachable. A horizontal-only scroll pane keeps every control
        // reachable while never stealing vertical room from the results.
        topControls = objectview.utils.swing.ScrollPaneUtils.horizontalOnly(top);
        add(topControls, BorderLayout.NORTH);

        resultsPanel.setLayout(
                new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));

        JScrollPane resultsScroll =
                new JScrollPane(resultsPanel);
        resultsScroll.setPreferredSize(new Dimension(320, 160));
        resultsScroll.getVerticalScrollBar().setUnitIncrement(16);

        add(resultsScroll, BorderLayout.CENTER);

        searchConfigButton.addActionListener(e -> openSearchDialog());
        sortConfigButton.addActionListener(e -> openSortDialog());
        viewConfigButton.addActionListener(e -> openViewDialog());
        sortButton.addActionListener(e -> sortTargetPanels());
        restoreOrderButton.addActionListener(
                e -> restoreOriginalTargetOrder());

        fieldHighlightBox.addActionListener(e -> refreshSearch());
    }

    private ViewConfig nameOnlyConfig(
            Class<? extends Viewable> cls) {

        ViewConfig cfg =
                ViewConfig.of(cls);

        cfg.setAllFields(false);
        cfg.setAddListener(false);
        cfg.setThumb(false);
        cfg.addField("name", ViewConfig.leaf());

        return cfg;
    }

    private void rememberOriginalTargetsFromCurrentPanel() {
        originalViewables.clear();
        originalTargetOrder.clear();

        // Virtualized: the baseline is the full data list (most cards aren't built).
        if (virtualList != null) {
            originalViewables.addAll(virtualList.items());
            return;
        }

        if (targetPanel == null) {
            return;
        }

        for (Component c : targetPanel.getComponents()) {
            if (c instanceof Card qp) {
                originalViewables.add(qp.getViewable());
                originalTargetOrder.add(qp);
            }
        }
    }

    public ViewConfig getSearchConfig() {
        return searchEditor.getConfig();
    }

    public ViewConfig getSortConfig() {
        return sortEditor.getConfig();
    }

    public ViewConfig getViewConfig() {
        return viewEditor.getConfig();
    }

    private static java.util.Map<String, ViewConfig> configsOf(
            java.util.Map<String, ViewConfigEditor> editors) {
        java.util.Map<String, ViewConfig> result = new java.util.LinkedHashMap<>();
        editors.forEach((name, editor) -> result.put(name, editor.getConfig()));
        return result;
    }

    private void installClassBranches(
            ViewConfigEditor base,
            java.util.Map<String, ViewConfigEditor> subtypeEditors) {
        java.util.List<ViewConfigEditor.ClassBranch> branches =
                new java.util.ArrayList<>();
        for (SubtypeConfig subtype : subtypeConfigs) {
            ViewConfigEditor editor = subtypeEditors.get(subtype.typeName());
            if (editor == null) continue;
            @SuppressWarnings("unchecked")
            Class<? extends Viewable> type = subtype.sample() == null
                    ? Viewable.class
                    : (Class<? extends Viewable>) subtype.sample().getClass();
            branches.add(new ViewConfigEditor.ClassBranch(
                    subtype.typeName(), subtype.baseType(), type,
                    additionalFieldTypes(subtype), editor.getConfig()));
        }
        base.setClassBranches(branches);
    }

    private static FieldTypeSource additionalFieldTypes(SubtypeConfig subtype) {
        java.util.Set<String> fields = subtype.additionalFields() == null
                ? java.util.Set.of()
                : new java.util.LinkedHashSet<>(subtype.additionalFields());
        FieldTypeSource source = subtype.fieldTypes();
        return new FieldTypeSource() {
            @Override public FieldTypeSource.FieldTypeInfo field(String name) {
                if ("name".equals(name) && !fields.contains(name)) {
                    return new FieldTypeSource.FieldTypeInfo(
                            "String", true, false, null, null);
                }
                return fields.contains(name) && source != null
                        ? source.field(name) : null;
            }

            @Override public java.util.List<String> fieldNames() {
                java.util.List<String> names = new java.util.ArrayList<>(fields);
                if (!fields.contains("name")) names.add("name");
                return java.util.List.copyOf(names);
            }
        };
    }

    private static ViewConfig additionalConfig(
            Class<? extends Viewable> cls, java.util.Set<String> fields) {
        ViewConfig config = ViewConfig.of(cls);
        config.setAllFields(false);
        if (fields != null) {
            for (String field : fields) {
                if (field != null && !field.isBlank()) {
                    config.addField(field, ViewConfig.leaf());
                }
            }
        }
        return config;
    }

    private ViewConfigEditor subtypeEditor(
            ViewConfig retained, ViewConfig fallback, boolean selectionOnly,
            SubtypeConfig subtype) {
        ViewConfigEditor editor = new ViewConfigEditor(
                retained == null ? fallback : retained,
                selectionOnly, subtype.sample(), FieldTableContributor.REORDERABLE);
        editor.setFieldTypes(subtype.fieldTypes());
        java.util.Set<String> hidden = new java.util.LinkedHashSet<>();
        if (subtype.fieldTypes() != null) {
            hidden.addAll(subtype.fieldTypes().fieldNames());
        }
        if (subtype.sample() != null) {
            for (java.lang.reflect.Field field :
                    ViewableAdapter.getConfigurableFields(subtype.sample().getClass())) {
                hidden.add(field.getName());
            }
        }
        hidden.removeAll(subtype.additionalFields() == null
                ? java.util.Set.of() : subtype.additionalFields());
        editor.setHiddenFields(hidden);
        return editor;
    }

    private JComponent hierarchyEditor(ViewConfigEditor base) {
        return base;
    }

    private ViewConfig effectiveConfig(
            ViewConfigEditor baseEditor,
            java.util.Map<String, ViewConfigEditor> subtypeEditors,
            Viewable instance) {
        ViewConfig result = baseEditor.getConfig();
        java.util.Map<String, ViewConfig> branchConfigs =
                baseEditor.classBranchConfigs();
        for (SubtypeConfig subtype : subtypeConfigs) {
            if (instance != null && (subtype.applies() == null
                    || !subtype.applies().test(instance))) continue;
            ViewConfig extra = branchConfigs.get(subtype.typeName());
            if (extra == null) {
                ViewConfigEditor fallback = subtypeEditors.get(subtype.typeName());
                extra = fallback == null ? null : fallback.getConfig();
            }
            if (extra != null) result = result.withAdditionalFields(extra);
        }
        return result;
    }

    public ConfigState configState() {
        return new ConfigState(getSearchConfig(), getSortConfig(), getViewConfig(),
                classConfigs(searchEditor, subtypeSearchEditors),
                classConfigs(sortEditor, subtypeSortEditors),
                classConfigs(viewEditor, subtypeViewEditors));
    }

    private static java.util.Map<String, ViewConfig> classConfigs(
            ViewConfigEditor base,
            java.util.Map<String, ViewConfigEditor> fallback) {
        java.util.Map<String, ViewConfig> branches = base.classBranchConfigs();
        return branches.isEmpty() ? configsOf(fallback) : branches;
    }

    public void setConfigListener(Consumer<ConfigState> listener) {
        this.configListener = listener;
    }

    private void notifyConfigChanged() {
        if (configListener != null) configListener.accept(configState());
    }

    private void openSearchDialog() {
        if (searchDialog == null) {
            searchDialog =
                    createDialog(
                            "Search Configuration",
                            hierarchyEditor(searchEditor),
                            this::refreshSearch);
        }

        searchDialog.setVisible(true);
    }

    private void openSortDialog() {
        if (sortDialog == null) {
            sortDialog =
                    createDialog(
                            "Sort Configuration",
                            hierarchyEditor(sortEditor),
                            this::applySort);
        }

        sortDialog.setVisible(true);
    }

    private void openViewDialog() {
        if (viewDialog == null) {
            viewDialog =
                    createDialog(
                            "View Configuration",
                            hierarchyEditor(viewEditor),
                            this::applyView);
        }

        viewDialog.setVisible(true);
    }

    private JDialog createDialog(
            String title,
            JComponent content,
            Runnable onApply) {

        JDialog dialog =
                new JDialog(
                        SwingUtilities.getWindowAncestor(this),
                        title,
                        Dialog.ModalityType.MODELESS);

        dialog.setLayout(new BorderLayout(8, 8));
        dialog.add(content, BorderLayout.CENTER);

        JButton apply =
                new JButton("Apply");

        apply.addActionListener(e -> {
            onApply.run();
            dialog.setVisible(false);
        });

        JPanel buttonPanel =
                new JPanel(new FlowLayout(FlowLayout.RIGHT));

        buttonPanel.add(apply);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setSize(800, 600);
        dialog.setLocationRelativeTo(this);

        return dialog;
    }

    private void asyncSearch() {
        if (suppressSearchEvents) {
            return;
        }
        debounceTimer.restart();
    }

    // ---- Coordinated (multi-section) search API ----------------------------

    /** Driven by a shared {@link MultiSearchBar}: hides this engine's own input
     *  + config toolbar (the bar owns those), while it keeps its own per-field
     *  results panel and per-panel navigation. */
    public void setCoordinated(boolean coordinated) {
        if (topControls != null) {
            topControls.setVisible(!coordinated);
        }
    }

    /** Runs the query against this section synchronously (highlighting its
     *  cards) without stealing navigation. Keeps the (hidden) field in sync so
     *  live-add re-search uses the right text. */
    public void runCoordinatedSearch(String query) {
        suppressSearchEvents = true;
        try {
            searchField.setText(query == null ? "" : query);
        } finally {
            suppressSearchEvents = false;
        }
        clearHighlights();
        rebuildSearchIndex();
        searchSync(query == null ? "" : query);
    }

    /** The class this section searches (for the shared config's per-class tab). */
    public String sectionTypeName() {
        return searchClass == null ? "" : searchClass.getSimpleName();
    }

    // Editors + apply hooks, so a single shared dialog (classes as roots) can
    // host every section's search/sort/view config and re-run on apply.
    public ViewConfigEditor searchEditor() { return searchEditor; }
    public ViewConfigEditor sortEditor() { return sortEditor; }
    public ViewConfigEditor viewEditor() { return viewEditor; }

    public void applySearch() { refreshSearch(); }
    public void applySort() { sortTargetPanels(); notifyConfigChanged(); }
    public void applyView() { applyViewConfig(); notifyConfigChanged(); }

    /** Sets the field-highlight option from the shared bar and re-runs. */
    public void setFieldHighlight(boolean on) {
        if (fieldHighlightBox.isSelected() != on) {
            fieldHighlightBox.setSelected(on);
        }
    }

    private void refreshSearch() {
        clearHighlights();
        rebuildSearchIndex();
        searchSync(searchField.getText());
        notifyConfigChanged();
    }

    private void rebuildSearchIndex() {
        // A virtual/data-backed target is searched directly from its complete
        // Viewable item list. Only ordinary component-backed targets need the
        // rendered-component index.
        if (virtualList == null) {
            searchAndSort.rebuildSearchIndex(
                    targetPanel,
                    effectiveConfig(searchEditor, subtypeSearchEditors, null));
        }
    }

    private void searchSync(String query) {
        if (targetPanel == null) {
            return;
        }

        String text =
                normalize(query == null ? "" : query);

        clearHighlights();
        clearResults();
        // New query: drop the previous hit set. searchSyncVirtual repopulates it
        // for a non-empty query; an empty query leaves it cleared so stale hits
        // don't re-highlight on scroll.
        virtualHits.clear();

        if (text.isEmpty()) {
            return;
        }

        List<String> queryTokens =
                tokens(text);

        if (queryTokens.isEmpty()) {
            return;
        }

        // Virtualized view: only the visible cards exist as components, so search
        // the DATA and navigate hits one at a time (building each card on demand).
        if (virtualList != null) {
            searchSyncVirtual(queryTokens);
            return;
        }

        Map<String, List<Card>> matchesByField =
                searchAndSort.search(queryTokens);

        Map<String, HitGroup> groups =
                new LinkedHashMap<>();

        for (Map.Entry<String, List<Card>> e
                : matchesByField.entrySet()) {

            HitGroup group =
                    new HitGroup(e.getKey());

            for (Card qp : e.getValue()) {
                if (!group.hits.contains(qp)) {
                    group.hits.add(qp);
                }

                highlightCard(qp);
            }

            groups.put(e.getKey(), group);
        }

        if (fieldHighlightBox.isSelected()) {
            addFieldHighlights(
                    matchesByField,
                    groups,
                    queryTokens);
        }

        targetPanel.revalidate();
        targetPanel.repaint();

        showSearchResults(groups);
    }

    private void addFieldHighlights(
            Map<String, List<Card>> matchesByField,
            Map<String, HitGroup> groups,
            List<String> queryTokens) {

        List<ViewableFieldPaths.FieldPath> paths =
                ViewableFieldPaths.collect(
                        effectiveConfig(searchEditor, subtypeSearchEditors, null),
                        ViewableFieldPaths.NOT_MEDIA_FIELDS);

        Map<String, ViewableFieldPaths.FieldPath> pathByTitle =
                new LinkedHashMap<>();

        for (ViewableFieldPaths.FieldPath fp : paths) {
            pathByTitle.put(fp.title(), fp);
        }

        // Pre-pass: a match can sit inside a collapsed collection (e.g. a
        // Character's collapsed "episodes", or an Episode's "characters"). Expand
        // the collapsed collections on each matching card's matching path so the
        // rows render, then refresh those cards once — otherwise the hit can't be
        // highlighted or scrolled to.
        Set<Card> toRefresh =
                Collections.newSetFromMap(new IdentityHashMap<>());

        for (Map.Entry<String, List<Card>> e
                : matchesByField.entrySet()) {
            ViewableFieldPaths.FieldPath fp = pathByTitle.get(e.getKey());
            if (fp == null) {
                continue;
            }
            for (Card qp : e.getValue()) {
                if (qp.expandCollectionsOnPath(fp.path())) {
                    toRefresh.add(qp);
                }
            }
        }
        for (Card qp : toRefresh) {
            qp.refresh();
        }

        for (Map.Entry<String, List<Card>> e
                : matchesByField.entrySet()) {

            ViewableFieldPaths.FieldPath fp =
                    pathByTitle.get(e.getKey());

            if (fp == null) {
                continue;
            }

            HitGroup group =
                    groups.get(e.getKey());

            if (group == null) {
                continue;
            }

            for (Card qp : e.getValue()) {
                List<JComponent> fieldHits =
                        collectMatchingFieldRows(
                                qp,
                                fp.path(),
                                queryTokens);

                // The precise field rows ARE the hits — drop the coarse card
                // placeholder so a card with rows isn't counted (and navigated)
                // twice. If no rows were found (e.g. a text-only match), the card
                // stays as the hit.
                if (!fieldHits.isEmpty()) {
                    group.hits.remove(qp);
                }

                for (JComponent hit : fieldHits) {
                    if (!group.hits.contains(hit)) {
                        group.hits.add(hit);
                    }

                    highlightField(hit);
                }

                highlightTextRecursively(
                        qp,
                        fp.path(),
                        queryTokens);
            }
        }
    }

    private void highlightCard(Card qp) {
        remember(qp);
        previousMatchedCards.add(qp);

        qp.setHighlightColor(CARD_HIT_BACKGROUND);
    }

    private void highlightField(JComponent c) {
        remember(c);
        c.setOpaque(true);
        c.setBackground(FIELD_HIT_BACKGROUND);
        c.repaint();
    }

    private void addHiddenHitBadge(
            Card panel,
            String fieldTitle) {

        Object existing =
                panel.getClientProperty(HIDDEN_HIT_BADGE_PROPERTY);

        if (existing instanceof JLabel label) {
            label.setText(label.getText() + ", " + fieldTitle);
            return;
        }

        remember(panel);

        JLabel badge =
                new JLabel("hidden hit: " + fieldTitle);

        badge.setForeground(HIDDEN_HIT_BADGE_COLOR);
        badge.setFont(badge.getFont().deriveFont(Font.ITALIC));

        panel.putClientProperty(HIDDEN_HIT_BADGE_PROPERTY, badge);

        panel.add(
                badge,
                GridBagUtils.gbc(
                        0,
                        panel.getComponentCount(),
                        1.0,
                        0.0,
                        GridBagConstraints.NORTHWEST,
                        GridBagConstraints.HORIZONTAL,
                        new Insets(2, 8, 2, 2)));

        panel.revalidate();
        panel.repaint();
    }

    private List<JComponent> collectMatchingFieldRows(
            Component root,
            List<String> selectedPath,
            List<String> queryTokens) {

        List<JComponent> hits =
                new ArrayList<>();

        collectMatchingFieldRows(
                root,
                selectedPath,
                queryTokens,
                hits);

        return hits;
    }

    private void collectMatchingFieldRows(
            Component root,
            List<String> selectedPath,
            List<String> queryTokens,
            List<JComponent> hits) {

        if (root instanceof TextBlock block) {
            if (block.hasMatchingRow(selectedPath, queryTokens)) {
                replaceAncestorWithDescendantIfNeeded(block, hits);
            }

            return;
        }

        if (root instanceof JComponent jc) {
            Object pathObj =
                    jc.getClientProperty(FIELD_PATH_PROPERTY);

            Object val =
                    jc.getClientProperty(FIELD_VALUE_PROPERTY);

            if (pathObj instanceof List<?> rowPath
                    && samePath(rowPath, selectedPath)
                    && matchesWithTokens(val, queryTokens)) {

                replaceAncestorWithDescendantIfNeeded(jc, hits);
            }
        }

        if (root instanceof Container ct) {
            for (Component child : ct.getComponents()) {
                collectMatchingFieldRows(
                        child,
                        selectedPath,
                        queryTokens,
                        hits);
            }
        }
    }

    private void replaceAncestorWithDescendantIfNeeded(
            JComponent candidate,
            List<JComponent> hits) {

        for (Iterator<JComponent> it = hits.iterator(); it.hasNext();) {
            JComponent existing =
                    it.next();

            if (SwingUtilities.isDescendingFrom(candidate, existing)) {
                it.remove();
                break;
            }

            if (SwingUtilities.isDescendingFrom(existing, candidate)) {
                return;
            }
        }

        hits.add(candidate);
    }

    private void showSearchResults(Map<String, HitGroup> groups) {
        resultsPanel.removeAll();

        JComponent first =
                null;

        for (HitGroup g : groups.values()) {
            if (g.hits.isEmpty()) {
                continue;
            }

            addHitGroupRow(g);

            if (first == null) {
                first = g.hits.get(0);
                g.index = 0;
                g.updateLabel();
            }
        }

        resultsPanel.revalidate();
        resultsPanel.repaint();

        if (first != null) {
            markCurrentHit(first);
            scrollTo(first);
        }
    }

    private void applyViewConfig() {
        applyViewConfig(true);
    }

    private void applyViewConfig(boolean searchAfter) {
        if (targetPanel == null) {
            return;
        }

        // A grouped/virtual target owns its card factory and can discard/rebuild
        // materialized cards lazily. Do not replace its structural child panels.
        if (virtualList != null) {
            if (virtualList instanceof ConfigurableVirtualizedContainer configurable) {
                configurable.setCardConfigResolver(q -> effectiveConfig(
                        viewEditor, subtypeViewEditors, q));
            }

            rebuildSearchIndex();

            if (searchAfter) {
                maybeRefreshSearch();
            }
            return;
        }

        List<Card> panels =
                new ArrayList<>();

        List<Viewable> viewables =
                originalViewables.isEmpty()
                        ? collectViewablesFromCurrentTarget()
                        : new ArrayList<>(originalViewables);

        RenderContext context =
                new RenderContext(viewables);

        for (Viewable q : viewables) {
            if (q == null) {
                continue;
            }

            ViewConfig viewCfg = effectiveConfig(
                    viewEditor, subtypeViewEditors, q);

            if (viewCfg.getCls() == null) {
                viewCfg.setCls(q.getClass());
            }

            context.putClassConfig(q.getClass(), viewCfg);

            Card panel =
                    new Card(q, viewCfg, context, false);

            context.registerTopLevel(q, panel);

            if (panel.hasRenderedConfiguredContent()) {
                panels.add(panel);
            }
        }

        originalViewables.clear();
        originalViewables.addAll(viewables);

        originalTargetOrder.clear();
        originalTargetOrder.addAll(panels);

        applyTargetOrder(panels);
        rebuildSearchIndex();

        // EDGE CASE: this recreates the cards in original order, visually
        // un-sorting, but deliberately leaves the `sorted` flag untouched.
        // Consequence: if a sort was active, the next live add
        // (cardsAdded) will re-impose it. That re-sort is the
        // intended behavior; if you ever want View Config to clear the
        // sort instead, set `sorted = false` here — but then a live add
        // after a view change won't restore the user's sort. Decide
        // explicitly rather than letting it drift.
        if (searchAfter) {
            maybeRefreshSearch();
        }
    }

    private List<Viewable> collectViewablesFromCurrentTarget() {
        List<Viewable> out =
                new ArrayList<>();

        if (targetPanel == null) {
            return out;
        }

        for (Component c : targetPanel.getComponents()) {
            if (c instanceof Card qp) {
                out.add(qp.getViewable());
            }
        }

        return out;
    }

    private void restoreOriginalTargetOrder() {
        sorted = false;

        // Virtualized: restore the original data order (a data swap, not a
        // component shuffle).
        if (virtualList != null) {
            virtualList.setItems(originalViewables);
            maybeRefreshSearch();
            return;
        }

        applyTargetOrder(originalTargetOrder);
        maybeRefreshSearch();
    }

    private void maybeRefreshSearch() {
        if (!searchField.getText().isBlank()) {
            asyncSearch();
        }
    }

    private int detectColumnCount() {
        return targetPanel != null
                && targetPanel.getLayout() instanceof CardStackLayout csl
                ? csl.columns()
                : 1;
    }

    private boolean matchesWithTokens(
            Object value,
            List<String> tokens) {

        if (value == null) {
            return false;
        }

        if (value instanceof Collection<?> c) {
            for (Object item : c) {
                if (matchesWithTokens(item, tokens)) {
                    return true;
                }
            }

            return false;
        }

        if (value instanceof Map<?, ?> m) {
            for (Object item : m.values()) {
                if (matchesWithTokens(item, tokens)) {
                    return true;
                }
            }

            return false;
        }

        String s =
                normalize(value instanceof Viewable q
                                  ? q.getName()
                                  : value.toString());

        for (String tok : tokens) {
            if (!s.contains(tok)) {
                return false;
            }
        }

        return true;
    }

    private String normalize(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }

    private List<String> tokens(String t) {
        if (t == null) {
            return List.of();
        }

        String[] arr =
                t.toLowerCase().trim().split("\\s+");

        List<String> out =
                new ArrayList<>();

        for (String p : arr) {
            if (!p.isBlank()) {
                out.add(p);
            }
        }

        return out;
    }

    private void highlightTextRecursively(
            Component root,
            List<String> selectedPath,
            List<String> queryTokens) {

        if (root instanceof TextBlock block) {
            if (block.hasMatchingRow(selectedPath, queryTokens)) {
                remember(block);
                block.setHighlightTokens(selectedPath, queryTokens);
            }

            return;
        }

        if (root instanceof JComponent jc) {
            Object pathObj =
                    jc.getClientProperty(FIELD_PATH_PROPERTY);

            boolean isSelectedField =
                    pathObj instanceof List<?> rowPath
                            && samePath(rowPath, selectedPath);

            if (isSelectedField) {
                highlightLabelsUnder(jc, queryTokens);
                return;
            }
        }

        if (root instanceof Container ct) {
            for (Component child : ct.getComponents()) {
                highlightTextRecursively(
                        child,
                        selectedPath,
                        queryTokens);
            }
        }
    }

    private void highlightLabelsUnder(
            Component root,
            List<String> queryTokens) {

        if (root instanceof TextRow row) {
            remember(row);
            row.setHighlightTokens(queryTokens);
            return;
        }

        if (root instanceof JLabel lbl) {
            highlightLabelText(lbl, queryTokens);
        }

        if (root instanceof Container ct) {
            for (Component child : ct.getComponents()) {
                highlightLabelsUnder(child, queryTokens);
            }
        }
    }

    private boolean samePath(
            List<?> a,
            List<String> b) {

        if (a == null || b == null || a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            if (!Objects.equals(String.valueOf(a.get(i)), b.get(i))) {
                return false;
            }
        }

        return true;
    }

    private void highlightLabelText(
            JLabel lbl,
            List<String> queryTokens) {

        String restoreText =
                (String) lbl.getClientProperty(OLD_LABEL_TEXT_PROPERTY);

        if (restoreText == null) {
            restoreText = lbl.getText();
            lbl.putClientProperty(OLD_LABEL_TEXT_PROPERTY, restoreText);
        }

        String plain =
                null;

        Object value =
                lbl.getClientProperty(FIELD_VALUE_PROPERTY);

        if (value instanceof String s) {
            plain = s;
        }

        if (plain == null) {
            plain = stripHtml(lbl.getText());
        }

        if (plain == null || plain.isBlank()) {
            return;
        }

        String highlighted =
                highlightTokens(plain, queryTokens);

        if (!highlighted.equals(escapeHtml(plain))) {
            remember(lbl);
            lbl.setText("<html><span>"
                                + highlighted
                                + "</span></html>");
        }
    }

    private String stripHtml(String s) {
        if (s == null) {
            return "";
        }

        if (!s.trim().toLowerCase().startsWith("<html")) {
            return s;
        }

        StringBuilder sb =
                new StringBuilder(s.length());

        boolean inTag =
                false;

        for (int i = 0; i < s.length(); i++) {
            char ch =
                    s.charAt(i);

            if (ch == '<') {
                inTag = true;
            } else if (ch == '>') {
                inTag = false;
            } else if (!inTag) {
                sb.append(ch);
            }
        }

        return sb.toString()
                 .replace("&lt;", "<")
                 .replace("&gt;", ">")
                 .replace("&amp;", "&")
                 .replace("&quot;", "\"")
                 .trim();
    }

    private String highlightTokens(
            String text,
            List<String> toks) {

        if (toks.isEmpty() || text.isEmpty()) {
            return escapeHtml(text);
        }

        String lower =
                text.toLowerCase();

        boolean[] mark =
                new boolean[text.length()];

        for (String tok : toks) {
            int idx =
                    0;

            while ((idx = lower.indexOf(tok, idx)) >= 0) {
                for (int i = idx;
                     i < idx + tok.length() && i < mark.length;
                     i++) {

                    mark[i] = true;
                }

                idx += Math.max(1, tok.length());
            }
        }

        StringBuilder sb =
                new StringBuilder();

        String color =
                String.format(
                        "rgb(%d,%d,%d)",
                        TEXT_HIGHLIGHT_BACKGROUND.getRed(),
                        TEXT_HIGHLIGHT_BACKGROUND.getGreen(),
                        TEXT_HIGHLIGHT_BACKGROUND.getBlue());

        boolean open =
                false;

        for (int i = 0; i < text.length(); i++) {
            if (mark[i] && !open) {
                sb.append("<span style='background-color:")
                  .append(color)
                  .append(";'>");

                open = true;
            } else if (!mark[i] && open) {
                sb.append("</span>");
                open = false;
            }

            sb.append(escapeChar(text.charAt(i)));
        }

        if (open) {
            sb.append("</span>");
        }

        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }

        StringBuilder sb =
                new StringBuilder();

        for (char c : s.toCharArray()) {
            sb.append(escapeChar(c));
        }

        return sb.toString();
    }

    private String escapeChar(char c) {
        return switch (c) {
            case '<' -> "&lt;";
            case '>' -> "&gt;";
            case '&' -> "&amp;";
            case '"' -> "&quot;";
            default -> String.valueOf(c);
        };
    }

    private void remember(JComponent c) {
        if (Boolean.TRUE.equals(
                c.getClientProperty("quiz.search.remembered"))) {
            return;
        }

        rememberedSearchComponents.add(c);

        c.putClientProperty("quiz.search.remembered", true);
        c.putClientProperty(OLD_BORDER_PROPERTY, c.getBorder());
        c.putClientProperty(OLD_BACKGROUND_PROPERTY, c.getBackground());
        c.putClientProperty(OLD_OPAQUE_PROPERTY, c.isOpaque());

        if (c instanceof JLabel l) {
            c.putClientProperty(OLD_FOREGROUND_PROPERTY, l.getForeground());
        }
    }

    private void clearHighlights() {
        currentHit =
                null;
        // NOTE: virtualHits is NOT cleared here — clearHighlights fires on every
        // navigate-between-hits, but the hit set belongs to the whole QUERY, so a
        // card rebuilt on scroll-back can be re-highlighted. It's reset per query
        // in searchSync (and repopulated by searchSyncVirtual).

        if (rememberedSearchComponents.isEmpty()
                && previousMatchedCards.isEmpty()) {
            return;
        }

        for (JComponent component
                : new ArrayList<>(rememberedSearchComponents)) {

            restoreRememberedComponent(component);
        }

        rememberedSearchComponents.clear();

        for (Card panel
                : new ArrayList<>(previousMatchedCards)) {

            restoreRememberedComponent(panel);
        }

        previousMatchedCards.clear();

        if (targetPanel != null) {
            targetPanel.revalidate();
            targetPanel.repaint();
        }
    }

    private void restoreRememberedComponent(JComponent jc) {
        if (jc instanceof TextRow row) {
            row.clearHighlight();
        }

        if (jc instanceof TextBlock block) {
            block.clearHighlight();
        }

        Object badge =
                jc.getClientProperty(HIDDEN_HIT_BADGE_PROPERTY);

        if (badge instanceof JLabel label) {
            jc.remove(label);
            jc.putClientProperty(HIDDEN_HIT_BADGE_PROPERTY, null);
            jc.revalidate();
        }

        if (!Boolean.TRUE.equals(
                jc.getClientProperty("quiz.search.remembered"))) {
            return;
        }

        Object oldBorder =
                jc.getClientProperty(OLD_BORDER_PROPERTY);

        Object oldBackground =
                jc.getClientProperty(OLD_BACKGROUND_PROPERTY);

        Object oldOpaque =
                jc.getClientProperty(OLD_OPAQUE_PROPERTY);

        Object oldForeground =
                jc.getClientProperty(OLD_FOREGROUND_PROPERTY);

        Object oldText =
                jc.getClientProperty(OLD_LABEL_TEXT_PROPERTY);

        jc.setBorder(oldBorder instanceof Border b ? b : null);

        if (oldBackground instanceof Color color) {
            jc.setBackground(color);
        }

        if (oldOpaque instanceof Boolean opaque) {
            jc.setOpaque(opaque);
        }

        if (jc instanceof JLabel lbl) {
            if (oldForeground instanceof Color color) {
                lbl.setForeground(color);
            }

            if (oldText instanceof String s) {
                lbl.setText(s);
            }
        }

        if (jc instanceof Card qp) {
            qp.setHighlightColor(null);
        }

        jc.putClientProperty("quiz.search.remembered", null);
        jc.putClientProperty(OLD_BORDER_PROPERTY, null);
        jc.putClientProperty(OLD_BACKGROUND_PROPERTY, null);
        jc.putClientProperty(OLD_OPAQUE_PROPERTY, null);
        jc.putClientProperty(OLD_FOREGROUND_PROPERTY, null);
        jc.putClientProperty(OLD_LABEL_TEXT_PROPERTY, null);
    }

    /*
     * Full clear is kept for debugging/future reset actions. It is no longer
     * used on every keypress because it walks the whole component tree.
     */
    @SuppressWarnings("unused")
    private void clearHighlightsDeep(Component c) {
        if (c instanceof JComponent jc) {
            restoreRememberedComponent(jc);
        }

        if (c instanceof Container ct) {
            for (Component child : ct.getComponents()) {
                clearHighlightsDeep(child);
            }
        }
    }

    private void markCurrentHit(JComponent c) {
        if (currentHit != null) {
            restoreBorderOnly(currentHit);
        }

        currentHit =
                c;
    }

    private void restoreBorderOnly(JComponent c) {
        Object o =
                c.getClientProperty(OLD_BORDER_PROPERTY);

        c.setBorder(o instanceof Border b ? b : null);
        c.repaint();
    }

    private boolean containsImagePane(Component c) {
        if (c instanceof ImagePane) {
            return true;
        }

        if (c instanceof Container ct) {
            for (Component child : ct.getComponents()) {
                if (containsImagePane(child)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void scrollTo(JComponent c) {
        if (targetScrollPane == null || c == null) {
            return;
        }
        // Deferred: revealing a hit inside a collapsed collection rebuilds/
        // relayouts the card, so the component's bounds aren't final on this
        // pass — converting them now would scroll to a stale (often zero)
        // position. Run after the pending layout so the hit lands in view.
        SwingUtilities.invokeLater(() -> {
            if (c.getParent() == null) {
                return;
            }
            Rectangle rect =
                    SwingUtilities.convertRectangle(
                            c.getParent(),
                            c.getBounds(),
                            targetPanel);
            rect.y -= 24;
            rect.height += 48;
            targetPanel.scrollRectToVisible(rect);
        });
    }

    private void clearResults() {
        resultsPanel.removeAll();
        resultsPanel.revalidate();
        resultsPanel.repaint();
    }

    private void addHitGroupRow(HitGroup g) {
        JPanel row =
                new JPanel(new BorderLayout(4, 0));

        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        g.label =
                new JLabel();

        g.updateLabel();

        JButton prevBtn =
                new JButton("<");

        JButton nextBtn =
                new JButton(">");

        prevBtn.setMargin(new Insets(2, 4, 2, 4));
        nextBtn.setMargin(new Insets(2, 4, 2, 4));

        prevBtn.addActionListener(e -> navigate(g, -1));
        nextBtn.addActionListener(e -> navigate(g, 1));

        JPanel navPanel =
                new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));

        navPanel.add(prevBtn);
        navPanel.add(nextBtn);

        row.add(g.label, BorderLayout.CENTER);
        row.add(navPanel, BorderLayout.EAST);

        resultsPanel.add(row);
    }

    private void navigate(HitGroup g, int delta) {
        if (g.hits.isEmpty()) {
            return;
        }

        g.index =
                Math.floorMod(g.index + delta, g.hits.size());

        g.updateLabel();

        JComponent target =
                g.hits.get(g.index);

        markCurrentHit(target);
        scrollTo(target);
    }

    // --- Virtualized (data-centric) search: hits are viewables, navigated one at
    // a time; each card is built on demand by the VirtualizedCardList. ---

    private void searchSyncVirtual(List<String> queryTokens) {
        Map<String, List<Viewable>> matchesByField =
                searchAndSort.searchViewables(
                        virtualList.items(),
                        queryTokens,
                        effectiveConfig(searchEditor, subtypeSearchEditors, null));

        // Remember the hits so a card rebuilt on scroll-back gets re-highlighted.
        virtualHits.clear();
        matchesByField.values().forEach(virtualHits::addAll);

        Map<String, ViewableFieldPaths.FieldPath> pathByTitle =
                new LinkedHashMap<>();

        Viewable sample = virtualList.items().isEmpty()
                ? null
                : virtualList.items().get(0);

        for (ViewableFieldPaths.FieldPath fp
                : ViewableFieldPaths.collectFromSample(
                sample,
                effectiveConfig(searchEditor, subtypeSearchEditors, null),
                ViewableFieldPaths.NOT_MEDIA_FIELDS)) {

            pathByTitle.put(fp.title(), fp);
        }

        Map<String, HitGroupQ> groups = new LinkedHashMap<>();

        for (Map.Entry<String, List<Viewable>> e : matchesByField.entrySet()) {
            HitGroupQ g = new HitGroupQ(
                    e.getKey(),
                    pathByTitle.get(e.getKey()),
                    queryTokens);
            g.hits.addAll(e.getValue());
            groups.put(e.getKey(), g);
        }

        showSearchResultsVirtual(groups);
    }

    private void showSearchResultsVirtual(Map<String, HitGroupQ> groups) {
        resultsPanel.removeAll();

        HitGroupQ first = null;
        for (HitGroupQ g : groups.values()) {
            if (g.hits.isEmpty()) {
                continue;
            }
            addHitGroupRowVirtual(g);
            if (first == null) {
                first = g;
            }
        }

        resultsPanel.revalidate();
        resultsPanel.repaint();

        if (first != null) {
            first.index = 0;
            first.updateLabel();
            navigateToCurrentVirtual(first);
        }
    }

    private void addHitGroupRowVirtual(HitGroupQ g) {
        JPanel row = new JPanel(new BorderLayout(4, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        g.label = new JLabel();
        g.updateLabel();

        JButton prevBtn = new JButton("<");
        JButton nextBtn = new JButton(">");
        prevBtn.setMargin(new Insets(2, 4, 2, 4));
        nextBtn.setMargin(new Insets(2, 4, 2, 4));
        prevBtn.addActionListener(e -> navigateVirtual(g, -1));
        nextBtn.addActionListener(e -> navigateVirtual(g, 1));

        JPanel navPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        navPanel.add(prevBtn);
        navPanel.add(nextBtn);

        row.add(g.label, BorderLayout.CENTER);
        row.add(navPanel, BorderLayout.EAST);

        resultsPanel.add(row);
    }

    private void navigateVirtual(HitGroupQ g, int delta) {
        if (g.hits.isEmpty()) {
            return;
        }
        g.index = Math.floorMod(g.index + delta, g.hits.size());
        g.updateLabel();
        navigateToCurrentVirtual(g);
    }

    /**
     * Reveals the current data hit through the target container. For a grouped
     * target this expands the matching group path and lazily materializes the
     * leaf card. The ordinary Card expansion/highlight machinery is
     * then reused unchanged.
     */
    private void navigateToCurrentVirtual(HitGroupQ g) {
        clearHighlights();

        Viewable q = g.hits.get(g.index);
        JComponent card = virtualList.navigateToTop(q);

        if (card == null) {
            return;
        }

        if (card instanceof Card qp) {
            if (g.fieldPath != null
                    && qp.expandCollectionsOnPath(g.fieldPath.path())) {
                qp.refresh();
            }

            highlightCard(qp);

            if (fieldHighlightBox.isSelected() && g.fieldPath != null) {
                List<JComponent> fieldHits =
                        collectMatchingFieldRows(
                                qp,
                                g.fieldPath.path(),
                                g.queryTokens);

                if (fieldHits.isEmpty()) {
                    addHiddenHitBadge(qp, g.title);
                } else {
                    for (JComponent hit : fieldHits) {
                        highlightField(hit);
                    }

                    highlightTextRecursively(
                            qp,
                            g.fieldPath.path(),
                            g.queryTokens);
                }
            }
        }

        markCurrentHitVirtual(card);

        targetPanel.revalidate();
        targetPanel.repaint();
    }

    private void markCurrentHitVirtual(JComponent c) {
        currentHit = c;
        remember(c);
        c.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xFF8800), 3, true),
                c.getBorder()));
        c.repaint();
    }

    private static class HitGroupQ {
        final String title;
        final List<Viewable> hits = new ArrayList<>();
        final ViewableFieldPaths.FieldPath fieldPath;
        final List<String> queryTokens;
        int index = 0;
        JLabel label;

        HitGroupQ(
                String title,
                ViewableFieldPaths.FieldPath fieldPath,
                List<String> queryTokens) {
            this.title = title;
            this.fieldPath = fieldPath;
            this.queryTokens = List.copyOf(queryTokens);
        }

        void updateLabel() {
            if (label != null) {
                int displayIndex = hits.isEmpty() ? 0 : index + 1;
                label.setText(title + " (" + displayIndex + "/" + hits.size() + ")");
            }
        }
    }

    private static class HitGroup {
        final String title;
        final List<JComponent> hits =
                new ArrayList<>();

        int index =
                0;

        JLabel label;

        HitGroup(String title) {
            this.title = title;
        }

        void updateLabel() {
            if (label != null) {
                int displayIndex =
                        hits.isEmpty() ? 0 : index + 1;

                label.setText(
                        title
                                + " ("
                                + displayIndex
                                + "/"
                                + hits.size()
                                + ")");
            }
        }
    }
}
