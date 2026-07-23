package objectview.utils.swing;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import javax.imageio.ImageIO;

import objectview.utils.UrlOpener;

/**
 * Generic lazy cached image.
 *
 * No flag-specific directories, JPEG fallback maps, Constants, or ResourceFinder.
 * Bytes are read only on first getThumbImage()/getFullImage().
 */
public class CachedImage {

    private static final double MAX_IMAGE_WIDTH = 600;
    private static final double MAX_IMAGE_HEIGHT = 400;
    private static final double MAX_THUMB_WIDTH = 150;
    private static final double MAX_THUMB_HEIGHT = 150;

    private final String filename;
    private final String url;
    private boolean svg;
    private final String forDebug;

    private boolean bytesLoaded;
    private byte[] imageBuf;
    private Image full;
    private Image thumb;

    private int fullImageWidth = -1;
    private int fullImageHeight = -1;

    public CachedImage(String filename, String url, boolean svg) {
        this.filename = filename;
        this.url = url;
        this.svg = svg;
        this.forDebug = filename + ", " + url;
    }

    public CachedImage(Image full) {
        this.filename = null;
        this.url = null;
        this.svg = false;
        this.full = toBufferedImage(full);
        this.bytesLoaded = true;
        this.fullImageWidth = full.getWidth(null);
        this.fullImageHeight = full.getHeight(null);
        this.forDebug = "from image";
    }

    public String getForDebug() {
        return forDebug;
    }

    public int getFullImageWidth() {
        return fullImageWidth;
    }

    public int getFullImageHeight() {
        return fullImageHeight;
    }

    public Image getImage() throws Exception {
        return getThumbImage();
    }

    public Image getFullImage() throws Exception {
        ensureBytesLoaded();

        if (full != null) {
            return full;
        }

        return readImageFromImageBuf(MAX_IMAGE_WIDTH, MAX_IMAGE_HEIGHT, false);
    }

    public Image getThumbImage() throws Exception {
        ensureBytesLoaded();

        if (thumb == null) {
            thumb = readImageFromImageBuf(MAX_THUMB_WIDTH, MAX_THUMB_HEIGHT, true);
        }

        return thumb;
    }

    protected void setSvg(boolean svg) {
        this.svg = svg;
    }

    protected void ensureBytesLoaded() throws Exception {
        if (bytesLoaded) {
            return;
        }

        String source = url;

        if ((source == null || source.isBlank())
                && looksLikeUrl(filename)) {
            source = filename;
        }

        if (source == null || source.isBlank()) {
            throw new IllegalStateException(
                    "CachedImage has no URL: " + forDebug);
        }

        readToImageBuf(source);
        bytesLoaded = true;
    }

    private static boolean looksLikeUrl(String s) {
        if (s == null) {
            return false;
        }

        return s.startsWith("file:/")
                || s.startsWith("http://")
                || s.startsWith("https://");
    }

    protected void setImageBuf(byte[] imageBuf) {
        this.imageBuf = imageBuf;
        this.bytesLoaded = true;
        this.full = null;
        this.thumb = null;
    }

    protected boolean hasImageBuf() {
        return imageBuf != null;
    }

    protected String filename() {
        return filename;
    }

    protected String url() {
        return url;
    }

    public boolean isSvg() {
        return svg;
    }

    /** The best serializable source for this image — an explicit URL if set, else the
     *  filename/key it was built from. Lets a persisted media value point back at the
     *  original source instead of carrying rendered pixels. Subclasses override to
     *  resolve a key into a loadable URL. */
    public String sourceUrl() {
        return url != null && !url.isBlank() ? url : filename;
    }

    protected void readToImageBuf(String url) throws Exception {
        try (InputStream in = UrlOpener.open(URI.create(url).toURL())) {
            imageBuf = in.readAllBytes();
        } catch (Exception e) {
            System.err.printf("CachedImage: failed reading %s: %s%n",
                    url,
                    e.getMessage());
            throw e;
        }
    }

    private Image readImageFromImageBuf(
            double maxWidth,
            double maxHeight,
            boolean jpeg) throws Exception {

        synchronized (getClass()) {
            if (imageBuf == null) {
                throw new IllegalStateException("No image bytes loaded: " + forDebug);
            }

            byte[] bytes = svg ? transcodeImageBuf(jpeg) : imageBuf;

            BufferedImage decoded =
                    ImageIO.read(new ByteArrayInputStream(bytes));

            if (decoded == null) {
                throw new IllegalStateException(
                        "ImageIO could not decode image: " + forDebug);
            }

            try {
                return scaleFullImage(decoded, maxWidth, maxHeight);
            } finally {
                decoded.flush();
            }
        }
    }

    private byte[] transcodeImageBuf(boolean jpeg) throws Exception {
        byte[] raster = SvgRasterizer.active().rasterize(imageBuf, jpeg);

        if (raster == null) {
            throw new IllegalStateException(
                    "SVG image but no SvgRasterizer registered (host must call "
                            + "SvgRasterizer.setActive): " + forDebug);
        }

        return raster;
    }

    private Image scaleFullImage(
            Image image,
            double maxWidth,
            double maxHeight) {

        if (fullImageHeight == -1) {
            fullImageHeight = image.getHeight(null);
            fullImageWidth = image.getWidth(null);
        }

        return scaleImage(image, maxWidth, maxHeight);
    }

    public static Image scaleImage(Image image, double maxWidth, double maxHeight) {
        int w = image.getWidth(null);
        int h = image.getHeight(null);

        double m = Math.max(w / maxWidth, h / maxHeight);

        int targetW = m > 1 ? Math.max(1, (int) Math.round(w / m)) : w;
        int targetH = m > 1 ? Math.max(1, (int) Math.round(h / m)) : h;

        BufferedImage scaled =
                new BufferedImage(
                        targetW,
                        targetH,
                        hasAlpha(image)
                                ? BufferedImage.TYPE_INT_ARGB
                                : BufferedImage.TYPE_INT_RGB);

        Graphics2D g = scaled.createGraphics();

        try {
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(
                    RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(image, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }

        return scaled;
    }

    private static boolean hasAlpha(Image image) {
        return image instanceof BufferedImage b
                ? b.getColorModel().hasAlpha()
                : true;
    }

    private static BufferedImage toBufferedImage(Image image) {
        if (image instanceof BufferedImage b) {
            return b;
        }

        BufferedImage buffered =
                new BufferedImage(
                        image.getWidth(null),
                        image.getHeight(null),
                        hasAlpha(image)
                                ? BufferedImage.TYPE_INT_ARGB
                                : BufferedImage.TYPE_INT_RGB);

        Graphics2D g = buffered.createGraphics();

        try {
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }

        return buffered;
    }
}
