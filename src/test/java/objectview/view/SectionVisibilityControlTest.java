package objectview.view;

import objectview.EdtTests;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SectionVisibilityControlTest {

    @Test void checkboxEditsStayPendingUntilOkAndCancelDiscardsThem() {
        EdtTests.onEdt(() -> {
            AtomicReference<Set<String>> reported = new AtomicReference<>();
            SectionVisibilityControl control = new SectionVisibilityControl(
                    "Classes",
                    List.of(new SectionVisibilityControl.Option("Star", "Star (1)"),
                            new SectionVisibilityControl.Option("Moon", "Moon (2)")),
                    Set.of("Star"), reported::set);

            JPopupMenu cancelled = control.popup();
            find(cancelled, JCheckBox.class, "Moon (2)").doClick();
            assertEquals(Set.of("Star"), control.selectedKeys(),
                    "checking a class must not redraw the host immediately");
            assertNull(reported.get());
            find(cancelled, JButton.class, "Cancel").doClick();
            assertEquals(Set.of("Star"), control.selectedKeys());

            JPopupMenu applied = control.popup();
            find(applied, JCheckBox.class, "Moon (2)").doClick();
            find(applied, JButton.class, "OK").doClick();
            assertEquals(Set.of("Star", "Moon"), control.selectedKeys());
            assertEquals(Set.of("Star", "Moon"), reported.get());
        });
    }

    private static <T extends Component> T find(
            Container root, Class<T> type, String text) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && text.equals(textOf(child))) return type.cast(child);
            if (child instanceof Container nested) {
                T found = findOrNull(nested, type, text);
                if (found != null) return found;
            }
        }
        throw new AssertionError("No " + type.getSimpleName() + " named " + text);
    }

    private static <T extends Component> T findOrNull(
            Container root, Class<T> type, String text) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && text.equals(textOf(child))) return type.cast(child);
            if (child instanceof Container nested) {
                T found = findOrNull(nested, type, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String textOf(Component component) {
        if (component instanceof JButton button) return button.getText();
        if (component instanceof JCheckBox box) return box.getText();
        return null;
    }
}
