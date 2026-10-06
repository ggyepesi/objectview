package objectview.render;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.field.FieldProperties;
import objectview.field.ViewableContractFieldSet;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A reference with no selected body is a value, not a door.
 *
 * <p>An extracted director, composer or location arrives as a QID and a label and
 * nothing else. DISPLAY is a real selected field whose one painted occurrence is
 * the caption; it must not create a second row or an empty expander.
 *
 * <p>The distinction has to be made from the SAME questions the card asks when it
 * builds fields, or the promise and the content drift apart again.
 */
class ContentlessReferenceTest {

    @Test void aSelectedDisplayIsPaintedOnceAsTheCaption() throws Exception {
        Film film = new Film("12 Monkeys", new Person("Terry Gilliam"));

        Card card = cardFor(film);

        assertNull(find(card, ReferenceRow.class),
                "a display-only value must not promise an expandable body");
        assertEquals(1, renderedCount(card, "Terry Gilliam"));
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
    @Test void aBlankSiblingDoesNotCreateAnExpanderBehindTheCaption() throws Exception {
        Person director = new Person("Terry Gilliam");
        director.nationality = "   ";
        Film film = new Film("12 Monkeys", director);

        Card card = cardFor(film);

        assertNull(find(card, ReferenceRow.class));
        assertEquals(1, renderedCount(card, "Terry Gilliam"));
    }

    @Test void aSelectedObjectFieldWithNoChildrenShowsOnlyItsFieldName() throws Exception {
        Film film = new Film("12 Monkeys", new Person("Terry Gilliam"));
        ViewConfig config = ViewConfig.of(Film.class);
        config.setAllFields(false);
        config.addField("director", ViewConfig.leaf());

        Card card = cardFor(film, config);

        assertNotNull(findByFieldName(card, "director"));
        assertFalse(renders(card, "Terry Gilliam"),
                "selecting the object field must not select DISPLAY implicitly");
    }

    @Test void nestedFieldsRenderWithoutLeakingAnUnselectedDisplay() throws Exception {
        Person director = new Person("Terry Gilliam");
        director.nationality = "British";
        Film film = new Film("12 Monkeys", director);
        ViewConfig person = ViewConfig.of(Person.class);
        person.setAllFields(false);
        person.addField("nationality", ViewConfig.leaf());
        ViewConfig config = ViewConfig.of(Film.class);
        config.setAllFields(false);
        config.addField("director", person);

        Card card = cardFor(film, config);

        assertNotNull(find(card, TextBlock.class),
                "the explicitly selected nationality is rendered");
        assertFalse(renders(card, "Terry Gilliam"),
                "DISPLAY is hidden like any other unselected field");
    }

    @Test void selectedNestedDisplayBecomesTheObjectFieldCaption() throws Exception {
        Film film = new Film("12 Monkeys", new Person("Terry Gilliam"));
        ViewConfig person = ViewConfig.of(Person.class);
        person.setAllFields(false);
        person.addField("personName", ViewConfig.leaf());
        ViewConfig config = ViewConfig.of(Film.class);
        config.setAllFields(false);
        config.addField("director", person);

        Card card = cardFor(film, config);

        assertTrue(renders(card, "Terry Gilliam"));
    }

    @Test void aBlankStructuralReferenceRendersItsConfiguredChildInsteadOfABlankChip()
            throws Exception {
        Position position = new Position("President");
        Holding projection = new Holding(position);
        FilmWithHolding film = new FilmWithHolding("History", projection);

        ViewConfig positionConfig = ViewConfig.of(Position.class);
        positionConfig.setAllFields(false);
        positionConfig.addField(ViewableContractFieldSet.DISPLAY_KEY, ViewConfig.leaf());
        ViewConfig holdingConfig = ViewConfig.of(Holding.class);
        holdingConfig.setAllFields(false);
        holdingConfig.addField("position", positionConfig);
        ViewConfig config = ViewConfig.of(FilmWithHolding.class);
        config.setAllFields(false);
        config.addField("holding", holdingConfig);

        RenderContext context = new RenderContext();
        Card[] rendered = new Card[1];
        SwingUtilities.invokeAndWait(() -> rendered[0] =
                new Card(film, config, context, false));

        assertTrue(renders(rendered[0], "President"));
        assertNull(findReference(rendered[0], ""),
                "the blank intermediate wrapper must not become an empty chip");
    }

    private static Card cardFor(Film film) throws Exception {
        return cardFor(film, ViewConfig.all(Film.class));
    }

    private static Card cardFor(Film film, ViewConfig config) throws Exception {
        RenderContext context = new RenderContext();
        Card[] rendered = new Card[1];
        SwingUtilities.invokeAndWait(() -> rendered[0] =
                new Card(film, config, context, false));
        return rendered[0];
    }

    private static Component findByFieldName(Component root, String fieldName) {
        if (root instanceof javax.swing.JComponent component
                && fieldName.equals(component.getClientProperty(
                FieldProperties.FIELD_NAME_PROPERTY))) {
            return root;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                Component found = findByFieldName(child, fieldName);
                if (found != null) return found;
            }
        }
        return null;
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

    private static boolean renders(Component root, String text) {
        if (root instanceof TextRow row
                && row.matchesRenderedText(java.util.List.of(text), true)) {
            return true;
        }
        if (root instanceof javax.swing.JComponent component
                && text.equals(component.getClientProperty(
                FieldProperties.FIELD_VALUE_PROPERTY))) {
            return true;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (renders(child, text)) return true;
            }
        }
        return false;
    }

    private static int renderedCount(Component root, String text) {
        int count = 0;
        if (root instanceof TextRow row
                && row.matchesRenderedText(java.util.List.of(text), true)) count++;
        if (root instanceof javax.swing.JLabel label && text.equals(label.getText())) count++;
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                count += renderedCount(child, text);
            }
        }
        return count;
    }

    private static ReferenceRow findReference(Component root, String text) {
        if (root instanceof ReferenceRow row
                && text.equals(row.getClientProperty(
                FieldProperties.FIELD_VALUE_PROPERTY))) {
            return row;
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                ReferenceRow found = findReference(child, text);
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

    private static final class FilmWithHolding extends ViewableAdapter {
        private final String title;
        private final Holding holding;

        private FilmWithHolding(String title, Holding holding) {
            this.title = title;
            this.holding = holding;
        }

        @Override public String getIdentifier() { return title; }
        @Override public String getDisplayName() { return title; }
    }

    private static final class Holding extends ViewableAdapter {
        private final Position position;

        private Holding(Position position) { this.position = position; }

        @Override public String getIdentifier() { return "holding"; }
        @Override public String getDisplayName() { return ""; }
    }

    private static final class Position extends ViewableAdapter {
        private final String name;

        private Position(String name) { this.name = name; }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }
}
