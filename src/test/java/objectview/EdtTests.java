package objectview;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

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

    /**
     * Waits until {@code condition}, evaluated on the EDT, holds.
     *
     * <p>A debounced search and the layout it triggers run as later EDT events, and
     * how much later depends on how warm the JVM is. A fixed sleep that suffices in a
     * full suite fails a cold single-test run (#520), so a test waits for the state it
     * asserts on, and fails with {@code description} only after {@code timeout}.
     */
    public static void awaitOnEdt(BooleanSupplier condition, Duration timeout,
            String description) {
        long deadline = System.nanoTime() + timeout.toNanos();
        AtomicBoolean holds = new AtomicBoolean();
        while (true) {
            onEdt(() -> holds.set(condition.getAsBoolean()));
            if (holds.get()) return;
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Timed out after " + timeout.toMillis()
                        + " ms waiting until " + description);
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(interrupted);
            }
        }
    }
}
