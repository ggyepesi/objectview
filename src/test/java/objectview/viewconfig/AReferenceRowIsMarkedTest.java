package objectview.viewconfig;

import objectview.ViewableAdapter;
import objectview.annotations.Inline;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reference renders as a link wherever its target has its own card, so ticking its
 * fields can change nothing on that card. The View editor says so on the row, by the
 * renderer's own representation rule. It is one terminal navigation value, so it has
 * no disclosure promising nested fields.
 */
class AReferenceRowIsMarkedTest {

    static final class Company extends ViewableAdapter {
        String name = "Acme";
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    static final class Address extends ViewableAdapter {
        String city = "Budapest";
        @Override public String getIdentifier() { return city; }
        @Override public String getDisplayName() { return city; }
    }

    static final class Person extends ViewableAdapter {
        String name = "Ada";
        Company employer = new Company();
        @Inline Address address = new Address();
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    @Test void aReferenceIsMarkedAndAnInlineObjectOrAValueIsNot() throws Exception {
        List<String> employer = new ArrayList<>();
        List<String> address = new ArrayList<>();
        List<String> name = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            ViewConfigEditor editor = new ViewConfigEditor(
                    ViewConfig.allWithMinorFields(Person.class), false, new Person(),
                    FieldTableContributor.REORDERABLE);
            editor.setConnectedFieldSelection(true);
            editor.setInheritsRecursion(true);
            JTable table = find(editor);
            for (int r = 0; r < table.getRowCount(); r++) {
                StringBuilder row = new StringBuilder();
                for (int c = 0; c < table.getColumnCount(); c++) {
                    row.append(table.getValueAt(r, c)).append(" | ");
                }
                String text = row.toString();
                if (text.contains("| employer |")) employer.add(text);
                if (text.contains("| address |")) address.add(text);
                if (text.contains("| name |")) name.add(text);
            }
        });

        assertTrue(employer.get(0).contains(ViewConfigEditor.REFERENCE_NOTE), employer.toString());
        assertFalse(employer.get(0).contains("▸") || employer.get(0).contains("▾"),
                "a scalar reference has no nested-field disclosure");
        assertFalse(address.get(0).contains(ViewConfigEditor.REFERENCE_NOTE),
                "an inline object is part of its owner, never a link");
        assertFalse(name.get(0).contains(ViewConfigEditor.REFERENCE_NOTE));
    }

    private static JTable find(Container root) {
        for (Component c : root.getComponents()) {
            if (c instanceof JTable t) return t;
            if (c instanceof Container n) {
                JTable found = find(n);
                if (found != null) return found;
            }
        }
        return null;
    }
}
