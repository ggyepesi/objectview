package objectview.render;

import objectview.ViewableAdapter;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A value the caller recognises renders as a link.
 *
 * <p>{@code @Link} is a property of the FIELD — declared once, for a column that always
 * holds URLs. This is a property of the VALUE, for the case where what a string means is
 * only knowable outside this module: a bare Wikidata QID sitting where a name should be.
 * objectview never learns what a QID is; it asks, and every view that renders one gains
 * the link at once rather than each remembering to.
 */
class ValueLinkTest {

    @Test void aValueTheCallerResolvesBecomesALinkRow() throws Exception {
        RenderContext context = new RenderContext();
        context.setValueLinker(value -> "Q42".equals(value)
                ? "https://www.wikidata.org/wiki/Q42" : null);

        Card card = cardFor(new Film("Hitchhiker", "Q42"), context);

        assertNotNull(find(card, LinkRow.class),
                      "a recognised value must be reachable, not plain text");
    }

    @Test void anOrdinaryValueIsLeftAlone() throws Exception {
        RenderContext context = new RenderContext();
        context.setValueLinker(value -> "Q42".equals(value)
                ? "https://www.wikidata.org/wiki/Q42" : null);

        Card card = cardFor(new Film("Hitchhiker", "Douglas Adams"), context);

        assertNull(find(card, LinkRow.class),
                   "the linker answers null for almost every value, and that must stay plain");
    }

    /** With no linker — every view that never sets one — nothing changes. */
    @Test void withoutALinkerNothingIsLinked() throws Exception {
        Card card = cardFor(new Film("Hitchhiker", "Q42"), new RenderContext());

        assertNull(find(card, LinkRow.class));
    }

    /** A linker that throws must not take the card down: rendering is not the place to
     *  discover that someone's URL builder disagrees with a value. */
    @Test void aFailingLinkerDegradesToPlainText() {
        RenderContext context = new RenderContext();
        context.setValueLinker(value -> { throw new IllegalStateException("boom"); });

        assertNull(context.valueLink("Q42"));
    }

    @Test void onlyThePaintedLinkTextIsAHotspot() {
        LinkRow row = new LinkRow("source", objectview.field.FieldPath.of("source"),
                "https://example.test", "Wikipedia");
        row.setSize(320, row.getPreferredSize().height);

        assertFalse(row.isPointOverValue(new java.awt.Point(1, row.getHeight() / 2)));
        boolean foundText = false;
        for (int x = 1; x < row.getWidth(); x++) {
            if (row.isPointOverValue(new java.awt.Point(x, row.getHeight() / 2))) {
                foundText = true;
                break;
            }
        }
        assertTrue(foundText, "the underlined value itself remains clickable");
    }

    private static Card cardFor(Film film, RenderContext context) throws Exception {
        Card[] rendered = new Card[1];
        SwingUtilities.invokeAndWait(() -> rendered[0] =
                new Card(film, ViewConfig.all(Film.class), context, false));
        return rendered[0];
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) return (T) root;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static final class Film extends ViewableAdapter {
        private final String title;
        private final String composer;

        Film(String title, String composer) {
            this.title = title;
            this.composer = composer;
        }

        @Override public String getIdentifier() { return title; }
        @Override public String getDisplayName() { return title; }
    }
}
