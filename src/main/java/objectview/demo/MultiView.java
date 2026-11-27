package objectview.demo;

import objectview.Viewable;
import objectview.render.RenderContext;
import objectview.search.MultiSearchBar;
import objectview.search.SearchPanel;
import objectview.view.SearchableView;
import objectview.view.SectionVisibilityControl;
import objectview.viewconfig.FieldTypeSource;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Hosts several {@link objectview.render.CardListView}s side by side that share one
 * {@link RenderContext}, so a reference field in one view (e.g. a
 * constellation's {@code stars}) renders as a chip that, on click, scrolls
 * to and flashes the target's card in its own view — and vice versa.
 *
 * Usage:
 * <pre>
 *   MultiView mv = new MultiView();
 *   mv.addSection("Constellations", Constellation.class, constellations);
 *   mv.addSection("Stars", Star.class, stars);
 *   mv.build(1);
 * </pre>
 *
 * All objects across all sections are pre-registered before any card is
 * built, which is what makes a reference resolve to a chip rather than an
 * embedded card. Larger sets of sections are paged without changing this
 * registry or replacing the existing section components.
 */
public class MultiView extends JPanel {

    public static final int DEFAULT_SECTIONS_PER_TAB = 4;

    private final RenderContext context = new RenderContext();
    private final List<Section> sections = new ArrayList<>();
    private final List<SearchableView> sectionViews = new ArrayList<>();
    private final List<JComponent> sectionBodies = new ArrayList<>();
    private final List<SearchPanel> sectionEngines = new ArrayList<>();
    private final JPanel center = new JPanel(new BorderLayout());
    private final JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP, JTabbedPane.SCROLL_TAB_LAYOUT);
    private final SectionPages pages = new SectionPages();
    private Set<String> requestedVisibleSections;
    private Set<String> visibleSections = Set.of();
    private Consumer<Set<String>> visibilityListener = ignored -> { };
    private String sectionNoun = "Sections";
    private SectionVisibilityControl visibilityControl;
    private MultiSearchBar searchBar;
    private int sectionsPerTab = Integer.MAX_VALUE;
    private JSpinner tabSize;
    private IntConsumer tabSizeListener = ignored -> { };
    private boolean built;

    public MultiView() {
        super(new BorderLayout());
        context.setInPlaceNavigation(true);
    }

    /** The shared context, e.g. to pre-register extra objects. */
    public RenderContext context() {
        return context;
    }

    /** The built sections, in order — for tests that drive one section's own config. */
    List<SearchableView> sectionViews() {
        return java.util.Collections.unmodifiableList(sectionViews);
    }

    /** The stable section keys whose panels are currently projected. Objects remain. */
    public Set<String> visibleSectionTitles() {
        return visibleSections;
    }

    /** How the shared chooser names these sections, for example "Classes". */
    public void sectionNoun(String value) {
        if (built) throw new IllegalStateException("sectionNoun() after build()");
        sectionNoun = value == null || value.isBlank() ? "Sections" : value;
    }

    /** Retains stable section keys when an owning window rebuilds this view. */
    public void visibleSections(Collection<String> titles) {
        requestedVisibleSections = titles == null ? null : new LinkedHashSet<>(titles);
        if (built) applyVisibleSections(requestedVisibleSections, true);
    }

    public void onVisibleSectionsChanged(Consumer<Set<String>> listener) {
        visibilityListener = listener == null ? ignored -> { } : listener;
    }

    /** Opts the owner into paging. Unconfigured views remain side by side. */
    public void sectionsPerTab(int count) {
        if (count < 1) throw new IllegalArgumentException("At least one section per tab");
        if (built && sectionsPerTab == Integer.MAX_VALUE) {
            throw new IllegalStateException("Enable section tabs before build()");
        }
        if (sectionsPerTab == count) return;
        sectionsPerTab = count;
        if (tabSize != null) tabSize.setValue(count);
        if (built) pages.rebuild();
    }

    public void onSectionsPerTabChanged(IntConsumer listener) {
        tabSizeListener = listener == null ? ignored -> { } : listener;
    }

    public void addSection(
            String title,
            Class<? extends Viewable> type,
            List<? extends Viewable> objects) {

        addSection(title, title, type, objects, null, null, null,
                null, null);
    }

    /** Adds a section whose stable visibility key differs from its decorated title. */
    public void addSection(
            String key,
            String title,
            Class<? extends Viewable> type,
            List<? extends Viewable> objects) {
        addSection(key, title, type, objects, null, null, null,
                null, null);
    }

    /** Adds a non-card ObjectView section (for example a group outline) while keeping
     *  the same side-by-side owner and top-level navigation registry. */
    public void addSection(
            String title,
            JComponent body,
            List<? extends Viewable> topLevelObjects) {
        addSection(title, title, body, topLevelObjects);
    }

    /** Adds a custom section whose stable visibility key differs from its title. */
    public void addSection(
            String key,
            String title,
            JComponent body,
            List<? extends Viewable> topLevelObjects) {
        java.util.Objects.requireNonNull(body, "section body");
        addCustomSection(key, title, () -> body, topLevelObjects, 1, false);
    }

    /** Gives a composite section room for two ordinary columns on its page. */
    public void addWideSection(String key, String title, Supplier<? extends JComponent> body,
                               List<? extends Viewable> topLevelObjects) {
        addCustomSection(key, title, body, topLevelObjects, 2, false);
    }

    /** Adds a section with its own nested views, built after all top levels are known. */
    public void addDedicatedTab(String key, String title, Supplier<? extends JComponent> body,
                                List<? extends Viewable> topLevelObjects) {
        addCustomSection(key, title, body, topLevelObjects, 1, true);
    }

    private void addCustomSection(String key, String title, Supplier<? extends JComponent> body,
                                  List<? extends Viewable> topLevelObjects, int columns, boolean dedicatedTab) {
        if (built) throw new IllegalStateException("addSection() after build()");
        sections.add(new Section(key, title, null,
                topLevelObjects == null ? new ArrayList<>()
                        : new ArrayList<>(topLevelObjects),
                null, null, null, null, null,
                java.util.Objects.requireNonNull(body, "section body"), columns, dedicatedTab));
    }

    /** As {@link #addSection(String, Class, List)}, but with the per-section
     *  dynamic config: a {@code sample} instance (so the search/sort/view editors
     *  enumerate map-held fields), plus optional {@code hiddenFields} and
     *  {@code fieldTypes} — so a DynamicFields section stays model-typed. */
    public void addSection(
            String title,
            Class<? extends Viewable> type,
            List<? extends Viewable> objects,
            Viewable sample,
            java.util.Set<String> hiddenFields,
            FieldTypeSource fieldTypes) {

        addSection(title, type, objects, sample, hiddenFields, fieldTypes,
                null, null);
    }

    public void addSection(
            String title,
            Class<? extends Viewable> type,
            List<? extends Viewable> objects,
            Viewable sample,
            java.util.Set<String> hiddenFields,
            FieldTypeSource fieldTypes,
            SearchPanel.ConfigState configState,
            Consumer<SearchPanel.ConfigState> configListener) {

        if (built) {
            throw new IllegalStateException("addSection() after build()");
        }
        addSection(title, title, type, objects, sample, hiddenFields, fieldTypes,
                configState, configListener);
    }

    /** Adds a card section whose stable visibility key differs from its title, with the
     *  per-section schema inputs ({@code sample}, {@code hiddenFields}, {@code fieldTypes})
     *  that keep a DynamicFields section typed by its model class. */
    public void addSection(
            String key,
            String title,
            Class<? extends Viewable> type,
            List<? extends Viewable> objects,
            Viewable sample,
            java.util.Set<String> hiddenFields,
            FieldTypeSource fieldTypes,
            SearchPanel.ConfigState configState,
            Consumer<SearchPanel.ConfigState> configListener) {
        if (built) {
            throw new IllegalStateException("addSection() after build()");
        }
        sections.add(new Section(
                key,
                title,
                type,
                new ArrayList<>(objects),
                sample,
                hiddenFields,
                fieldTypes,
                configState,
                configListener,
                null, 1, false));
    }

    public void build(int columnsPerView) {
        if (built) {
            return;
        }
        built = true;

        Set<String> initial = initialVisibleSections();
        // Visibility controls layout and search, not whether a loaded reference can
        // navigate. All class sections supply targets before cards are rendered.
        sections.forEach(section -> context.addTopLevels(section.objects()));
        List<SearchPanel> engines = new ArrayList<>();
        for (Section s : sections) {
            JComponent body = buildSection(s, columnsPerView, engines);
            sectionBodies.add(body);
            SearchableView view = sectionViews.get(sectionViews.size() - 1);
            SearchPanel engine = view == null ? null : view.search();
            int index = sectionEngines.size();
            if (engine != null) engine.onHitNavigation(() -> pages.show(pages.bySection.get(index)));
            sectionEngines.add(engine);
        }

        for (int index = 0; index < sections.size(); index++) {
            for (Viewable target : sections.get(index).objects()) {
                pages.registerRevealer(target, index);
            }
        }
        JPanel root = new JPanel(new BorderLayout(0, 4));
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JPanel controls = new JPanel(new BorderLayout());
        JButton back = new JButton(context.backAction());
        back.setName("objectview.navigation.back");
        JPanel backSlot = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        backSlot.add(back);
        controls.add(backSlot, BorderLayout.EAST);
        if (sections.size() > 1) {
            visibilityControl = new SectionVisibilityControl(sectionNoun,
                    sections.stream().map(section -> new SectionVisibilityControl.Option(
                            section.key(), section.title() + " ("
                                    + section.objects().size() + ")")).toList(),
                    initial,
                    selected -> applyVisibleSections(selected, true));
            controls.add(visibilityControl, BorderLayout.WEST);
            if (sectionsPerTab != Integer.MAX_VALUE) {
                JPanel sizeControl = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
                tabSize = new JSpinner(new SpinnerNumberModel(sectionsPerTab, 1, null, 1));
                boolean hasWideSections = sections.stream().anyMatch(section -> section.columns() > 1);
                JLabel label = new JLabel(hasWideSections ? "Panel slots per tab:" : sectionNoun + " per tab:");
                label.setLabelFor(tabSize);
                if (hasWideSections) {
                    label.setToolTipText("A wide section uses two panel slots.");
                    tabSize.setToolTipText(label.getToolTipText());
                }
                tabSize.addChangeListener(ignored -> {
                    int count = (Integer) tabSize.getValue();
                    if (count == sectionsPerTab) return;
                    sectionsPerTab(count);
                    tabSizeListener.accept(count);
                });
                sizeControl.add(label);
                sizeControl.add(tabSize);
                controls.add(sizeControl, BorderLayout.CENTER);
            }
        }
        header.add(controls);
        searchBar = new MultiSearchBar(engines);
        header.add(searchBar);
        root.add(header, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);

        add(root, BorderLayout.CENTER);
        applyVisibleSections(initial, false);
        revalidate();
    }

    private Set<String> initialVisibleSections() {
        Set<String> available = sections.stream().map(Section::key)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (requestedVisibleSections == null) return available;
        available.retainAll(requestedVisibleSections);
        return available;
    }

    private void applyVisibleSections(Collection<String> selected, boolean notify) {
        Set<String> available = sections.stream().map(Section::key)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> next = new LinkedHashSet<>();
        if (selected != null) selected.stream().filter(available::contains).forEach(next::add);
        requestedVisibleSections = new LinkedHashSet<>(next);
        visibleSections = java.util.Collections.unmodifiableSet(new LinkedHashSet<>(next));

        List<Viewable> visibleObjects = new ArrayList<>();
        List<Viewable> hiddenObjects = new ArrayList<>();
        List<SearchPanel> visibleEngines = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            Section section = sections.get(index);
            if (next.contains(section.key())) {
                visibleObjects.addAll(section.objects());
                SearchPanel engine = sectionEngines.get(index);
                if (engine != null) visibleEngines.add(engine);
            } else {
                hiddenObjects.addAll(section.objects());
            }
        }

        context.deselect(hiddenObjects);
        context.setSelectionOrder(visibleObjects);
        searchBar.showEngines(visibleEngines);
        searchBar.setVisible(!visibleEngines.isEmpty());
        if (visibilityControl != null) {
            visibilityControl.selectWithoutNotification(next);
        }

        pages.rebuild();
        if (notify) visibilityListener.accept(visibleSections);
    }

    private JComponent buildSection(
            Section s, int columns, List<SearchPanel> engines) {
        if (s.customBody() != null) {
            sectionViews.add(null);
            JPanel body = new JPanel(new BorderLayout(4, 4));
            body.setBorder(BorderFactory.createTitledBorder(s.title()));
            if (sections.size() > 1) {
                body.add(closeSectionControl(s.key(), s.title()), BorderLayout.NORTH);
            }
            body.add(s.customBody().get(), BorderLayout.CENTER);
            return body;
        }
        SearchableView view = SearchableView.builder(s.objects())
                .type(s.type())
                .sectionName(s.key())
                .sample(s.sample())
                .hiddenFields(s.hiddenFields())
                .fieldTypes(s.fieldTypes())
                .configState(s.configState())
                .configListener(s.configListener())
                .renderContext(context)
                .coordinated(true)
                .columns(columns)
                .build();
        sectionViews.add(view);

        JPanel body = new JPanel(new BorderLayout(4, 4));
        // Class name + instance count on the section border, so each class shows
        // its own total in place instead of only in the window caption.
        body.setBorder(BorderFactory.createTitledBorder(
                s.title() + "  (" + s.objects().size() + ")"));
        if (sections.size() > 1) {
            body.add(closeSectionControl(s.key(), s.title()), BorderLayout.NORTH);
        }

        if (!s.objects().isEmpty()) {
            // A per-section search engine in coordinated mode: its own input +
            // config toolbar is hidden (the shared bar owns those), but it keeps
            // its own per-field results panel + per-panel navigation, and
            // highlights this section's cards. Driven by the shared bar.
            if (view.search() != null) engines.add(view.search());
        }

        body.add(view, BorderLayout.CENTER);
        return body;
    }

    private JComponent closeSectionControl(String key, String title) {
        JButton close = new JButton("\u00d7");
        close.setToolTipText("Hide " + title);
        close.getAccessibleContext().setAccessibleName("Hide " + title);
        close.setMargin(new Insets(0, 6, 0, 6));
        close.addActionListener(ignored -> hideSection(key));
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        row.add(close);
        return row;
    }

    private void hideSection(String key) {
        if (key == null || !visibleSections.contains(key)) return;
        Set<String> next = new LinkedHashSet<>(visibleSections);
        next.remove(key);
        applyVisibleSections(next, true);
    }

    public void appendViewables(java.util.Collection<? extends Viewable> values) {
        if (values == null) return;
        for (Viewable value : values) {
            for (int i = 0; i < sections.size(); i++) {
                if (java.util.Objects.equals(sections.get(i).key(), value.typeName())) {
                    SearchableView view = sectionViews.get(i);
                    if (view != null
                            && sections.get(i).objects().stream().noneMatch(existing -> existing == value)) {
                        sections.get(i).objects().add(value);
                        view.appendViewables(java.util.List.of(value));
                        pages.registerRevealer(value, i);
                        pages.addOwner(value, i);
                    }
                    break;
                }
            }
        }
    }

    public void removeViewables(java.util.Collection<? extends Viewable> values) {
        if (values == null || values.isEmpty()) return;
        Set<Viewable> removed = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        removed.addAll(values);
        for (int index = 0; index < sections.size(); index++) {
            SearchableView view = sectionViews.get(index);
            if (view == null) continue;
            sections.get(index).objects().removeIf(removed::contains);
            view.removeViewables(values);
        }
        // Unlike hiding a panel, removing an instance removes its target.
        context.setTopLevels(sections.stream().flatMap(section -> section.objects().stream()).toList());
        pages.rebuild();
        for (int index = 0; index < sections.size(); index++) {
            SearchableView view = sectionViews.get(index);
            if (view != null) view.refreshViewables(sections.get(index).objects());
        }
    }

    public void refreshViewables(java.util.Collection<? extends Viewable> values) {
        for (SearchableView view : sectionViews) if (view != null) view.refreshViewables(values);
    }

    /** Keeps the sections on each tab visible side by side. */
    private JComponent layout(List<Integer> indices) {
        List<JComponent> bodies = indices.stream().map(sectionBodies::get).toList();
        if (bodies.isEmpty()) {
            return new JPanel();
        }
        if (bodies.size() == 1) {
            return bodies.get(0);
        }
        if (indices.stream().anyMatch(index -> sections.get(index).columns() > 1)) {
            JPanel grid = new JPanel(new GridBagLayout());
            for (int i = 0; i < bodies.size(); i++) {
                GridBagConstraints cell = new GridBagConstraints();
                cell.gridx = i;
                cell.weightx = sections.get(indices.get(i)).columns();
                cell.weighty = 1;
                cell.fill = GridBagConstraints.BOTH;
                if (i > 0) cell.insets = new Insets(0, 6, 0, 0);
                // The declared widths, not a child's preferred width, divide the page.
                JPanel slot = new JPanel(new BorderLayout());
                slot.setPreferredSize(new Dimension(0, 0));
                slot.setMinimumSize(new Dimension(0, 0));
                slot.add(bodies.get(i), BorderLayout.CENTER);
                grid.add(slot, cell);
            }
            return grid;
        }
        if (bodies.size() == 2) {
            JSplitPane split = new JSplitPane(
                    JSplitPane.HORIZONTAL_SPLIT,
                    bodies.get(0),
                    bodies.get(1));
            split.setResizeWeight(0.5);
            split.setContinuousLayout(true);
            return split;
        }

        JPanel grid = new JPanel(new GridLayout(1, bodies.size(), 6, 6));
        bodies.forEach(grid::add);
        return grid;
    }

    private record Page(List<Integer> sections, String title, JPanel body) { }

    private final class SectionPages {
        private final Map<Object, SectionRoute> routes = new IdentityHashMap<>();
        private final Map<Integer, Page> bySection = new java.util.HashMap<>();
        private final List<Page> ordered = new ArrayList<>();

        private final class SectionRoute implements Runnable {
            private final int firstSection;
            private Page visiblePage;

            private SectionRoute(int firstSection) { this.firstSection = firstSection; }

            @Override public void run() {
                if (visiblePage == null) {
                    String key = sections.get(firstSection).key();
                    if (!visibleSections.contains(key)) {
                        Set<String> next = new LinkedHashSet<>(visibleSections);
                        next.add(key);
                        applyVisibleSections(next, true);
                    }
                }
                show(visiblePage);
            }
        }

        SectionPages() {
            tabs.setName("objectview.sections.tabs");
            tabs.addChangeListener(ignored -> context.clearBack());
        }

        private void rebuild() {
            Page selected = selectedPage();
            List<Integer> previous = selected == null ? List.of() : selected.sections();
            context.clearBack();
            center.removeAll();
            tabs.removeAll();
            ordered.clear();
            bySection.clear();
            routes.values().forEach(route -> route.visiblePage = null);
            List<Integer> current = new ArrayList<>();
            int usedColumns = 0;
            for (int i = 0; i < sections.size(); i++) {
                String key = sections.get(i).key();
                if (!visibleSections.contains(key)) continue;
                if (sections.get(i).dedicatedTab()) {
                    addPage(current);
                    current.clear();
                    usedColumns = 0;
                    addPage(List.of(i));
                } else {
                    int columns = Math.min(sectionsPerTab, sections.get(i).columns());
                    if (usedColumns + columns > sectionsPerTab) {
                        addPage(current);
                        current.clear();
                        usedColumns = 0;
                    }
                    current.add(i);
                    usedColumns += columns;
                }
            }
            addPage(current);
            if (ordered.size() > 1) {
                ordered.forEach(page -> tabs.addTab(page.title(), page.body()));
                previous.stream().map(bySection::get).filter(java.util.Objects::nonNull)
                        .findFirst().ifPresent(page -> tabs.setSelectedComponent(page.body()));
                center.add(tabs, BorderLayout.CENTER);
            } else if (!ordered.isEmpty()) {
                center.add(ordered.get(0).body(), BorderLayout.CENTER);
            }
            center.revalidate();
            center.repaint();
        }

        private void addPage(List<Integer> indices) {
            if (indices.isEmpty()) return;
            String title = indices.size() == 1 ? sections.get(indices.get(0)).title()
                    : indices.stream().map(i -> sections.get(i).key())
                            .collect(Collectors.joining(" · "));
            JPanel body = new JPanel(new BorderLayout());
            body.add(layout(indices), BorderLayout.CENTER);
            Page page = new Page(List.copyOf(indices), title, body);
            ordered.add(page);
            for (int index : indices) {
                bySection.put(index, page);
                sections.get(index).objects().forEach(value -> addOwner(value, index));
            }
        }

        private void addOwner(Viewable value, int index) {
            SectionRoute route = routes.get(value);
            if (route != null && route.visiblePage == null) route.visiblePage = bySection.get(index);
        }

        private Page selectedPage() {
            if (ordered.size() == 1) return ordered.get(0);
            int index = tabs.getSelectedIndex();
            return index < 0 ? null : ordered.get(index);
        }

        private void registerRevealer(Object target, int index) {
            routes.computeIfAbsent(target, value -> {
                SectionRoute route = new SectionRoute(index);
                context.prependTopLevelRevealer(value, route);
                return route;
            });
        }

        private void show(Page page) {
            if (page != null && ordered.size() > 1 && page != selectedPage()) {
                tabs.setSelectedComponent(page.body());
            }
        }
    }

    private record Section(
            String key,
            String title,
            Class<? extends Viewable> type,
            List<Viewable> objects,
            Viewable sample,
            java.util.Set<String> hiddenFields,
            FieldTypeSource fieldTypes,
            SearchPanel.ConfigState configState,
            Consumer<SearchPanel.ConfigState> configListener,
            Supplier<? extends JComponent> customBody,
            int columns,
            boolean dedicatedTab) {
    }
}
