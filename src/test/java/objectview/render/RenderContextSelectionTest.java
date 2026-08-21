package objectview.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderContextSelectionTest {

    /** A view that does not offer the gesture must not have it silently applied: a
     *  Ctrl-click there is an ordinary click, and the tooltip says so because it asks. */
    @Test
    void withoutMultipleSelectionAModifierClickIsAnOrdinaryClick() {
        RenderContext context = new RenderContext();
        context.setSelectionEnabled(true);
        Object first = new Object();
        Object second = new Object();

        context.select(first, true);
        context.select(second, true);

        assertFalse(context.multipleSelectionEnabled());
        assertFalse(context.isSelected(first));
        assertEquals(List.of(second), context.selectedObjects());
    }

    @Test
    void modifierSelectionTogglesSeveralObjectsAndPlainClickReplacesThem() {
        RenderContext context = new RenderContext();
        context.setSelectionEnabled(true);
        context.setMultipleSelectionEnabled(true);
        List<List<Object>> reports = new ArrayList<>();
        context.addSelectionSetListener(reports::add);
        Object first = new Object();
        Object second = new Object();

        context.select(first, false);
        context.select(second, true);
        assertTrue(context.isSelected(first));
        assertTrue(context.isSelected(second));
        // In the order the reader built it, and reported that way — a caller acting on
        // "the selected rows" must not get them in an order that varies per run.
        assertEquals(List.of(first, second), context.selectedObjects());
        assertEquals(List.of(first, second), reports.get(1));
        assertSame(second, context.selected(),
                "the single-selection consumer acts on the one just clicked");

        context.select(first, true);
        assertFalse(context.isSelected(first));
        assertTrue(context.isSelected(second));
        assertEquals(List.of(second), context.selectedObjects());

        context.select(first, false);
        assertTrue(context.isSelected(first));
        assertFalse(context.isSelected(second));
        assertEquals(1, context.selectedObjects().size());
        assertEquals(4, reports.size());
    }
}
