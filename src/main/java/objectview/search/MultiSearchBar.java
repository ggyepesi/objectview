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

    private final List<SearchPanel> engines;
    private final JTextField field = new JTextField(30);
    private final JCheckBox fieldHighlight = new JCheckBox("Highlight fields");
    private final javax.swing.Timer debounce;

    public MultiSearchBar(List<SearchPanel> engines) {
        super(new FlowLayout(FlowLayout.LEFT, 6, 4));
        this.engines = new ArrayList<>(engines);
        for (SearchPanel e : this.engines) {
            e.setCoordinated(true);
        }

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
                                                    SearchPanel::searchEditor, eng -> runSearch()));
        sortCfg.addActionListener(e -> openConfig("Sort Configuration",
                                                  SearchPanel::sortEditor, SearchPanel::applySort));
        viewCfg.addActionListener(e -> openConfig("View Configuration",
                                                  SearchPanel::viewEditor, SearchPanel::applyView));

        add(field);
        add(fieldHighlight);
        add(searchCfg);
        add(sortCfg);
        add(viewCfg);
    }

    private void runSearch() {
        String query = field.getText();
        for (SearchPanel e : engines) {
            e.runCoordinatedSearch(query);
        }
    }

    // Single config dialog with one tab per class (classes as roots): each tab
    // hosts that section's editor; Apply re-applies + re-runs for every section.
    private void openConfig(String title,
                            Function<SearchPanel, JComponent> editor,
                            Consumer<SearchPanel> onApply) {
        JTabbedPane tabs = new JTabbedPane();
        for (SearchPanel e : engines) {
            tabs.addTab(e.sectionTypeName(), editor.apply(e));
        }

        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this), title,
                Dialog.ModalityType.MODELESS);
        JButton apply = new JButton("Apply");
        apply.addActionListener(a -> {
            for (SearchPanel e : engines) {
                onApply.accept(e);
            }
        });
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(apply);

        dialog.setLayout(new BorderLayout());
        dialog.add(tabs, BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }
}
