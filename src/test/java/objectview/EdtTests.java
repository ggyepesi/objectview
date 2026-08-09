package objectview;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

/**
 * Runs a test body on the Event Dispatch Thread.
 *
 * <p>Views such as {@code SearchableView} post {@code invokeLater(rebuild)} while
 * they are being built, so a test driving those components from the main thread
 * races the EDT over their internal state — the virtualized list's item index most
 * visibly, where {@code buildIfNeeded} then intermittently returns null. Swing
 * components belong to one thread; tests use the same one the app does.
 */
public final class EdtTests {

    private EdtTests() {}

    public static void onEdt(Runnable body) {
        try {
            SwingUtilities.invokeAndWait(body);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException(cause);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
