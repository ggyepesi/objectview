package objectview.viewconfig;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NestedFieldSelectionTest {

    private static final class Child extends ViewableAdapter {
        String kept = "yes";
        String omitted = "no";
        @Override public String getIdentifier() { return kept; }
        @Override public String getDisplayName() { return kept; }
    }

    private static final class Parent extends ViewableAdapter {
        Child child = new Child();
        @Override public String getIdentifier() { return "parent"; }
        @Override public String getDisplayName() { return "parent"; }
    }

    private static final class Collections extends ViewableAdapter {
        List<String> tags = List.of("one", "two");
        List<Child> children = List.of(new Child());
        @Override public String getIdentifier() { return "collections"; }
        @Override public String getDisplayName() { return "collections"; }
    }

    @Test void uncheckingParentSuppressesButDoesNotForgetNestedChoices() throws Exception {
        ViewConfig child = ViewConfig.of(Child.class);
        child.setAllFields(false);
        child.addField("kept", ViewConfig.leaf());
        ViewConfig config = ViewConfig.of(Parent.class);
        config.setAllFields(false);
        config.addField("child", child);

        ViewConfigEditor[] editor = new ViewConfigEditor[1];
        SwingUtilities.invokeAndWait(() -> {
            editor[0] = new ViewConfigEditor(config, new Parent());
            editor[0].setConnectedFieldSelection(true);
            editor[0].setSelectedPath(objectview.field.FieldPath.parse("child.kept"));
            JTable table = findTable(editor[0]);
            assertNotNull(table);
            int row = rowContaining(table, "child");
            int use = booleanColumn(table);
            table.setValueAt(false, row, use);
            assertFalse(editor[0].getConfig().hasField("child"),
                    "an unchecked reference suppresses its entire subtree");
            assertTrue(editor[0].getConfig().getRememberedFieldConfig("child")
                            .hasField("kept"),
                    "the suppressed choices are persisted outside the effective fields");

            int kept = rowContaining(table, "kept");
            assertTrue(Boolean.TRUE.equals(table.getValueAt(kept, use)),
                    "a suppressed descendant keeps its check");
            assertFalse(table.isCellEditable(kept, use),
                    "a suppressed descendant is visible but disabled");
            assertFalse(table.prepareRenderer(
                            table.getCellRenderer(kept, use), kept, use).isEnabled(),
                    "the disabled state is painted, not merely rejected by the model");
            assertTrue(rowContains(table, row, "1 nested field remembered"));

            table.setValueAt(true, row, use);
            ViewConfig restored = editor[0].getConfig().getFieldConfig("child");
            assertNotNull(restored);
            assertTrue(restored.hasField("kept"));
            assertFalse(restored.hasField("omitted"));
            assertTrue(table.isCellEditable(kept, use));
            assertTrue(table.prepareRenderer(
                            table.getCellRenderer(kept, use), kept, use).isEnabled());
        });
    }

    @Test void selectingNestedValueActivatesItsCompleteOwnerPath() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ViewConfig config = ViewConfig.of(Parent.class);
            config.setAllFields(false);
            ViewConfigEditor editor = new ViewConfigEditor(config, new Parent());
            editor.setConnectedFieldSelection(true);
            editor.setSelectedPath(objectview.field.FieldPath.parse("child.kept"));
            JTable table = findTable(editor);
            int use = booleanColumn(table);
            int child = rowContaining(table, "child");
            int kept = rowContaining(table, "kept");

            assertFalse(table.isCellEditable(child, use),
                    "an empty object branch cannot be selected as a value");
            assertTrue(table.isCellEditable(kept, use),
                    "its terminal values remain available for first configuration");
            table.setValueAt(true, kept, use);

            assertTrue(Boolean.TRUE.equals(table.getValueAt(child, use)),
                    "the owner is activated by its selected descendant");
            assertTrue(editor.getConfig().getFieldConfig("child").hasField("kept"));
        });
    }

    @Test void removingLastNestedValuePrunesTheEmptyOwnerWithoutRememberingIt()
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ViewConfig config = ViewConfig.of(Parent.class);
            config.setAllFields(false);
            ViewConfigEditor editor = new ViewConfigEditor(config, new Parent());
            editor.setConnectedFieldSelection(true);
            editor.setSelectedPath(objectview.field.FieldPath.parse("child.kept"));
            JTable table = findTable(editor);
            int use = booleanColumn(table);
            int kept = rowContaining(table, "kept");
            table.setValueAt(true, kept, use);
            table.setValueAt(false, kept, use);

            ViewConfig result = editor.getConfig();
            assertFalse(result.hasField("child"));
            assertNull(result.getRememberedFieldConfig("child"),
                    "removing choices is different from suppressing their owner");
        });
    }

    @Test void clearNestedSelectionIsTheExplicitForgetOperation() throws Exception {
        ViewConfig child = ViewConfig.of(Child.class);
        child.setAllFields(false);
        child.addField("kept", ViewConfig.leaf());
        ViewConfig config = ViewConfig.of(Parent.class);
        config.setAllFields(false);
        config.addField("child", child);

        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(config, new Parent());
            editor.setConnectedFieldSelection(true);
            assertTrue(editor.clearNestedSelection(
                    objectview.field.FieldPath.of("child")));
            assertFalse(editor.getConfig().hasField("child"));
            assertNull(editor.getConfig().getRememberedFieldConfig("child"));
        });
    }

    @Test void rawCollectionsAreTerminalButObjectCollectionsNeedANestedChoice()
            throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ViewConfig config = ViewConfig.of(Collections.class);
            config.setAllFields(false);
            ViewConfigEditor editor = new ViewConfigEditor(config, new Collections());
            editor.setConnectedFieldSelection(true);
            JTable table = findTable(editor);
            int use = booleanColumn(table);

            assertTrue(table.isCellEditable(rowContaining(table, "tags"), use));
            assertFalse(table.isCellEditable(rowContaining(table, "children"), use));
        });
    }

    @Test void suppressedChoicesSurviveAnEditorRoundTrip() throws Exception {
        ViewConfig child = ViewConfig.of(Child.class);
        child.setAllFields(false);
        child.addField("kept", ViewConfig.leaf());
        ViewConfig config = ViewConfig.of(Parent.class);
        config.setAllFields(false);
        config.rememberField("child", child);

        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(config, new Parent());
            editor.setConnectedFieldSelection(true);
            editor.setSelectedPath(objectview.field.FieldPath.parse("child.kept"));
            JTable table = findTable(editor);
            int use = booleanColumn(table);
            int childRow = rowContaining(table, "child");
            int kept = rowContaining(table, "kept");

            assertFalse(Boolean.TRUE.equals(table.getValueAt(childRow, use)));
            assertTrue(Boolean.TRUE.equals(table.getValueAt(kept, use)));
            assertFalse(table.isCellEditable(kept, use));
            table.setValueAt(true, childRow, use);
            assertTrue(editor.getConfig().getFieldConfig("child").hasField("kept"));
        });
    }

    @Test void suppressedChoicesSurviveJsonPersistence() {
        ViewConfig child = ViewConfig.of(Child.class);
        child.setAllFields(false);
        child.addField("kept", ViewConfig.leaf());
        ViewConfig config = ViewConfig.of(Parent.class);
        config.setAllFields(false);
        config.rememberField("child", child);

        java.io.File file = new java.io.File(System.getProperty("java.io.tmpdir"),
                "suppressed-fields-" + System.nanoTime() + ".json");
        try {
            ViewConfigJsonIO.save(file, config);
            ViewConfig loaded = ViewConfigJsonIO.fromJson(ViewConfigJsonIO.loadJson(file));
            assertNotNull(loaded.getRememberedFieldConfig("child"));
            assertTrue(loaded.getRememberedFieldConfig("child").hasField("kept"));
            assertFalse(loaded.hasField("child"));
        } finally {
            file.delete();
        }
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

    private static int rowContaining(JTable table, String value) {
        for (int row = 0; row < table.getRowCount(); row++) {
            for (int col = 0; col < table.getColumnCount(); col++) {
                if (value.equals(String.valueOf(table.getValueAt(row, col)).trim())) return row;
            }
        }
        throw new AssertionError("row not found: " + value);
    }

    private static boolean rowContains(JTable table, int row, String value) {
        for (int col = 0; col < table.getColumnCount(); col++) {
            Object cell = table.getValueAt(row, col);
            if (cell != null && String.valueOf(cell).contains(value)) return true;
        }
        return false;
    }

    private static int booleanColumn(JTable table) {
        for (int col = 0; col < table.getColumnCount(); col++) {
            if (table.getColumnClass(col) == Boolean.class) return col;
        }
        throw new AssertionError("boolean column not found");
    }
}
