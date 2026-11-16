package objectview.render;

import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.FieldPath;
import objectview.field.LinkedTextSequence;
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

    @Test void aLinkedCaptionPartOpensItsOwnUrlAndPlainTextKeepsNavigation()
            throws Exception {
        Viewable child = new ViewableAdapter() {
            @Override public String getIdentifier() { return "Q2"; }
            @Override public String getDisplayName() { return "Child"; }
        };
        LinkedTextSequence caption = LinkedTextSequence.of(
                LinkedTextSequence.link("Q1", "https://example.test/Q1"),
                LinkedTextSequence.text(" — "),
                LinkedTextSequence.link("P40", "https://example.test/P40"),
                LinkedTextSequence.text(" → "),
                LinkedTextSequence.link("Q2", "https://example.test/Q2"));
        RecordingContext context = new RecordingContext();
        ReferenceRow row = new ReferenceRow(
                "", FieldPath.of("statements"), child, context,
                ViewConfig.of(child.getClass()), "Child", false, true, true,
                caption, "source", null);
        row.setSize(360, 40);

        java.util.List<String> opened = new java.util.ArrayList<>();
        try (AutoCloseable ignored =
                     objectview.utils.BrowserLauncher.replaceOpener(opened::add)) {
            row.valueClickedAt(pointFor(row, "https://example.test/P40"));
            assertEquals(java.util.List.of("https://example.test/P40"), opened);
            assertEquals(0, context.focuses,
                    "a painted linked run must not fall through to chip navigation");

            row.valueClickedAt(pointWithNoLink(row));
            assertEquals(1, opened.size(), "plain caption text opens nothing");
        }
        assertEquals(1, context.focuses,
                "ordinary caption text keeps the reference's navigation behaviour");
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

    private static Point pointFor(TextRow row, String url) {
        for (int y = 0; y < row.getHeight(); y++) {
            for (int x = 0; x < row.getWidth(); x++) {
                Point point = new Point(x, y);
                if (url.equals(row.linkedUrlAt(point))) return point;
            }
        }
        throw new AssertionError("linked caption part was not laid out: " + url);
    }

    private static Point pointWithNoLink(TextRow row) {
        for (int y = 0; y < row.getHeight(); y++) {
            for (int x = 0; x < row.getWidth(); x++) {
                Point point = new Point(x, y);
                if (row.isPointOverValue(point) && row.linkedUrlAt(point) == null) {
                    return point;
                }
            }
        }
        throw new AssertionError("plain caption part was not laid out");
    }

    private static final class RecordingContext extends RenderContext {
        private int focuses;

        @Override public boolean focusTopLevel(Object object) {
            focuses++;
            return true;
        }
    }
}
