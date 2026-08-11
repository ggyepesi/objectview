package objectview.render;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A reference with nothing behind it is a value, not a door.
 *
 * <p>An extracted director, composer or location arrives as a QID and a label and
 * nothing else — its only field is the display name, which the reference row already
 * shows. It was still rendered with an expand triangle, so every one of them promised
 * content and opened an empty box.
 *
 * <p>The distinction has to be made from the SAME questions the card asks when it
 * builds fields, or the promise and the content drift apart again.
 */
class ContentlessReferenceTest {

    @Test void aReferenceWhoseTargetHasNoFieldsRendersWithoutAnExpander() throws Exception {
        Film film = new Film("12 Monkeys", new Person("Terry Gilliam"));

        Card card = cardFor(film);

        assertNull(find(card, ReferenceRow.class),
                   "the director has only a name — an expander here opens nothing");
        assertNotNull(find(card, TextRow.class), "but the name is still shown");
    }

    @Test void aReferenceWhoseTargetHasFieldsKeepsItsExpander() throws Exception {
        Person director = new Person("Terry Gilliam");
        director.nationality = "British";
        Film film = new Film("12 Monkeys", director);

        Card card = cardFor(film);

        assertNotNull(find(card, ReferenceRow.class),
                      "there IS something behind this one, so it must stay expandable");
    }

    /** A declared field that happens to be empty renders nothing, so it cannot be what
     *  justifies an expander — otherwise the box opens empty again. */
    @Test void aDeclaredButEmptyFieldDoesNotJustifyAnExpander() throws Exception {
        Person director = new Person("Terry Gilliam");
        director.nationality = "   ";
        Film film = new Film("12 Monkeys", director);

        Card card = cardFor(film);

        assertNull(find(card, ReferenceRow.class),
                   "a blank value renders nothing; the expander would open an empty box");
    }

    private static Card cardFor(Film film) throws Exception {
        RenderContext context = new RenderContext();
        Card[] rendered = new Card[1];
        SwingUtilities.invokeAndWait(() -> rendered[0] =
                new Card(film, ViewConfig.all(Film.class), context, false));
        return rendered[0];
    }

    @SuppressWarnings("unchecked")
    private static <T> T find(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return (T) root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Film extends ViewableAdapter {
        private final String title;
        private final Person director;

        private Film(String title, Person director) {
            this.title = title;
            this.director = director;
        }

        @Override public String getIdentifier() { return title; }
        @Override public String getDisplayName() { return title; }
    }

    /** The shape an extracted reference has: a label bound to the DISPLAY role, which
     *  the reference row itself shows, and nothing else. */
    private static final class Person extends ViewableAdapter {
        @DisplayField
        private final String personName;
        private String nationality;

        private Person(String personName) {
            this.personName = personName;
        }

        @Override public String getIdentifier() { return personName; }
        @Override public String getDisplayName() { return personName; }
        @Override public String getReferenceLabel() { return personName; }
    }
}
