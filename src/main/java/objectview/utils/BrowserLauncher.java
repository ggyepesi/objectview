package objectview.utils;

import java.awt.Desktop;
import java.net.URI;

/**
 * Opens URLs in the system browser. Centralizes the {@code Desktop.browse}
 * boilerplate that was previously duplicated across panels, and fails soft
 * on unsupported platforms or malformed URLs.
 */
public final class BrowserLauncher {

    private BrowserLauncher() {}

    public static void open(String url) {
        if (url == null || url.isBlank()) {
            return;
        }

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
