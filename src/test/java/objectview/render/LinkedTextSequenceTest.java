package objectview.render;

import objectview.field.FieldPath;
import objectview.field.LinkedTextSequence;
import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.SwingUtilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkedTextSequenceTest {
    @Test void oneTextRowRetainsIndependentDestinationsAndOrdinaryTextBehaviour() {
        LinkedTextSequence value = LinkedTextSequence.of(
                LinkedTextSequence.link("Q1", "https://example.test/Q1"),
                LinkedTextSequence.text(" — "),
                LinkedTextSequence.link("P40", "https://example.test/P40"),
                LinkedTextSequence.text(" → literal"));

        TextRow row = new TextRow("Statement", FieldPath.ROOT, value);

        assertEquals("Q1 — P40 → literal", value.toString());
        assertEquals(List.of("https://example.test/Q1", "https://example.test/P40"),
                row.linkedUrls());
        assertTrue(row.matchesRenderedText(List.of("P40", "literal"), false),
                "linked text remains ordinary searchable text");
    }

    /** Hovering a link names its destination, as a browser does, and plain text in the
     *  same row shows no usage help (the reported yellow "click a linked part" text). */
    @Test void hoveringALinkShowsItsUrlAndPlainTextShowsNothing() throws Exception {
        LinkedTextSequence value = LinkedTextSequence.of(
                LinkedTextSequence.link("Ra", "https://example.test/Ra"),
                LinkedTextSequence.text(" and plain text"));
        TextRow[] row = new TextRow[1];
        SwingUtilities.invokeAndWait(() -> {
            row[0] = new TextRow("Members", FieldPath.ROOT, value);
            row[0].setSize(400, 40);
        });

        Point onLink = pointWhere(row[0], true);
        Point onText = pointWhere(row[0], false);
        assertNotNull(onLink, "the link must be hit-testable");
        assertEquals("https://example.test/Ra", row[0].getToolTipText(hover(row[0], onLink)));
        assertNull(row[0].getToolTipText(hover(row[0], onText)));
    }

    @Test void aWholeLinkRowHoversWithItsUrlOnly() throws Exception {
        LinkRow[] row = new LinkRow[1];
        SwingUtilities.invokeAndWait(() -> {
            row[0] = new LinkRow("Source", FieldPath.ROOT,
                    "Article|https://example.test/article", "");
            row[0].setSize(400, 40);
        });
        Point onValue = null;
        for (int x = 0; x < 400 && onValue == null; x++) {
            for (int y = 0; y < 40 && onValue == null; y++) {
                if (row[0].isPointOverValue(new Point(x, y))) onValue = new Point(x, y);
            }
        }
        assertNotNull(onValue);
        assertEquals("https://example.test/article",
                row[0].getToolTipText(hover(row[0], onValue)));
    }

    /** A card used to fold linked text into its painted text block, which draws plain
     *  strings, so a linked field showed as unclickable text. */
    @Test void aLinkedTextFieldInACardStaysAClickableRow() throws Exception {
        LinkedSource item = new LinkedSource();
        Card[] card = new Card[1];
        SwingUtilities.invokeAndWait(() -> card[0] = new Card(item,
                objectview.viewconfig.ViewConfig.of(LinkedSource.class),
                new RenderContext(), false));

        TextRow row = find(card[0], "source");
        assertNotNull(row, "the linked field must render as its own TextRow");
        assertEquals(List.of("https://example.test/page"), row.linkedUrls());
    }

    private static final class LinkedSource extends objectview.ViewableAdapter {
        @objectview.annotations.DisplayField private final String name = "Item";
        private final String note = "plain";
        private final LinkedTextSequence source = LinkedTextSequence.of(
                LinkedTextSequence.link("https://example.test/page",
                        "https://example.test/page"));
        @Override public String getIdentifier() { return "item"; }
        @Override public String getDisplayName() { return name; }
    }

    private static TextRow find(Component root, String field) {
        if (root instanceof TextRow row && field.equals(
                row.getClientProperty(objectview.field.FieldProperties.FIELD_NAME_PROPERTY))) {
            return row;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                TextRow found = find(child, field);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static Point pointWhere(TextRow row, boolean linked) {
        for (int x = 0; x < row.getWidth(); x++) {
            for (int y = 0; y < row.getHeight(); y++) {
                Point point = new Point(x, y);
                if (!row.isPointOverValue(point)) continue;
                if ((row.linkedUrlAt(point) != null) == linked) return point;
            }
        }
        return null;
    }

    private static MouseEvent hover(Component source, Point point) {
        return new MouseEvent(source, MouseEvent.MOUSE_MOVED, 0, 0,
                point == null ? 0 : point.x, point == null ? 0 : point.y, 0, false);
    }
}
