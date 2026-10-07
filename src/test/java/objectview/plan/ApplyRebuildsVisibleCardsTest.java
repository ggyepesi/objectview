package objectview.plan;

import objectview.field.FieldPath;
import objectview.render.RenderContext;
import objectview.render.TextRow;
import objectview.search.SearchPanel;
import objectview.view.SearchableView;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A changed tick reaches the card already on screen once the reader presses Apply.
 * In TransformApp a position tick seemed to have no effect; the card visible before
 * the edit must be rebuilt from the edited config, not kept with the old one.
 */
class ApplyRebuildsVisibleCardsTest {

    @Test void untickingThePositionCaptionAndApplyingRebuildsTheVisibleCard() throws Exception {
        HistoryShape h = new HistoryShape();
        ViewConfig position = ViewConfig.leaf();
        position.addField("name", ViewConfig.leaf());
        ViewConfig offices = ViewConfig.leaf();
        offices.addField("position", position);
        ViewConfig view = ViewConfig.leaf();
        view.addField("name", ViewConfig.leaf());
        view.addField("offices", offices);

        RenderContext context = new RenderContext(List.of(h.wigmund));
        context.setFieldSchemaResolver(HistoryShape::schema);
        context.setTypeSchemaResolver(HistoryShape::schemaOf);
        context.setCollectionExpanded(h.wigmund.dynamicFieldValues().get("offices"), true);
        SearchableView[] built = new SearchableView[1];
        JComponent[] before = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            built[0] = SearchableView.builder(List.of(h.wigmund))
                    .sample(h.wigmund)
                    .renderContext(context)
                    .configState(new SearchPanel.ConfigState(null, null, view))
                    .build();
            before[0] = built[0].cardList().getVirtualList().buildIfNeeded(h.wigmund);
        });
        assertTrue(paints(before[0], "King of Mercia"), "the ticked caption shows first");

        JComponent[] after = new JComponent[1];
        SwingUtilities.invokeAndWait(() -> {
            built[0].search().viewEditor().uncheckFieldPath(
                    FieldPath.of("offices", "position", "name"));
            built[0].search().applyView();
            after[0] = built[0].cardList().getVirtualList().buildIfNeeded(h.wigmund);
        });

        assertFalse(paints(after[0], "King of Mercia"),
                "after Apply the visible card follows the edited config");
        assertTrue(paints(after[0], "Wigmund of Mercia"),
                "the rest of the card is unchanged");
    }

    private static boolean paints(Component root, String text) {
        if (root instanceof TextRow row && row.matchesRenderedText(List.of(text), true)) {
            return true;
        }
        if (root instanceof javax.swing.JLabel label && text.equals(label.getText())) return true;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (paints(child, text)) return true;
            }
        }
        return false;
    }
}
