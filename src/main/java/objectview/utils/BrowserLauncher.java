package objectview.utils;

import java.awt.Desktop;
import java.net.URI;
import java.util.function.Consumer;

/**
 * Opens URLs in the system browser. Centralizes the {@code Desktop.browse}
 * boilerplate that was previously duplicated across panels, and fails soft
 * on unsupported platforms or malformed URLs.
 */
public final class BrowserLauncher {

    private static volatile Consumer<String> opener = BrowserLauncher::browse;

    private BrowserLauncher() {}

    public static void open(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        opener.accept(url.trim());
    }

    /** Sends every opened URL to {@code replacement} until the returned handle is
     *  closed. A test that clicks a link asserts the destination this way instead of
     *  launching the developer's real browser. */
    public static AutoCloseable replaceOpener(Consumer<String> replacement) {
        Consumer<String> previous = opener;
        opener = java.util.Objects.requireNonNull(replacement, "replacement");
        return () -> opener = previous;
    }

    private static void browse(String url) {
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url.trim()));
            }
        } catch (Exception e) {
            System.err.println(
                    "Could not open URL: " + url + " (" + e.getMessage() + ")");
        }
    }
}
