package objectview.search;

import javax.swing.*;
import java.awt.*;

/**
 * Optional helper if you prefer replacing the top-panel construction in
 * SearchPanel with a small reusable class.
 */
public class SearchToolBar extends JPanel {

    public SearchToolBar(
            JButton backButton,
            JTextField searchField,
            JButton searchConfigButton,
            JButton sortConfigButton,
            JButton sortButton,
            JButton restoreOrderButton,
            JButton viewConfigButton,
            JCheckBox highlightFieldsBox) {

        super(new GridLayout(2, 1, 0, 3));

        JPanel firstRow =
                new JPanel(new BorderLayout(4, 0));

        JPanel firstButtons =
                new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        firstButtons.add(backButton);
        firstButtons.add(searchConfigButton);

        firstRow.add(firstButtons, BorderLayout.WEST);
        firstRow.add(searchField, BorderLayout.CENTER);

        JPanel secondRow =
                new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

        secondRow.add(sortConfigButton);
        secondRow.add(sortButton);
        secondRow.add(restoreOrderButton);
        secondRow.add(viewConfigButton);
        secondRow.add(highlightFieldsBox);

        add(firstRow);
        add(secondRow);
    }
}
