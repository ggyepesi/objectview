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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

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
 * embedded card.
 */
public class MultiView extends JPanel {

    private final RenderContext context = new RenderContext();
    private final List<Section> sections = new ArrayList<>();
    private final List<SearchableView> sectionViews = new ArrayList<>();
    private final List<JComponent> sectionBodies = new ArrayList<>();
    private final List<SearchPanel> sectionEngines = new ArrayList<>();
    private final JPanel center = new JPanel(new BorderLayout());
    private Set<String> requestedVisibleSections;
    private Set<String> visibleSections = Set.of();
    private Consumer<Set<String>> visibilityListener = ignored -> { };
    private String sectionNoun = "Sections";
    private SectionVisibilityControl visibilityControl;
    private MultiSearchBar searchBar;
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
        if (built) throw new IllegalStateException("addSection() after build()");
        sections.add(new Section(key, title, null,
                topLevelObjects == null ? new ArrayList<>()
                        : new ArrayList<>(topLevelObjects),
                null, null, null, null, null,
                java.util.Objects.requireNonNull(body, "section body")));
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
                null));
    }

    public void build(int columnsPerView) {
        if (built) {
            return;
        }
        built = true;

        Set<String> initial = initialVisibleSections();
        for (Section section : sections) {
            if (initial.contains(section.key())) context.addTopLevels(section.objects());
        }
        List<SearchPanel> engines = new ArrayList<>();
        for (Section s : sections) {
            JComponent body = buildSection(s, columnsPerView, engines);
            sectionBodies.add(body);
            SearchableView view = sectionViews.get(sectionViews.size() - 1);
            sectionEngines.add(view == null ? null : view.search());
        }

        JPanel root = new JPanel(new BorderLayout(0, 4));
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        if (sections.size() > 1) {
            visibilityControl = new SectionVisibilityControl(sectionNoun,
                    sections.stream().map(section -> new SectionVisibilityControl.Option(
                            section.key(), section.title() + " ("
                                    + section.objects().size() + ")")).toList(),
                    initial,
                    selected -> applyVisibleSections(selected, true));
            header.add(visibilityControl);
        }
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
        List<JComponent> visibleBodies = new ArrayList<>();
        List<SearchPanel> visibleEngines = new ArrayList<>();
        for (int index = 0; index < sections.size(); index++) {
            Section section = sections.get(index);
            if (next.contains(section.key())) {
                visibleObjects.addAll(section.objects());
                visibleBodies.add(sectionBodies.get(index));
                SearchPanel engine = sectionEngines.get(index);
                if (engine != null) visibleEngines.add(engine);
            } else {
                hiddenObjects.addAll(section.objects());
            }
        }

        context.deselect(hiddenObjects);
        context.setTopLevels(visibleObjects);
        context.setSelectionOrder(visibleObjects);
        for (int index = 0; index < sections.size(); index++) {
            SearchableView view = sectionViews.get(index);
            if (view != null && next.contains(sections.get(index).key())) {
                view.refreshViewables(sections.get(index).objects());
            }
        }
        searchBar.showEngines(visibleEngines);
        searchBar.setVisible(!visibleEngines.isEmpty());
        if (visibilityControl != null) {
            visibilityControl.selectWithoutNotification(next);
        }

        center.removeAll();
        center.add(layout(visibleBodies), BorderLayout.CENTER);
        center.revalidate();
        center.repaint();
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
            body.add(s.customBody(), BorderLayout.CENTER);
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
                    if (view != null) view.appendViewables(java.util.List.of(value));
                    break;
                }
            }
        }
    }

    public void removeViewables(java.util.Collection<? extends Viewable> values) {
        for (SearchableView view : sectionViews) if (view != null) view.removeViewables(values);
    }

    public void refreshViewables(java.util.Collection<? extends Viewable> values) {
        for (SearchableView view : sectionViews) if (view != null) view.refreshViewables(values);
    }

    /**
     * Lays the sections out side by side (split for two, grid for more).
     *
     * This remains deliberate: ordinary multi-class results benefit from seeing
     * related cards simultaneously. A tabbed owner is supported now, but it must call
     * {@link RenderContext#registerTopLevelRevealer(Object, Runnable)} for each hidden
     * card so {@code focusTopLevel} selects the owning tab before it scrolls.
     */
    private JComponent layout(List<JComponent> bodies) {
        if (bodies.isEmpty()) {
            return new JPanel();
        }
        if (bodies.size() == 1) {
            return bodies.get(0);
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
            JComponent customBody) {
    }
}
