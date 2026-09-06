package objectview.viewconfig;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A row's checkbox answers for the field at its own path.
 *
 * <p>Every row asked the ROOT config whether it showed a field of that row's leaf NAME.
 * Under an all-fields config that is invisible — the root says yes to everything — and
 * under an explicit one it is wrong in both directions: a nested row reads as unchecked
 * because the root lists no field of that name, and a nested row reads as checked when
 * the root happens to list one spelled the same.
 *
 * <p>It was found on the Oscars search config, as a reference checked with its display
 * name unchecked: {@code nominee.@view:display} asked the root about
 * {@code @view:display}, and the root lists category, forWork, ceremony and nominee.
 */
class ANestedRowAnswersForItsOwnFieldTest {

    static final class Nested extends ViewableAdapter {
        String city = "Budapest";
        @Override public String getIdentifier() { return city; }
        @Override public String getDisplayName() { return city; }
    }

    static final class Parent extends ViewableAdapter {
        String city = "root-city";
        Nested nominee = new Nested();
        @Override public String getIdentifier() { return city; }
        @Override public String getDisplayName() { return city; }
    }

    /** A reference with no config of its own shows all its fields — its display too. */
    @Test void aReferenceWithNoConfigOfItsOwnShowsItsFields() throws Exception {
        ViewConfig config = ViewConfig.of(Parent.class);
        config.setAllFields(false);
        config.addField("nominee", ViewConfig.leaf());

        boolean[] shown = new boolean[2];
        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(config, new Parent());
            editor.setSelectedPath(objectview.field.FieldPath.parse("nominee.city"));
            JTable table = findTable(editor);
            shown[0] = useOf(table, "Display label", 1);   // the nested one comes first
            shown[1] = useOf(table, "city", 2);            // root's own city is row 0
        });

        assertTrue(shown[0], "the reference's display name is one of its fields");
        assertTrue(shown[1], "and so is every other field it has");
    }

    /** And a nested row does not answer for a root field that is spelled the same. */
    @Test void aNestedRowDoesNotInheritARootFieldOfTheSameName() throws Exception {
        ViewConfig config = ViewConfig.of(Parent.class);
        config.setAllFields(false);
        config.addField("city", ViewConfig.leaf());   // the ROOT's city

        boolean[] nestedCity = new boolean[1];
        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(config, new Parent());
            editor.setSelectedPath(objectview.field.FieldPath.parse("nominee.city"));
            JTable table = findTable(editor);
            nestedCity[0] = useOf(table, "city", 2);
        });

        assertFalse(nestedCity[0],
                "the nested city is a different field, under a reference the config "
                        + "does not include");
    }

    private static boolean useOf(JTable table, String label, int occurrence) {
        int seen = 0;
        for (int r = 0; r < table.getRowCount(); r++) {
            for (int c = 0; c < table.getColumnCount(); c++) {
                Object value = table.getValueAt(r, c);
                if (value != null && label.equals(String.valueOf(value).trim())) {
                    if (++seen == occurrence) {
                        for (int b = 0; b < table.getColumnCount(); b++) {
                            if (table.getValueAt(r, b) instanceof Boolean checked) {
                                return checked;
                            }
                        }
                    }
                    break;
                }
            }
        }
        throw new AssertionError("no row " + occurrence + " labelled " + label);
    }

    private static JTable findTable(Container root) {
        for (Component child : root.getComponents()) {
            if (child instanceof JTable table) return table;
            if (child instanceof Container nested) {
                JTable found = findTable(nested);
                if (found != null) return found;
            }
        }
        return null;
    }
}
