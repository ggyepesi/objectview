package objectview.view;

import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A presentation-only choice of which sibling sections are visible.
 *
 * <p>The options are data held by the control, not Swing selection state. The
 * checkboxes edit a temporary choice. Only OK replaces the projected sections;
 * cancelling or dismissing the popup changes nothing.
 * The host receives stable keys and decides how those sections are projected.</p>
 */
public final class SectionVisibilityControl extends JPanel {

    public record Option(String key, String label) {
        public Option {
            if (key == null || key.isBlank()) throw new IllegalArgumentException("blank key");
            label = label == null || label.isBlank() ? key : label;
        }
    }

    private final String noun;
    private final List<Option> options;
    private final Set<String> selected = new LinkedHashSet<>();
    private final Consumer<Set<String>> listener;
    private final JButton chooser = new JButton();

    public SectionVisibilityControl(
            String noun, Collection<Option> options, Collection<String> selected,
            Consumer<Set<String>> listener) {
        super(new FlowLayout(FlowLayout.LEFT, 0, 0));
        this.noun = noun == null || noun.isBlank() ? "Sections" : noun;
        this.options = options == null ? List.of() : List.copyOf(options);
        this.listener = listener == null ? ignored -> { } : listener;
        selectWithoutNotification(selected);
        chooser.addActionListener(ignored -> popup().show(
                chooser, 0, chooser.getHeight()));
        add(chooser);
    }

    public Set<String> selectedKeys() {
        return Set.copyOf(selected);
    }

    /** Reconciles an externally retained choice without reporting a user action. */
    public void selectWithoutNotification(Collection<String> keys) {
        selected.clear();
        if (keys != null) {
            Set<String> available = options.stream().map(Option::key)
                    .collect(java.util.stream.Collectors.toSet());
            keys.stream().filter(available::contains).forEach(selected::add);
        }
        chooser.setText(noun + " shown: " + selected.size() + " of "
                + options.size() + "…");
    }

    JPopupMenu popup() {
        JPopupMenu menu = new JPopupMenu();
        menu.setLayout(new BorderLayout(6, 6));
        Set<String> pending = new LinkedHashSet<>(selected);
        JPanel choices = new JPanel();
        choices.setLayout(new BoxLayout(choices, BoxLayout.Y_AXIS));
        if (options.isEmpty()) choices.add(new JLabel("No sections."));
        for (Option option : options) {
            JCheckBox item = new JCheckBox(
                    option.label(), selected.contains(option.key()));
            item.addActionListener(ignored -> {
                if (item.isSelected()) pending.add(option.key());
                else pending.remove(option.key());
            });
            choices.add(item);
        }

        JScrollPane scroll = new JScrollPane(choices);
        scroll.setBorder(null);
        scroll.setPreferredSize(new Dimension(360,
                Math.min(420, Math.max(90, options.size() * 26 + 8))));
        menu.add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        JButton all = new JButton("Show all");
        all.addActionListener(ignored -> {
            pending.clear();
            options.forEach(option -> pending.add(option.key()));
            for (java.awt.Component child : choices.getComponents()) {
                if (child instanceof JCheckBox box) box.setSelected(true);
            }
        });
        actions.add(all);
        JButton ok = new JButton("OK");
        ok.addActionListener(ignored -> {
            selected.clear();
            selected.addAll(pending);
            changed();
            menu.setVisible(false);
        });
        actions.add(ok);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(ignored -> menu.setVisible(false));
        actions.add(cancel);
        menu.add(actions, BorderLayout.SOUTH);
        return menu;
    }

    private void changed() {
        chooser.setText(noun + " shown: " + selected.size() + " of "
                + options.size() + "…");
        listener.accept(Set.copyOf(selected));
    }
}
