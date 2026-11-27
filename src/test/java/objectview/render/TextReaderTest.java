package objectview.render;

import objectview.EdtTests;
import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.field.FieldPath;
import objectview.field.LinkedTextSequence;
import objectview.utils.BrowserLauncher;
import org.junit.jupiter.api.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
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

    @Test void clickingANamePassesTheActualLoadedReferenceNotAnExternalUrl() {
        EdtTests.onEdt(() -> {
            Viewable character = new ViewableAdapter() {
                @Override public String getDisplayName() { return "Odin"; }
                @Override public String getIdentifier() { return "Q1"; }
            };
            var target = new java.util.concurrent.atomic.AtomicReference<Viewable>();
            var text = LinkedTextSequence.of(
                    LinkedTextSequence.reference("Allfather", character),
                    LinkedTextSequence.text(" drank from the well."));
            var pane = TextReader.pane(text, (link, origin) -> target.set(link.reference()));
            pane.setSize(500, 150);
            var click = mouse(pane, character(pane, 0));
            assertEquals("Odin", pane.getToolTipText(click));
            Arrays.stream(pane.getMouseListeners()).forEach(listener -> listener.mouseClicked(click));
            assertSame(character, target.get());
            assertTrue(text.urls().isEmpty(), "a loaded reference is not a URL");
        });
    }

    @Test void theSameInstanceAndFieldRaiseOneReaderEvenAfterTheCardIsRebuilt() {
        EdtTests.onEdt(() -> {
            FieldPath text = FieldPath.of("text");
            var first = address("Story", "story:1", FieldPath.ROOT, text, "Text");
            var rebuilt = address("Story", "story:1", FieldPath.of("stories"),
                    FieldPath.of("stories", "text"), "");
            assertEquals("Odin at the well — Text — Full text", first.title());
            assertEquals("Odin at the well — text — Full text", rebuilt.title(),
                    "a column without a repeated label still names the field");
            assertEquals(first.key(), rebuilt.key());

            var readers = new TextReader.Readers<Object>();
            Object original = readers.forField(first.key(), Object::new);
            assertSame(original, readers.forField(rebuilt.key(), () -> {
                throw new AssertionError("reopening a field must not create a second reader");
            }));
            readers.closed(first.key(), original);
            Object reopened = readers.forField(first.key(), Object::new);
            assertNotSame(original, reopened, "a closed reader can be opened again");
            readers.closed(first.key(), original);
            assertSame(reopened, readers.forField(first.key(), Object::new),
                    "late cleanup must not remove the reopened reader");
        });
    }

    @Test void equallyNamedInstancesAndDifferentFieldsKeepSeparateReaders() {
        EdtTests.onEdt(() -> {
            FieldPath text = FieldPath.of("text");
            var first = address("Story", "1", FieldPath.ROOT, text, "Text");
            assertNotEquals(first.key(), address("Story", "2", FieldPath.ROOT, text, "Text").key());
            assertNotEquals(first.key(), address("Passage", "1", FieldPath.ROOT, text, "Text").key());
            assertNotEquals(first.key(), address("Story", "1", FieldPath.ROOT,
                    FieldPath.of("translation"), "Text").key());
        });
    }

    private static TextReader.Address address(String type, String id, FieldPath hostPath,
            FieldPath fieldPath, String label) {
        Viewable instance = new ViewableAdapter() {
            @Override public String getIdentifier() { return id; }
            @Override public String typeName() { return type; }
            @Override public String getDisplayName() { return "Odin at the well"; }
        };
        var row = new TextRow(label, fieldPath, "A passage");
        new Host(instance, hostPath).add(row);
        return TextReader.address(row, label, fieldPath);
    }

    private static final class Host extends JPanel implements RenderedInstanceHost {
        private final Viewable instance;
        private final FieldPath path;

        Host(Viewable instance, FieldPath path) {
            this.instance = instance;
            this.path = path;
        }

        @Override public Viewable renderedInstance() { return instance; }
        @Override public FieldPath renderedInstancePath() { return path; }
        @Override public void setHighlightColor(java.awt.Color color) { }
        @Override public boolean isHighlighted() { return false; }
        @Override public void refreshRenderedContent() { }
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
