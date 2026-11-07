package objectview.render;

import objectview.field.FieldPath;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Peer fields must not look nested merely because their representation has a
 * disclosure control. The control owns a shared gutter; field names own one column. */
class PeerFieldAlignmentTest {

    @Test void rawLinkReferenceAndCollectionFieldNamesStartInOneColumn() {
        FieldPath path = FieldPath.of("field");
        List<JComponent> peers = List.of(
                new TextRow("Field", path, ""),
                new LinkRow("Field", path, "Wikipedia|https://en.wikipedia.org/", ""),
                new TextBlock(List.of(new TextBlock.Row(
                        "Field", path, "", List.of("")))),
                new CollectionHeader("Field", path, 0, false,
                        List.of(), List.of(), false, null));

        int expected = firstPaintedX(peers.get(0));
        for (JComponent peer : peers) {
            assertEquals(expected, firstPaintedX(peer), peer.getClass().getSimpleName());
        }
    }

    private static int firstPaintedX(JComponent component) {
        component.setForeground(Color.BLACK);
        component.setSize(360, component.getPreferredSize().height);
        BufferedImage image = new BufferedImage(
                component.getWidth(), component.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            component.paint(graphics);
        } finally {
            graphics.dispose();
        }
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if ((image.getRGB(x, y) >>> 24) != 0) return x;
            }
        }
        throw new AssertionError(component.getClass().getSimpleName() + " painted nothing");
    }
}
