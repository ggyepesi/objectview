package objectview.media;

import objectview.utils.swing.CachedImage;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ImagePaneSizingTest {

    @Test void portraitComponentKeepsTheImagesAspectRatio() throws Exception {
        ImagePane pane = pane(80, 160);

        assertEquals(new Dimension(80, 160), pane.getPreferredSize());
        assertEquals(new Dimension(75, 150), pane.getMinimumSize());
    }

    @Test void displayCapScalesBothDimensionsTogether() throws Exception {
        ImagePane pane = pane(200, 400);

        pane.setDisplayCap(100);

        assertEquals(new Dimension(50, 100), pane.getPreferredSize());
        assertEquals(new Dimension(50, 100), pane.getMinimumSize());
    }

    private static ImagePane pane(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        CachedImage cached = new CachedImage(image) {
            @Override public Image getThumbImage() {
                return image;
            }
        };
        return new ImagePane("test", null, cached, false, false,
                ImagePane.ImageKind.THUMB, ImagePane.LoadPolicy.IMMEDIATE);
    }
}
