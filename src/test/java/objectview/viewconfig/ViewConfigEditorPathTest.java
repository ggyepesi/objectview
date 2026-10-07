package objectview.viewconfig;

import objectview.field.FieldPath;
import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewConfigEditorPathTest {

    private static final class Place extends ViewableAdapter {
        String country = "Hungary";
        String city = "Budapest";
        @Override public String getIdentifier() { return city; }
        @Override public String getDisplayName() { return city; }
    }

    private static final class Team extends ViewableAdapter {
        String league = "NHL";
        Place place = new Place();
        @Override public String getIdentifier() { return league; }
        @Override public String getDisplayName() { return league; }
    }

    @Test void resolvesSubtypeConfigKeyWithoutExposingItAsAFieldSegment() {
        ViewConfigEditor.ResolvedFieldPath path = ViewConfigEditor.resolveFieldPath(
                "State", FieldPath.parse(
                        "@subtype:USState.admissionDate.year"));

        assertEquals("USState", path.owner());
        assertEquals(FieldPath.parse("admissionDate.year"), path.path());
    }

    @Test void ordinaryPathKeepsItsBaseOwner() {
        ViewConfigEditor.ResolvedFieldPath path =
                ViewConfigEditor.resolveFieldPath(
                        "State", FieldPath.parse("capital.name"));

        assertEquals("State", path.owner());
        assertEquals(FieldPath.parse("capital.name"), path.path());
    }

    @Test void anExcludedFieldIsNeitherOfferedNorReturnedButItsChoiceIsRemembered()
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(
                    ViewConfig.of(Team.class), new Team());
            editor.setExcludedFieldPaths(Set.of(FieldPath.of("league")));

            assertFalse(tableContains(findTable(editor), "league"));
            assertFalse(editor.selectedFieldPaths().contains(FieldPath.of("league")));
            assertFalse(editor.getConfig().hasField("league"));

            editor.setExcludedFieldPaths(Set.of());
            assertTrue(tableContains(findTable(editor), "league"));
            assertTrue(editor.selectedFieldPaths().contains(FieldPath.of("league")),
                    "changing quiz context must restore the unchanged field choice");
        });
    }

    @Test void aNestedExclusionKeepsItsSiblingAvailable() throws Exception {
        ViewConfig placeTicks = ViewConfig.leaf();
        placeTicks.addField("country", ViewConfig.leaf());
        placeTicks.addField("city", ViewConfig.leaf());
        ViewConfig team = ViewConfig.of(Team.class);
        team.setAllFields(false);
        team.addField("league", ViewConfig.leaf());
        team.addField("place", placeTicks);
        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(team, new Team());
            editor.setExcludedFieldPaths(Set.of(FieldPath.parse("place.country")));

            List<FieldPath> selected = editor.selectedFieldPaths();
            assertFalse(selected.contains(FieldPath.parse("place.country")));
            assertTrue(selected.contains(FieldPath.parse("place.city")));
            ViewConfig place = editor.getConfig().getFieldConfig("place");
            assertFalse(place.hasField("country"));
            assertTrue(place.hasField("city"));
        });
    }

    private static boolean tableContains(JTable table, String value) {
        for (int row = 0; row < table.getRowCount(); row++) {
            for (int column = 0; column < table.getColumnCount(); column++) {
                if (value.equals(String.valueOf(table.getValueAt(row, column)).trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JTable findTable(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof JTable table) return table;
            if (component instanceof Container child) {
                JTable found = findTable(child);
                if (found != null) return found;
            }
        }
        return null;
    }
}
