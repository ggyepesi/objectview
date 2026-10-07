package objectview.plan;

import objectview.field.ViewableFieldPaths.PathInfo;
import objectview.render.RenderContext;
import objectview.render.RenderingMode;
import objectview.render.TextRow;
import objectview.search.SearchPanel;
import objectview.table.ViewableColumnsView;
import objectview.view.SearchableView;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A table shows what a card shows. Its columns are the leaf paths of the literal
 * config, and each cell renders through the same executor, so ticking only
 * offices.position.Display gives one column of position captions and never reads
 * an office's source. Columns used to come from a second reader of the config's
 * shorthand.
 */
class TableFollowsTheTicksTest {

    @Test void theColumnsAreTheTickedLeavesAndTheCellShowsOnlyThePositionCaptions()
            throws Exception {
        HistoryShape h = new HistoryShape();
        ViewConfig position = ViewConfig.leaf();
        position.addField("@view:display", ViewConfig.leaf());
        ViewConfig offices = ViewConfig.leaf();
        offices.addField("position", position);
        ViewConfig view = ViewConfig.leaf();
        view.addField("@view:display", ViewConfig.leaf());
        view.addField("offices", offices);

        RenderContext context = new RenderContext(List.of(h.wigmund));
        context.setFieldSchemaResolver(HistoryShape::schema);
        context.setTypeSchemaResolver(HistoryShape::schemaOf);
        context.setCollectionExpanded(h.wigmund.dynamicFieldValues().get("offices"), true);
        ViewableColumnsView[] table = new ViewableColumnsView[1];
        JComponent[] row = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            table[0] = SearchableView.builder(List.of(h.wigmund))
                    .mode(RenderingMode.TABLE)
                    .sample(h.wigmund)
                    .renderContext(context)
                    .configState(new SearchPanel.ConfigState(null, null, view))
                    .build().table();
            h.reads.clear();
            row[0] = table[0].row(h.wigmund);
        });

        assertEquals(List.of("name", "offices.position.name"),
                table[0].columns().stream().map(PathInfo::dotted).toList());
        assertTrue(paints(row[0], "King of Mercia"), "the first position caption");
        assertTrue(paints(row[0], "monarch"), "the second position caption");
        assertTrue(h.reads.stream().noneMatch(read -> read.endsWith(".source")),
                "no office's source is read: " + h.reads);
    }

    private static boolean paints(Component root, String text) {
        if (root instanceof TextRow row && row.matchesRenderedText(List.of(text), true)) {
            return true;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (paints(child, text)) return true;
            }
        }
        return false;
    }
}
