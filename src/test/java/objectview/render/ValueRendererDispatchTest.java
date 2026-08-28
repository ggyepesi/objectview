package objectview.render;

import objectview.field.FieldPath;
import objectview.media.ImagePane;
import objectview.media.MediaValueData;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.awt.Component;
import java.awt.Container;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValueRendererDispatchTest {

    @Test void eachStringUsesItsOwnPresentation() {
        assertNotNull(find(render("https://example.com/photo.jpg"), ImagePane.class));
        assertInstanceOf(LinkRow.class, render("https://example.com/page"));
        assertInstanceOf(TextRow.class, render("ordinary text"));
    }

    @Test void mixedCollectionsAndMapsDispatchTheirLeaves() {
        JComponent collection = render(List.of(
                "https://example.com/photo.webp",
                "https://example.com/page",
                "ordinary text"));
        assertNotNull(find(collection, ImagePane.class));
        assertNotNull(find(collection, LinkRow.class));
        assertNotNull(find(collection, TextRow.class));

        JComponent map = render(Map.of(
                "https://example.com/key", "https://example.com/photo.png"));
        assertNotNull(find(map, LinkRow.class), "URL keys are dispatched too");
        assertNotNull(find(map, ImagePane.class), "map values are dispatched");
    }

    @Test void renderingAnImagePaneUsesALayoutOwnedCopy() throws Exception {
        ImagePane stored = new ImagePane(
                "photo", "https://example.com/photo.jpg", null,
                false, false, false);

        ImagePane rendered = find(render(stored), ImagePane.class);

        assertNotNull(rendered);
        assertNotSame(stored, rendered,
                "table/card sizing must not mutate the component stored in the domain");
    }

    /**
     * A card decides what to render BEFORE delegating here: ordinary values fold into
     * one painted text block, which nothing downstream can turn back into a link or an
     * image. Taught only ValueRenderer, the same field showed a picture in TABLE mode
     * and unclickable text in CARD mode — one view-mode toggle apart, same data.
     */
    @Test void aCardShowsWhatTheValueDenotesRatherThanItsText() {
        Card card = new Card(new Linked(), ViewConfig.all(Linked.class),
                new RenderContext(List.of()), false);

        assertNotNull(find(card, ImagePane.class),
                "an image address renders as the image, in a card as in a table");
        assertNotNull(find(card, LinkRow.class),
                "a URL renders as a link, in a card as in a table");
        assertNotNull(find(card, TextBlock.class),
                "ordinary values still share the painted, drag-to-select block");
    }

    @Test void inlineMediaCollectionIsNotMistakenForStructuralViewables() {
        Card card = new Card(new Illustrated(), ViewConfig.all(Illustrated.class),
                new RenderContext(List.of()), false);

        assertNotNull(find(card, ImagePane.class),
                "INLINE media is still value data; the structural inline renderer "
                        + "must not discard its non-Viewable members");
    }

    private static final class Linked extends objectview.ViewableAdapter {
        @objectview.annotations.DisplayField
        private final String name = "one";
        private final String note = "ordinary text";
        private final String photo = "https://example.com/photo.jpg";
        private final String page = "https://example.com/page";

        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static final class Illustrated extends objectview.ViewableAdapter {
        @objectview.annotations.DisplayField private final String name = "one";
        @objectview.annotations.Inline
        private final List<MediaValueData> image = List.of(
                new MediaValueData("portrait", "https://example.com/photo.jpg", false));
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static JComponent render(Object value) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
        return ValueRenderer.createFieldComponent(
                visited, ancestors, null, "value", FieldPath.ROOT,
                value, ViewConfig.leaf(), false);
    }

    private static <T extends Component> T find(Component root, Class<T> type) {
        if (root == null) return null;
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) return found;
            }
        }
        return null;
    }
}
