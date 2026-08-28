package objectview.viewconfig;

import objectview.ViewableAdapter;
import objectview.annotations.Minor;
import org.junit.jupiter.api.Test;

import javax.swing.JCheckBox;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One inline Minor fields gate governs reflected and schema-declared minor fields.
 * Individual fields remain ordinary remembered rows beneath that gate.
 */
class MinorBlockRowTest {

    private static class Town extends ViewableAdapter {
        private final String name = "Eger";
        @Minor private final String postcode = "3300";
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static class Plain extends ViewableAdapter {
        private final String name = "Eger";
        private final String postcode = "3300";   // NOT annotated @Minor
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static List<FieldRow> rowsFor(Object sample) {
        return ConfigFieldRowSource.INSTANCE.rows(new FieldRowContext(
                ViewConfig.all(sample.getClass().asSubclass(objectview.Viewable.class)),
                (objectview.Viewable) sample, false, false, Set.of(), null));
    }

    /** A carrier whose fields live on the instance, not on the class. */
    private static class Carrier extends ViewableAdapter
            implements objectview.field.DynamicFields {
        @Override public String getIdentifier() { return "carrier"; }
        @Override public String getDisplayName() { return "carrier"; }
        @Override public java.util.Map<String, Object> dynamicFieldValues() {
            return java.util.Map.of();
        }
    }

    // A class answers the minor question only if it is where the fields are declared.
    // Reflecting over a dynamic carrier finds none and would answer "there are no
    // minor fields" when the truth is "no sample, so it cannot be known" — and the
    // editor keeps its bar precisely on that distinction.
    @Test void aDynamicCarrierWithNoSampleCannotAnswerTheMinorQuestion() {
        assertFalse(ConfigFieldRowSource.INSTANCE.minorFieldsDecidable(
                        new FieldRowContext(ViewConfig.all(Carrier.class), null,
                                false, false, Set.of(), null)),
                "fields on the instance mean the class settles nothing");
        assertTrue(ConfigFieldRowSource.INSTANCE.minorFieldsDecidable(
                        new FieldRowContext(ViewConfig.all(Plain.class), null,
                                false, false, Set.of(), null)),
                "a reflected class declares its own fields, so it answers for itself");
    }

    private static boolean hasBlock(List<FieldRow> rows) {
        return rows.stream().anyMatch(FieldRow::isMinorBlock);
    }

    @Test void aDeclaredMinorFieldRaisesTheBlock() {
        assertTrue(hasBlock(rowsFor(new Town())),
                "@Minor is a declaration, so the block has a group to stand for");
    }

    @Test void classOnlyEditorFindsInheritedMinorFields() throws Exception {
        class Base extends ViewableAdapter {
            @Minor private final List<String> alternateNames = List.of("alias");
            @Override public String getIdentifier() { return "base"; }
            @Override public String getDisplayName() { return "base"; }
        }
        class Person extends Base { }

        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(
                    ViewConfig.allWithMinorFields(Person.class), (objectview.Viewable) null);
            JCheckBox gate = findCheckBox(editor, "Show minor fields");
            JTable table = findTable(editor);
            assertTrue(gate != null && gate.isVisible());
            assertTrue(tableContains(table, "alternateNames"));
            gate.doClick();
            assertFalse(tableContains(table, "alternateNames"));
        });
    }

    @Test void noMinorFieldsMeansNoBlock() {
        assertFalse(hasBlock(rowsFor(new Plain())),
                "an empty group is not a group");
    }

    @Test void theMinorOnlyTableNeverCarriesTheBlock() {
        List<FieldRow> minorOnly = ConfigFieldRowSource.INSTANCE.rows(new FieldRowContext(
                ViewConfig.all(Town.class), new Town(), true, false, Set.of(), null));
        assertFalse(hasBlock(minorOnly),
                "the minor-only table IS the block's contents — it cannot contain itself");
    }

    @Test void hidingTheOnlyMinorFieldRemovesTheBlock() {
        List<FieldRow> hidden = ConfigFieldRowSource.INSTANCE.rows(new FieldRowContext(
                ViewConfig.all(Town.class), new Town(), false, false,
                Set.of("postcode"), null));
        assertFalse(hasBlock(hidden),
                "a block over nothing the reader can see is a dead control");
    }

    /** A field this class does not annotate, that a schema declares minor. */
    private static final class SchemaMinor implements FieldTypeSource {
        @Override public FieldTypeInfo field(String name) {
            return "postcode".equals(name)
                    ? new FieldTypeInfo("String", false, true, null, null,
                            null, null, null, null)
                    : null;
        }
    }

    @Test void aSchemaDeclaredMinorFieldRaisesTheSameInlineGate() {
        List<FieldRow> rows = ConfigFieldRowSource.INSTANCE.rows(new FieldRowContext(
                ViewConfig.all(Plain.class), new Plain(), false, false,
                Set.of(), new SchemaMinor()));

        assertTrue(hasBlock(rows),
                "snapshot and reflected minor fields use the same visible control");
    }

    @Test void theGateAndMinorDetectionShareOneDefinition() {
        FieldRowContext context = new FieldRowContext(
                ViewConfig.all(Plain.class), new Plain(), false, false,
                Set.of(), new SchemaMinor());

        assertTrue(ConfigFieldRowSource.INSTANCE.hasMinorFields(context),
                "the 'All minor fields' checkbox must still appear for a schema minor");
        assertTrue(hasBlock(ConfigFieldRowSource.INSTANCE.rows(context)));
    }

    @Test void minorGateShowsAndHidesRowsWithoutForgettingTheirChecks() throws Exception {
        ViewConfigEditor[] editor = new ViewConfigEditor[1];
        JTable[] table = new JTable[1];
        SwingUtilities.invokeAndWait(() -> {
            editor[0] = new ViewConfigEditor(
                    ViewConfig.allWithMinorFields(Town.class), new Town());
            table[0] = findTable(editor[0]);
            assertTrue(table[0] != null);
            assertTrue(tableContains(table[0], "postcode"),
                    "a checked minor gate reveals ordinary field rows");
            JCheckBox gate = findCheckBox(editor[0], "Show minor fields");
            assertTrue(gate != null && gate.isVisible() && gate.isSelected());

            setUse(table[0], "postcode", false);
            gate.doClick();
            assertFalse(tableContains(table[0], "postcode"));
            ViewConfig closed = editor[0].getConfig();
            assertFalse(closed.getFields().containsKey("postcode"));

            gate.doClick();
            assertTrue(tableContains(table[0], "postcode"));
            assertFalse(useValue(table[0], "postcode"),
                    "reopening restores the remembered per-field state");

            // The gate and the individual choices are configuration state, not
            // accidental memory held only by the current JTable rows.
            ViewConfigEditor rebuilt = new ViewConfigEditor(closed, new Town());
            JTable rebuiltTable = findTable(rebuilt);
            JCheckBox rebuiltGate = findCheckBox(rebuilt, "Show minor fields");
            assertFalse(tableContains(rebuiltTable, "postcode"),
                    "a rebuilt editor keeps the minor gate closed");
            assertTrue(rebuiltGate != null && !rebuiltGate.isSelected());
            rebuiltGate.doClick();
            assertFalse(useValue(rebuiltTable, "postcode"),
                    "a rebuilt editor restores the remembered unchecked field");
        });
    }

    @Test void schemaMinorGateChangesRowsAndEffectiveConfig() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ViewConfig initial = ViewConfig.allWithMinorFields(Plain.class);
            ViewConfigEditor editor = new ViewConfigEditor(initial, new Plain());
            editor.setFieldTypes(new SchemaMinor());
            JTable table = findTable(editor);
            JCheckBox gate = findCheckBox(editor, "Show minor fields");

            assertTrue(gate != null && gate.isVisible() && gate.isSelected());
            assertTrue(tableContains(table, "postcode"));
            assertTrue(editor.getConfig().getFields().containsKey("postcode"));

            gate.doClick();
            assertFalse(tableContains(table, "postcode"));
            assertFalse(editor.getConfig().getFields().containsKey("postcode"));

            gate.doClick();
            assertTrue(tableContains(table, "postcode"));
            assertTrue(editor.getConfig().getFields().containsKey("postcode"));
        });
    }

    private static void setUse(JTable table, String field, boolean value) {
        for (int row = 0; row < table.getRowCount(); row++) {
            if (!rowContains(table, row, field)) continue;
            for (int column = 0; column < table.getColumnCount(); column++) {
                if (table.getColumnClass(column) != Boolean.class) continue;
                table.getModel().setValueAt(value, row, column);
                return;
            }
        }
        throw new AssertionError("No configurable row for " + field);
    }

    private static boolean useValue(JTable table, String field) {
        for (int row = 0; row < table.getRowCount(); row++) {
            if (!rowContains(table, row, field)) continue;
            for (int column = 0; column < table.getColumnCount(); column++) {
                if (table.getColumnClass(column) == Boolean.class) {
                    return Boolean.TRUE.equals(table.getValueAt(row, column));
                }
            }
        }
        throw new AssertionError("No configurable row for " + field);
    }

    private static boolean rowContains(JTable table, int row, String value) {
        for (int column = 0; column < table.getColumnCount(); column++) {
            Object cell = table.getValueAt(row, column);
            if (cell != null && value.equals(cell.toString().trim())) return true;
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

    private static JCheckBox findCheckBox(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JCheckBox box && text.equals(box.getText())) return box;
            if (component instanceof Container child) {
                JCheckBox found = findCheckBox(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean tableContains(JTable table, String value) {
        for (int row = 0; row < table.getRowCount(); row++) {
            for (int column = 0; column < table.getColumnCount(); column++) {
                Object cell = table.getValueAt(row, column);
                if (cell != null && value.equals(cell.toString().trim())) return true;
            }
        }
        return false;
    }
}
