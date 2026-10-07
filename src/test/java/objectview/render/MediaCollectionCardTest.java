package objectview.render;

import objectview.ViewableAdapter;
import objectview.media.ImagePane;
import objectview.media.MediaValue;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import java.awt.Component;
import java.awt.Container;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A gallery used to bypass the collection disclosure and eagerly stack every
 * image above the card's remaining fields. A query could therefore paint only
 * its first few images and leave the actual question below the visible area. */
class MediaCollectionCardTest {

    @Test
    void severalImagesStartAsOneCollapsedCollection() {
        Gallery gallery = new Gallery(List.of(
                image("one"), image("two"), image("three")));

        Card card = new Card(gallery, ViewConfig.of(Gallery.class), false);

        List<CollectionHeader> headers = descendants(card, CollectionHeader.class);
        assertEquals(1, headers.size());
        assertFalse(headers.get(0).isExpanded());
        assertTrue(descendants(card, ImagePane.class).isEmpty(),
                "collapsed galleries must not eagerly build image components");
        assertFalse(descendants(card, TextBlock.class).isEmpty(),
                "fields following the gallery must remain rendered");
    }

    @Test
    void oneImageRemainsImmediatelyVisibleInsideItsCountedCollection() {
        Gallery gallery = new Gallery(List.of(image("only")));

        Card card = new Card(gallery, ViewConfig.of(Gallery.class), false);

        List<CollectionHeader> headers = descendants(card, CollectionHeader.class);
        assertEquals(1, headers.size());
        assertTrue(headers.get(0).isExpanded());
        assertEquals(1, descendants(card, ImagePane.class).size());
    }

    @Test
    void openingTheGalleryBuildsEveryImage() {
        Gallery gallery = new Gallery(List.of(
                image("one"), image("two"), image("three")));
        RenderContext context = new RenderContext();
        context.setCollectionExpanded(gallery.images, true);

        Card card = new Card(
                gallery, ViewConfig.of(Gallery.class), context, false);

        List<CollectionHeader> headers = descendants(card, CollectionHeader.class);
        assertEquals(1, headers.size());
        assertTrue(headers.get(0).isExpanded());
        assertEquals(3, descendants(card, ImagePane.class).size());
    }

    private static TestMedia image(String name) {
        return new TestMedia(name, "https://example.test/" + name + ".jpg");
    }

    private static <T extends Component> List<T> descendants(
            Container root, Class<T> type) {
        java.util.ArrayList<T> found = new java.util.ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) found.add(type.cast(child));
            if (child instanceof Container nested) {
                found.addAll(descendants(nested, type));
            }
        }
        return found;
    }

    private record TestMedia(String mediaLabel, String mediaUrl)
            implements MediaValue {
        @Override public boolean mediaSvg() { return false; }
    }

    @SuppressWarnings("unused")
    private static final class Gallery extends ViewableAdapter {
        private final String name = "Gallery";
        private final List<MediaValue> images;
        private final String question = "Which person is shown?";

        private Gallery(List<MediaValue> images) {
            this.images = images;
        }

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }
}
