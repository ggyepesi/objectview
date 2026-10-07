package objectview.render;

import objectview.ViewableAdapter;
import objectview.field.FieldPath;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BooleanFieldCardTest {

    @Test void bothBooleanValuesAreRenderedLiterally() throws Exception {
        ViewConfig config = ViewConfig.leaf();
        config.addField("enabled", ViewConfig.leaf());
        config.addField("archived", ViewConfig.leaf());
        Card[] card = new Card[1];
        SwingUtilities.invokeAndWait(() -> card[0] = new Card(new Flags(), config, false));

        TextBlock block = find(card[0]);
        assertNotNull(block);
        assertTrue(block.hasMatchingRow(FieldPath.parse("enabled"), List.of("true"), true));
        assertTrue(block.hasMatchingRow(FieldPath.parse("archived"), List.of("false"), true));
    }

    private static TextBlock find(Component component) {
        if (component instanceof TextBlock block) return block;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                TextBlock found = find(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static final class Flags extends ViewableAdapter {
        @SuppressWarnings("unused") private final boolean enabled = true;
        @SuppressWarnings("unused") private final boolean archived = false;
        @Override public String getIdentifier() { return "flags"; }
        @Override public String getDisplayName() { return "flags"; }
    }
}
