package objectview.viewconfig;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
            JTable table = findTable(editor[0]);
            assertNotNull(table);
            int row = rowContaining(table, "child");
            int use = booleanColumn(table);
            table.setValueAt(false, row, use);
            assertFalse(editor[0].getConfig().hasField("child"),
                    "an unchecked reference suppresses its entire subtree");

            table.setValueAt(true, row, use);
            ViewConfig restored = editor[0].getConfig().getFieldConfig("child");
            assertNotNull(restored);
            assertTrue(restored.hasField("kept"));
            assertFalse(restored.hasField("omitted"));
        });
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

    private static int booleanColumn(JTable table) {
        for (int col = 0; col < table.getColumnCount(); col++) {
            if (table.getColumnClass(col) == Boolean.class) return col;
        }
        throw new AssertionError("boolean column not found");
    }
}
