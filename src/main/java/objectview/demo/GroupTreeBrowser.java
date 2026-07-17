package objectview.demo;

import objectview.Viewable;
import objectview.group.ViewableGroup;
import objectview.search.SearchPanel;
import objectview.viewconfig.FieldTypeSource;
import objectview.viewconfig.ViewConfig;
import objectview.virtual.VirtualizedGroupTreeView;

import javax.swing.*;
import java.awt.*;
import java.util.Set;

/**
 * Unified grouped browser:
 *
 * - VirtualizedGroupTreeView flattens the whole hierarchy (group headers + member
 *   cards) into ONE virtualized outline — expand/collapse and member field-chips
 *   behave like the flat card list, with a single scroll.
 * - SearchPanel owns search / sort / view configuration / highlighting.
 */
public final class GroupTreeBrowser extends JPanel {

    public GroupTreeBrowser(
            ViewableGroup<?> root,
            Class<? extends Viewable> memberClass,
            Viewable sample,
            Set<String> hiddenFields,
            FieldTypeSource fieldTypes) {

        setLayout(new BorderLayout(6, 6));

        VirtualizedGroupTreeView groupedView = new VirtualizedGroupTreeView(
                root,
                ViewConfig.all(memberClass));

        groupedView.scrollPane()
                   .getVerticalScrollBar()
                   .setUnitIncrement(16);

        SearchPanel searchPanel = new SearchPanel(
                memberClass,
                sample);

        searchPanel.setHiddenFields(
                hiddenFields == null ? Set.of() : hiddenFields);
        searchPanel.setFieldTypes(fieldTypes);

        groupedView.setTargetListener(searchPanel);
        searchPanel.setRenderContext(groupedView.renderContext());
        searchPanel.setTarget(groupedView, groupedView.scrollPane());

        add(searchPanel, BorderLayout.NORTH);
        add(groupedView, BorderLayout.CENTER);
    }
}
