package objectview.render;

import objectview.EdtTests;
import objectview.field.FieldPath;
import objectview.field.LinkedTextSequence;
import objectview.utils.BrowserLauncher;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JTextPane;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextReaderTest {
    @Test void thePlainTextBlockOffersAReaderForThePointedAtField() {
        EdtTests.onEdt(() -> {
            var field = new TextBlock.Row("motivation", FieldPath.of("motivation"),
                    "for contributions to physics", List.of("for contributions to physics"));
            var block = new TextBlock(List.of(field));
            block.setSize(450, 60);
            assertTrue(hasReader(block.textMenu(new Point(100, 12))));
        });
    }

    @Test void theReaderKeepsTheCompleteTextSelectableAndDoesNotOfferAnEmptyField() {
        EdtTests.onEdt(() -> {
            String passage = "First paragraph\n\nSecond paragraph\nThe ending.";
            var row = new TextRow("narrative", FieldPath.ROOT, passage);
            assertTrue(hasReader(row.textMenu()));
            assertFalse(hasReader(new TextRow("empty", FieldPath.ROOT, " ").textMenu()));
            JTextPane reader = TextReader.pane(passage);
            assertFalse(reader.isEditable());
            assertEquals(0, reader.getCaretPosition());
            reader.selectAll();
            assertEquals(passage, reader.getSelectedText());
        });
    }

    @Test void linkedEvidenceKeepsTheTextAndItsIndependentLinksInTheReader() throws Exception {
        var opened = new ArrayList<String>();
        try (var unused = BrowserLauncher.replaceOpener(opened::add)) {
            EdtTests.onEdt(() -> {
                var passage = LinkedTextSequence.of(
                        LinkedTextSequence.text("The storyteller names "),
                        LinkedTextSequence.link("Odin", "https://example.test/Odin"),
                        LinkedTextSequence.text(" and "),
                        LinkedTextSequence.link("Thor", "https://example.test/Thor"));
                assertTrue(hasReader(new TextRow("Supporting text", FieldPath.ROOT, passage).textMenu()));
                JTextPane reader = TextReader.pane(passage);
                reader.setSize(600, 200);
                assertEquals(passage.text(), reader.getText());
                Point odin = character(reader, passage.text().indexOf("Odin"));
                Point thor = character(reader, passage.text().indexOf("Thor"));
                Point plain = character(reader, 0);
                assertEquals("https://example.test/Odin", reader.getToolTipText(mouse(reader, odin)));
                assertEquals("https://example.test/Thor", reader.getToolTipText(mouse(reader, thor)));
                assertNull(reader.getToolTipText(mouse(reader, plain)));
                assertNull(reader.getToolTipText(mouse(reader, new Point(580, 130))));
                for (Point point : List.of(plain, odin, thor)) {
                    var click = mouse(reader, point);
                    Arrays.stream(reader.getMouseListeners()).forEach(listener -> listener.mouseClicked(click));
                }
                assertEquals(List.of("https://example.test/Odin", "https://example.test/Thor"), opened);
            });
        }
    }

    private static boolean hasReader(JPopupMenu menu) {
        return Arrays.stream(menu.getComponents()).anyMatch(component ->
                component instanceof JMenuItem item && TextReader.ACTION.equals(item.getText()));
    }

    private static Point character(JTextPane pane, int offset) {
        try {
            var bounds = pane.modelToView2D(offset);
            return new Point((int) bounds.getX() + 2, (int) bounds.getCenterY());
        } catch (javax.swing.text.BadLocationException failure) {
            throw new AssertionError(failure);
        }
    }

    private static MouseEvent mouse(JTextPane pane, Point point) {
        return new MouseEvent(pane, MouseEvent.MOUSE_CLICKED, 0, 0, point.x, point.y,
                1, false, MouseEvent.BUTTON1);
    }
}
