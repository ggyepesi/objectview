package objectview.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A source image URL may contain a literal space; rendering must reach the request. */
class UrlOpenerAddressTest {

    @Test void theExactMetImageUrlKeepsItsPathAndEscapesItsSpace() throws Exception {
        String source = "https://images.metmuseum.org/CRDImages/eg/web-large/"
                + "23.3.189 (1).jpg";

        assertEquals("https://images.metmuseum.org/CRDImages/eg/web-large/"
                        + "23.3.189%20(1).jpg",
                UrlOpener.address(source).toExternalForm());
    }

    @Test void anExistingEscapeIsNotEscapedAgain() throws Exception {
        String source = "https://example.test/already%20encoded.jpg";

        assertEquals(source, UrlOpener.address(source).toExternalForm());
    }
}
