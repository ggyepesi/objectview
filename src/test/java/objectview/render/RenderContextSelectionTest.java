package objectview.render;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderContextSelectionTest {
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
        assertEquals(2, context.selectedObjects().size());

        context.select(first, true);
        assertFalse(context.isSelected(first));
        assertTrue(context.isSelected(second));

        context.select(first, false);
        assertTrue(context.isSelected(first));
        assertFalse(context.isSelected(second));
        assertEquals(1, context.selectedObjects().size());
        assertEquals(4, reports.size());
    }
}
