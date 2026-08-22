package objectview.render;

import objectview.ViewableAdapter;
import objectview.field.FieldSchema;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A detail window opened out of a list is a new place to look at ONE object, not a second
 * copy of the list's state.
 *
 * <p>It must keep the rendering services — above all the schema resolver, because a dynamic
 * object's domain fields live in a map that reflection cannot see, so a detail window built
 * on a bare context renders an object with no fields at all. It must NOT keep the list's
 * selection: the detached card is not one of the rows the reader picked, and treating it as
 * one makes a batch action operate on something the reader never selected.
 */
class DetachedDetailContextTest {

    @Test void theDetailWindowKeepsTheServicesThatDecideWhatAFieldIs() {
        RenderContext list = new RenderContext();
        FieldSchema schema = java.util.List::of;
        list.setFieldSchemaResolver(viewable -> schema);
        list.setValueLinker(value -> "Q42".equals(value) ? "https://example.org/Q42" : null);
        list.putClassConfig(Film.class, ViewConfig.all(Film.class));

        RenderContext detail = list.detachedDetailContext();

        assertEquals(schema, detail.fieldSchema(new Film("Hitchhiker")),
                "without the resolver a dynamic object renders with no domain fields");
        assertEquals("https://example.org/Q42", detail.valueLink("Q42"));
        assertNotNull(detail.configFor(Film.class),
                "the detail window shows the object the way the list was configured to");
    }

    @Test void theDetailWindowDoesNotInheritWhatTheReaderSelectedInTheList() {
        RenderContext list = new RenderContext();
        list.setSelectionEnabled(true);
        Film picked = new Film("Blood Diamond");
        list.select(picked);

        RenderContext detail = list.detachedDetailContext();

        assertNull(detail.selected(), "a detached card is not one of the list's picked rows");
        assertTrue(detail.selectedObjects().isEmpty());
        assertEquals(picked, list.selected(), "and detaching must not disturb the list");
    }

    public static final class Film extends ViewableAdapter {
        private final String title;

        Film(String title) {
            this.title = title;
        }

        @Override public String getIdentifier() { return title; }
        @Override public String getDisplayName() { return title; }
    }
}
