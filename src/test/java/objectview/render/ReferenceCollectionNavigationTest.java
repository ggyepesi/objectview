package objectview.render;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.FieldPath;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** A collection reference has independent navigation and disclosure hit targets. */
class ReferenceCollectionNavigationTest {

    @Test void theCaptionNavigatesWhileTheTriangleControlsTheInlineProjection() {
        Viewable moon = new ViewableAdapter() {
            @Override public String getIdentifier() { return "Q405"; }
            @Override public String getDisplayName() { return "Moon"; }
        };
        RecordingContext context = new RecordingContext();
        ReferenceRow row = new ReferenceRow(
                "", FieldPath.of("moons"), moon, context,
                ViewConfig.of(moon.getClass()), "Moon", true, true, true,
                "Moon", "name");
        row.setSize(240, 40);

        Point caption = firstValuePoint(row);
        row.valueClickedAt(caption);
        assertEquals(1, context.focuses);

        row.valueClickedAt(new Point(1, caption.y));
        assertFalse(context.isExpanded(moon, true),
                "the disclosure hit must collapse instead of navigating");
        assertEquals(1, context.focuses);
    }

    private static Point firstValuePoint(TextRow row) {
        for (int y = 0; y < row.getHeight(); y++) {
            for (int x = 0; x < row.getWidth(); x++) {
                Point point = new Point(x, y);
                if (row.isPointOverValue(point)) return point;
            }
        }
        throw new AssertionError("caption was not laid out");
    }

    private static final class RecordingContext extends RenderContext {
        private int focuses;

        @Override public boolean focusTopLevel(Object object) {
            focuses++;
            return true;
        }
    }
}
