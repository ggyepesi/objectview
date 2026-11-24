package objectview.search;

import objectview.demo.MultiView;

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
    private final JCheckBox applyViewAndSortToAllTabs =
            new JCheckBox("Apply Sort/View to all tabs");
    private final javax.swing.Timer debounce;
    private final JTabbedPane hitNavigationTabs = new JTabbedPane();

    public MultiSearchBar(List<SearchPanel> engines) {
        super(new BorderLayout(0, 4));

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
                                                  true, false));
        viewCfg.addActionListener(e -> openConfig("View Configuration",
                                                  SearchPanel::viewEditor, SearchPanel::applyView,
                                                  false, true));

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        toolbar.add(field);
        toolbar.add(fieldHighlight);
        toolbar.add(searchCfg);
        toolbar.add(sortCfg);
        toolbar.add(viewCfg);
        applyViewAndSortToAllTabs.setToolTipText(
                "Sort every visible class by its own Sort configuration; View also "
                        + "shares Show empty fields and Top-level cards: captions only.");
        toolbar.add(applyViewAndSortToAllTabs);
        add(toolbar, BorderLayout.NORTH);

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
        openConfig(title, editor, onApply, false, false);
    }

    private void openConfig(String title,
                            Function<SearchPanel, JComponent> editor,
                            Consumer<SearchPanel> onApply,
                            boolean sortConfiguration,
                            boolean viewConfiguration) {
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
            boolean allTabs = (sortConfiguration || viewConfiguration)
                    && applyViewAndSortToAllTabs.isSelected();
            apply.setText(selected < 0 ? "Apply"
                    : allTabs ? "Apply to all tabs"
                    : "Apply to " + tabs.getTitleAt(selected));
            apply.setEnabled(selected >= 0 && (!sortConfiguration
                    || (allTabs
                            ? engines.stream().anyMatch(
                                    SearchPanel::hasUnappliedSortConfiguration)
                            : engines.get(selected)
                                    .hasUnappliedSortConfiguration())));
            if (sortConfiguration && selected >= 0) {
                state.setText(allTabs
                        ? "Each tab keeps its own sort configuration."
                        : engines.get(selected).isSortedByCurrentSortConfiguration()
                                ? "Instances are sorted by this configuration."
                                : "This configuration has not been applied.");
            }
        };
        labelApply.run();
        tabs.addChangeListener(e -> labelApply.run());
        java.awt.event.ItemListener allTabsListener = event -> labelApply.run();
        if (sortConfiguration || viewConfiguration) {
            applyViewAndSortToAllTabs.addItemListener(allTabsListener);
            dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                @Override public void windowClosed(java.awt.event.WindowEvent event) {
                    applyViewAndSortToAllTabs.removeItemListener(allTabsListener);
                }
            });
        }
        if (sortConfiguration) {
            engines.forEach(engine -> engine.setSortConfigurationListener(labelApply));
        }
        apply.addActionListener(a -> {
            int selected = tabs.getSelectedIndex();
            if (selected < 0 || selected >= engines.size()) return;
            boolean applyAll = (sortConfiguration || viewConfiguration)
                    && applyViewAndSortToAllTabs.isSelected();
            if (applyAll && viewConfiguration) {
                shareViewOptions(engines.get(selected), engines);
            }
            if (!sortConfiguration) {
                applySelectedOrAll(tabs, engines, onApply, applyAll);
                return;
            }
            apply.setEnabled(false);
            apply.setText(applyAll ? "Sorting all tabs…"
                    : "Sorting " + tabs.getTitleAt(selected) + "…");
            state.setText(applyAll ? "Sorting instances in every tab…"
                    : "Sorting " + tabs.getTitleAt(selected) + " instances…");
            // Let Swing paint the explicit running state before the synchronous
            // sorter starts; the button remains disabled until it finishes.
            javax.swing.Timer start = new javax.swing.Timer(50, ignored -> {
                try {
                    applySelectedOrAll(tabs, engines, onApply, applyAll);
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
        applySelectedOrAll(tabs, sections, apply, false);
    }

    /** Applies either the visible section or every section's own configuration. */
    static <T> void applySelectedOrAll(
            JTabbedPane tabs, List<T> sections, Consumer<T> apply, boolean all) {
        if (tabs == null || sections == null || apply == null) return;
        if (all) {
            sections.forEach(apply);
            return;
        }
        int selected = tabs.getSelectedIndex();
        if (selected < 0 || selected >= sections.size()) return;
        apply.accept(sections.get(selected));
    }

    /** Shares only type-independent presentation choices; field paths stay per tab. */
    static void shareViewOptions(SearchPanel source, List<SearchPanel> targets) {
        if (source == null || targets == null) return;
        Boolean rootsFolded = source.viewEditor().rootsFoldedChoice();
        Boolean showEmpty = source.viewEditor().emptyFieldsVisibleChoice();
        targets.forEach(target -> {
            if (rootsFolded != null) {
                target.viewEditor().rootsFoldedChoice(rootsFolded);
            }
            if (showEmpty != null) {
                target.viewEditor().emptyFieldsVisibleChoice(showEmpty);
            }
        });
    }
}
