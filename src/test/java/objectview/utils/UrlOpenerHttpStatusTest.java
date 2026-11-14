package objectview.utils;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UrlOpenerHttpStatusTest {

    @Test void aFinalHttpStatusIsTypedInsteadOfEncodedOnlyInItsMessage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/missing", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        try {
            var failure = assertThrows(UrlOpener.HttpStatusException.class,
                    () -> UrlOpener.open("http://localhost:"
                            + server.getAddress().getPort() + "/missing"));
            assertEquals(404, failure.status());
        } finally {
            server.stop(0);
        }
    }
}
