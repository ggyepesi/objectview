package objectview.demo;

import objectview.Viewable;
import objectview.render.CardListView;
import objectview.render.RenderContext;
import objectview.search.MultiSearchBar;
import objectview.search.SearchPanel;
import objectview.viewconfig.FieldTypeSource;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Hosts several {@link CardListView}s side by side that share one
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
    private boolean built;

    public MultiView() {
        super(new BorderLayout());
        context.setInPlaceNavigation(true);
    }

    /** The shared context, e.g. to pre-register extra objects. */
    public RenderContext context() {
        return context;
    }

    public void addSection(
            String title,
            Class<? extends Viewable> type,
            List<? extends Viewable> objects) {

        addSection(title, type, objects, null, null, null);
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

        if (built) {
            throw new IllegalStateException("addSection() after build()");
        }
        sections.add(new Section(
                title,
                type,
                new ArrayList<>(objects),
                sample,
                hiddenFields,
                fieldTypes));
    }

    public void build(int columnsPerView) {
        if (built) {
            return;
        }
        built = true;

        // Pre-register every object so cross-references render as chips.
        for (Section s : sections) {
            for (Viewable q : s.objects()) {
                context.addTopLevel(q);
            }
        }

        List<JComponent> bodies = new ArrayList<>();
        List<SearchPanel> engines = new ArrayList<>();
        for (Section s : sections) {
            bodies.add(buildSection(s, columnsPerView, engines));
        }

        // One search bar across all sections (each section keeps its own class
        // config); the bar fans the query out, every section highlights its own
        // matches, and navigation is unified.
        JPanel root = new JPanel(new BorderLayout(0, 4));
        if (!engines.isEmpty()) {
            root.add(new MultiSearchBar(engines), BorderLayout.NORTH);
        }
        root.add(layout(bodies), BorderLayout.CENTER);

        add(root, BorderLayout.CENTER);
        revalidate();
    }

    private JComponent buildSection(
            Section s, int columns, List<SearchPanel> engines) {
        CardListView view = new CardListView();
        view.setRenderContext(context);

        for (Viewable q : s.objects()) {
            view.addViewable(q);
        }
        view.createCardsPanel(Math.max(1, columns));

        JPanel body = new JPanel(new BorderLayout(4, 4));
        // Class name + instance count on the section border, so each class shows
        // its own total in place instead of only in the window caption.
        body.setBorder(BorderFactory.createTitledBorder(
                s.title() + "  (" + s.objects().size() + ")"));

        if (!s.objects().isEmpty()) {
            // A per-section search engine in coordinated mode: its own input +
            // config toolbar is hidden (the shared bar owns those), but it keeps
            // its own per-field results panel + per-panel navigation, and
            // highlights this section's cards. Driven by the shared bar.
            SearchPanel engine = s.sample() != null
                    ? new SearchPanel(s.type(), s.sample())
                    : new SearchPanel(s.type());
            if (s.hiddenFields() != null) {
                engine.setHiddenFields(s.hiddenFields());
            }
            if (s.fieldTypes() != null) {
                engine.setFieldTypes(s.fieldTypes());
            }
            engine.setTarget(view.getCardsPanel(), view.getCardsScrollPane());
            engine.setRenderContext(context);
            engine.setCoordinated(true);
            view.addTargetListener(engine);
            engines.add(engine);

            body.add(engine, BorderLayout.NORTH);
        }

        body.add(view.getCardsScrollPane(), BorderLayout.CENTER);
        return body;
    }

    /**
     * Lays the sections out side by side (split for two, grid for more).
     *
     * This is deliberate, not cosmetic: navigation relies on the target
     * card being visible. {@code focusTopLevel} scrolls to + flashes the
     * card and brings the window forward, but it does not reveal a hidden
     * container — so a tabbed layout would jump to a card on an inactive
     * tab without selecting that tab. Switching to tabs would require a
     * "reveal" hook (select the owning tab before scrolling) wired through
     * the shared context.
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
            String title,
            Class<? extends Viewable> type,
            List<Viewable> objects,
            Viewable sample,
            java.util.Set<String> hiddenFields,
            FieldTypeSource fieldTypes) {
    }
}
