package objectview.utils;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

public class UrlOpener {
    private static final String userAgent =
            "MyWikiBot/1.0 (contact@ggyepesi.gmail.com)";

    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 30000;

    private static final long MIN_DELAY_MILLIS = 300;
    private static long lastRequestTime = 0;

    public static InputStream open(String url) throws Exception {
        return open(new URL(url));
    }

    public static InputStream open(URL url) throws Exception {
        String protocol = url.getProtocol();

        if ("http".equalsIgnoreCase(protocol)
                || "https".equalsIgnoreCase(protocol)) {
            return openHttp(url);
        }

        // file:, jar:, resource-like URLs, etc.
        return url.openStream();
    }

    private static InputStream openHttp(URL url) throws Exception {
        while (true) {
            HttpURLConnection connection;

            try {
                waitIfTooSoon();

                connection = (HttpURLConnection) url.openConnection();

                connection.setRequestProperty("User-Agent", userAgent);
                connection.setRequestProperty("Connection", "close");

                connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
                connection.setReadTimeout(READ_TIMEOUT_MILLIS);

                int responseCode = connection.getResponseCode();

                if (responseCode == 429) {
                    sleepRetryAfter(connection, 5000);
                    continue;
                }

                if (responseCode >= 400) {
                    throw new RuntimeException(
                            "HTTP " + responseCode + " for " + url);
                }

                return connection.getInputStream();

            } catch (Exception e) {
                if (isTimeout(e)) {
                    System.out.println(
                            "Timeout for " + url
                                    + ", retrying in 5 seconds...");
                    Thread.sleep(5000);
                    continue;
                }

                throw e;
            }
        }
    }

    private static synchronized void waitIfTooSoon()
            throws InterruptedException {

        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;

        if (elapsed < MIN_DELAY_MILLIS) {
            Thread.sleep(MIN_DELAY_MILLIS - elapsed);
        }

        lastRequestTime = System.currentTimeMillis();
    }

    private static void sleepRetryAfter(
            HttpURLConnection connection,
            long defaultMillis)
            throws Exception {

        long millis = RetryAfter.millis(
                connection.getHeaderField("Retry-After"), defaultMillis);

        System.out.println(
                "HTTP 429. Sleeping "
                        + millis / 1000
                        + " seconds");

        Thread.sleep(millis);
    }

    private static boolean isTimeout(Exception e) {
        String message = e.getMessage();

        return message != null
                && (message.contains("Operation timed out")
                || message.contains("timed out")
                || message.contains("Read timed out")
                || message.contains("connect timed out"));
    }
}