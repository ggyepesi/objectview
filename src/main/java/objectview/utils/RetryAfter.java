package objectview.utils;

/**
 * One place to interpret an HTTP {@code Retry-After} header, shared by every
 * client that honors 429 throttling (the blocking {@link UrlOpener} and the
 * async {@code WikidataSparqlClient}) so the backoff math can't drift apart.
 */
public final class RetryAfter {

    private RetryAfter() {
    }

    /**
     * Milliseconds to wait per a {@code Retry-After} header value, or
     * {@code defaultMillis} when the header is absent or not a delta-seconds
     * number. A one-second cushion is added to the server's value — the
     * long-standing courtesy margin.
     *
     * <p>Only the delta-seconds form is parsed; the HTTP-date form is treated as
     * absent (falls back to {@code defaultMillis}), matching how Wikimedia sends
     * it.</p>
     */
    public static long millis(String header, long defaultMillis) {
        if (header != null) {
            try {
                return (Long.parseLong(header.trim()) + 1) * 1000L;
            } catch (NumberFormatException ignored) {
                // HTTP-date form or garbage — fall through to the default.
            }
        }
        return defaultMillis;
    }
}
