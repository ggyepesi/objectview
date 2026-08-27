package objectview.utils.swing;

import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.Window;

/**
 * The one policy for revealing a Swing window as the result of a user action.
 *
 * <p>Calling {@link Window#toFront()} in the same event that disposes another
 * window is unreliable on some window managers (notably macOS): activation is
 * still being transferred away from the disposed window.  Request once now and
 * once after that event has drained.  Reused, hidden and minimized windows all
 * follow the same path.</p>
 */
public final class SwingWindowActivation {
    private SwingWindowActivation() { }

    public static <W extends Window> W showAndFocus(W window) {
        if (window == null) return null;
        reveal(window);
        SwingUtilities.invokeLater(() -> {
            if (window.isDisplayable() && window.isVisible()) reveal(window);
        });
        return window;
    }

    private static void reveal(Window window) {
        if (window instanceof Frame frame
                && (frame.getExtendedState() & Frame.ICONIFIED) != 0) {
            frame.setExtendedState(frame.getExtendedState() & ~Frame.ICONIFIED);
        }
        window.setAutoRequestFocus(true);
        window.setVisible(true);
        window.toFront();
        window.requestFocus();
        window.requestFocusInWindow();
    }
}
