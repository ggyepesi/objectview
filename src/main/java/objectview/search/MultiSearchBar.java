package objectview.search;

import objectview.demo.MultiView;
import objectview.render.RenderContext;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * One shared search bar over several per-class sections (a
 * {@link MultiView}). Just two things are shared: the <b>search input</b>
 * (type once; the query fans out to every section) and the <b>config</b>
 * (Search/Sort/View) as single dialogs with one tab per class — the classes as
 * roots, one click to that class's fields.
 *
 * <p>Everything else stays per-section: each engine highlights its own cards and
 * shows its own per-field hit counts + ◀/▶ navigation (the field path on each
 * row identifies the owning panel — it <i>is</i> the panel). The engines work
 * independently; the bar only coordinates the input and the config.
 */
public class MultiSearchBar extends JPanel {

    private final List<SearchPanel> engines = new ArrayList<>();
    private final JTextField field = new JTextField(30);
    private final JCheckBox fieldHighlight = new JCheckBox("Highlight fields");
    private final JButton sortAllTabs = new JButton("Sort all tabs");
    private final JCheckBox showEmptyInAllTabs =
            new JCheckBox("Show empty fields");
    private final JCheckBox captionsOnlyInAllTabs =
            new JCheckBox("Top-level captions only");
    private final javax.swing.Timer debounce;
    private final JTabbedPane hitNavigationTabs = new JTabbedPane();
    private boolean synchronizingAllTabControls;
    private final RenderContext viewContext;
    private final JPanel searchToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));

    public MultiSearchBar(List<SearchPanel> engines) {
        this(engines, null);
    }

    public MultiSearchBar(List<SearchPanel> engines, RenderContext viewContext) {
        super(new BorderLayout(0, 4));
        this.viewContext = viewContext;

        field.setBorder(BorderFactory.createTitledBorder("Search"));
        debounce = new javax.swing.Timer(150, e -> runSearch());
        debounce.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { debounce.restart(); }
            @Override public void removeUpdate(DocumentEvent e) { debounce.restart(); }
            @Override public void changedUpdate(DocumentEvent e) { debounce.restart(); }
        });

        fieldHighlight.addActionListener(e -> {
            for (SearchPanel eng : this.engines) {
                eng.setFieldHighlight(fieldHighlight.isSelected());
            }
            runSearch();
        });

        JButton searchCfg = new JButton("Search Config…");
        JButton sortCfg = new JButton("Sort Config…");
        JButton viewCfg = new JButton("View Config…");
        searchCfg.addActionListener(e -> openConfig("Search Configuration",
                                                    SearchPanel::searchEditor,
                                                    SearchPanel::applySearch));
        sortCfg.addActionListener(e -> openConfig("Sort Configuration",
                                                  SearchPanel::sortEditor, SearchPanel::applySort,
                                                  true));
        viewCfg.addActionListener(e -> openConfig("View Configuration",
                                                  SearchPanel::viewEditor, SearchPanel::applyView,
                                                  false));

        searchToolbar.add(field);
        searchToolbar.add(fieldHighlight);
        searchToolbar.add(searchCfg);
        searchToolbar.add(sortCfg);
        searchToolbar.add(viewCfg);

        sortAllTabs.setToolTipText(
                "Sort every visible tab by that tab's own Sort configuration.");
        sortAllTabs.addActionListener(ignored -> sortAllTabs());
        showEmptyInAllTabs.addActionListener(ignored -> {
            if (synchronizingAllTabControls) return;
            boolean visible = showEmptyInAllTabs.isSelected();
            if (viewContext == null) engines.forEach(engine -> engine.showEmptyFields(visible));
            else viewContext.showEmptyFields(visible);
        });
        captionsOnlyInAllTabs.addActionListener(ignored ->
                applyCaptionsChoice(captionsOnlyInAllTabs.isSelected()));
        JPanel allTabsToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        allTabsToolbar.setBorder(BorderFactory.createTitledBorder("All tabs"));
        allTabsToolbar.add(sortAllTabs);
        allTabsToolbar.add(showEmptyInAllTabs);
        allTabsToolbar.add(captionsOnlyInAllTabs);

        JPanel controls = new JPanel();
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(searchToolbar);
        controls.add(allTabsToolbar);
        add(controls, BorderLayout.NORTH);

        hitNavigationTabs.setPreferredSize(new Dimension(640, 210));
        hitNavigationTabs.setVisible(false);
        add(hitNavigationTabs, BorderLayout.CENTER);
        showEngines(engines);
    }

    /**
     * Replaces the class sections coordinated by this bar without replacing the bar.
     * MultiView uses this when the reader hides or shows a section: the query text and
     * highlight choice stay put, while hidden sections stop producing invisible hits
     * and configuration tabs.
     */
    public void showEngines(List<SearchPanel> visible) {
        List<SearchPanel> next = visible == null ? List.of() : List.copyOf(visible);
        for (SearchPanel engine : engines) {
            if (!next.contains(engine)) engine.runCoordinatedSearch("");
        }
        engines.clear();
        engines.addAll(next);
        searchToolbar.setVisible(!engines.isEmpty());
        synchronizeAllTabControls();
        hitNavigationTabs.removeAll();
        for (SearchPanel engine : engines) {
            engine.setCoordinated(true);
            engine.setNavigationVisibilityOwnedByContainer(true);
            engine.setFieldHighlight(fieldHighlight.isSelected());
            hitNavigationTabs.addTab(engine.sectionTypeName(), engine);
        }
        runSearch();
        revalidate();
        repaint();
    }

    private void synchronizeAllTabControls() {
        synchronizingAllTabControls = true;
        try {
            sortAllTabs.setEnabled(!engines.isEmpty());
            SearchPanel first = engines.isEmpty() ? null : engines.get(0);
            Boolean showEmpty = viewContext == null ? null : viewContext.defaultEmptyFieldsVisible();
            if (showEmpty == null && first != null) {
                showEmpty = first.viewEditor().emptyFieldsVisibleChoice();
            }
            if (showEmpty == null && viewContext != null) showEmpty = true;
            Boolean captionsOnly = first == null ? null
                    : first.viewEditor().rootsFoldedChoice();
            showEmptyInAllTabs.setEnabled(showEmpty != null);
            captionsOnlyInAllTabs.setEnabled(captionsOnly != null);
            if (showEmpty != null) showEmptyInAllTabs.setSelected(showEmpty);
            if (captionsOnly != null) captionsOnlyInAllTabs.setSelected(captionsOnly);
        } finally {
            synchronizingAllTabControls = false;
        }
    }

    private void applyCaptionsChoice(boolean selected) {
        if (synchronizingAllTabControls) return;
        for (SearchPanel engine : engines) {
            engine.viewEditor().rootsFoldedChoice(selected);
            engine.applyView();
        }
    }

    private void sortAllTabs() {
        sortAllTabs.setEnabled(false);
        sortAllTabs.setText("Sorting all tabs…");
        javax.swing.Timer start = new javax.swing.Timer(50, ignored -> {
            try {
                engines.forEach(SearchPanel::applySort);
            } finally {
                sortAllTabs.setText("Sort all tabs");
                sortAllTabs.setEnabled(!engines.isEmpty());
            }
        });
        start.setRepeats(false);
        start.start();
    }

    private void runSearch() {
        String query = field.getText();
        hitNavigationTabs.setVisible(!engines.isEmpty()
                && query != null && !query.isBlank());
        for (SearchPanel e : engines) {
            e.runCoordinatedSearch(query);
        }
        revalidate();
    }

    JTabbedPane hitNavigationTabs() {
        return hitNavigationTabs;
    }

    // Single config dialog with one tab per class (classes as roots): each tab
    // hosts that section's editor. Apply belongs to the visible class tab; changing
    // one class's configuration must not re-run every hidden class section.
    private void openConfig(String title,
                            Function<SearchPanel, JComponent> editor,
                            Consumer<SearchPanel> onApply) {
        openConfig(title, editor, onApply, false);
    }

    private void openConfig(String title,
                            Function<SearchPanel, JComponent> editor,
                            Consumer<SearchPanel> onApply,
                            boolean sortConfiguration) {
        JTabbedPane tabs = new JTabbedPane();
        for (SearchPanel e : engines) {
            tabs.addTab(e.sectionTypeName(), editor.apply(e));
        }

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this), title,
                Dialog.ModalityType.MODELESS);
        JButton apply = new JButton();
        JLabel state = new JLabel(" ");
        Runnable labelApply = () -> {
            int selected = tabs.getSelectedIndex();
            apply.setText(selected < 0 ? "Apply"
                    : "Apply to " + tabs.getTitleAt(selected));
            apply.setEnabled(selected >= 0 && (!sortConfiguration
                    || engines.get(selected).hasUnappliedSortConfiguration()));
            if (sortConfiguration && selected >= 0) {
                state.setText(engines.get(selected).isSortedByCurrentSortConfiguration()
                                ? "Instances are sorted by this configuration."
                                : "This configuration has not been applied.");
            }
        };
        labelApply.run();
        tabs.addChangeListener(e -> labelApply.run());
        if (sortConfiguration) {
            engines.forEach(engine -> engine.setSortConfigurationListener(labelApply));
        }
        apply.addActionListener(a -> {
            int selected = tabs.getSelectedIndex();
            if (selected < 0 || selected >= engines.size()) return;
            if (!sortConfiguration) {
                applySelected(tabs, engines, onApply);
                synchronizeAllTabControls();
                return;
            }
            apply.setEnabled(false);
            apply.setText("Sorting " + tabs.getTitleAt(selected) + "…");
            state.setText("Sorting " + tabs.getTitleAt(selected) + " instances…");
            // Let Swing paint the explicit running state before the synchronous
            // sorter starts; the button remains disabled until it finishes.
            javax.swing.Timer start = new javax.swing.Timer(50, ignored -> {
                try {
                    applySelected(tabs, engines, onApply);
                } finally {
                    labelApply.run();
                }
            });
            start.setRepeats(false);
            start.start();
        });
        JPanel south = new JPanel(new BorderLayout(8, 0));
        south.add(state, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(apply);
        south.add(actions, BorderLayout.EAST);

        dialog.setLayout(new BorderLayout());
        dialog.add(tabs, BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /** Applies a tabbed configuration only to the section named by the visible tab. */
    static <T> void applySelected(
            JTabbedPane tabs, List<T> sections, Consumer<T> apply) {
        if (tabs == null || sections == null || apply == null) return;
        int selected = tabs.getSelectedIndex();
        if (selected < 0 || selected >= sections.size()) return;
        apply.accept(sections.get(selected));
    }

}
