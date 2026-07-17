package objectview.media;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

import objectview.utils.swing.CachedImage;
import objectview.utils.TitleUtils;
import objectview.Viewable;
import objectview.render.CardListView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImagePane extends JPanel
        implements MouseListener, MouseMotionListener, ImageRef {

    private static final Logger log = LoggerFactory.getLogger(ImagePane.class);

    public interface SelectionListener {
        void selected(ImagePane imagePane);
    }

    public enum ImageKind {
        THUMB,
        FULL
    }

    public enum LoadPolicy {
        IMMEDIATE,
        ON_PAINT,
        ON_DEMAND
    }

    private static final JTextArea draggingCoordinates = new JTextArea();
    private static final JFrame draggingFrame = new JFrame();

    static {
        draggingCoordinates.setEditable(false);
        draggingFrame.getContentPane().add(draggingCoordinates);
        draggingFrame.setUndecorated(true);
        draggingFrame.setOpacity(0.75f);
    }

    private String title = "";
    private Viewable viewable;
    private CachedImage cachedImage;

    // The actual image painted by this ImagePane. It may be thumb/full/crop.
    private Image displayImage;

    private ImageKind imageKind = ImageKind.THUMB;
    private LoadPolicy loadPolicy = LoadPolicy.IMMEDIATE;
    private boolean imageLoading = false;
    private boolean imageLoadFailed = false;

    // Cap on the pane's preferred (layout) size — the painted image scales to
    // fit, so a huge natural image doesn't blow up or collapse the card grid.
    private static final int MAX_DISPLAY_SIZE = 400;

    private int imageWidth = 150;
    private int imageHeight = 150;

    private final Insets insets = new Insets(0, 0, 0, 0);
    private double pw;
    private double ph;
    private double xmultiplier;
    private double ymultiplier;

    private Point startPoint = new Point();
    private Point currentPoint = new Point();

    private boolean dragging = false;
    private boolean dragged = false;
    private boolean dragEnabled = false;

    private SelectionListener selectionListener = null;
    private String key;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
        draggingFrame.setTitle(key);
    }

    public ImagePane(String title, String url, Viewable viewable, boolean addTitle) throws Exception {
        this(title, url, viewable, addTitle, true);
    }

    public ImagePane(String title, String url, Viewable viewable,
                     boolean addTitle, boolean isSvg) throws Exception {
        this(title, url, viewable, addTitle, isSvg,
                ImageKind.THUMB,
                LoadPolicy.IMMEDIATE);
    }

    public ImagePane(String title, String url, Viewable viewable,
                     boolean addTitle, boolean isSvg,
                     ImageKind imageKind,
                     LoadPolicy loadPolicy) throws Exception {
        super();
        init(title, viewable,
                createCachedImage(title, url, isSvg),
                addTitle, true,
                imageKind, loadPolicy);
    }

    public ImagePane(String title, String url, Viewable viewable,
                     boolean addTitle, boolean isSvg,
                     boolean loadThumbnailImmediately) throws Exception {
        this(title, url, viewable, addTitle, isSvg,
                ImageKind.THUMB,
                loadThumbnailImmediately
                        ? LoadPolicy.IMMEDIATE
                        : LoadPolicy.ON_PAINT);
    }

    protected CachedImage createCachedImage(
            String title, String url, boolean isSvg) throws Exception {
        return new CachedImage(title, url, isSvg);
    }

    public ImagePane(String title, Viewable viewable, CachedImage cachedImage,
                     boolean addListeners, boolean addTitle) throws Exception {
        super();
        init(title, viewable, cachedImage, addTitle, addListeners,
                ImageKind.THUMB, LoadPolicy.IMMEDIATE);
    }

    public ImagePane(String title, Viewable viewable, CachedImage cachedImage,
                     boolean addListeners) throws Exception {
        super();
        init(title, viewable, cachedImage, false, addListeners,
                ImageKind.THUMB, LoadPolicy.IMMEDIATE);
        dragEnabled = true;
    }

    public ImagePane(String title, Viewable viewable, CachedImage cachedImage,
                     boolean addListeners, boolean addTitle,
                     ImageKind imageKind, LoadPolicy loadPolicy) throws Exception {
        super();
        init(title, viewable, cachedImage, addTitle, addListeners,
                imageKind, loadPolicy);
    }

    public ImagePane clone(boolean addTitle, boolean addListeners) {
        return new ImagePane(this, addTitle, addListeners);
    }

    private ImagePane(ImagePane other, boolean addTitle, boolean addListeners) {
        try {
            init(other.title, other.viewable, other.cachedImage, addTitle,
                    addListeners, other.imageKind, other.loadPolicy);
            this.dragEnabled = other.dragEnabled;
            this.selectionListener = other.selectionListener;
            setKey(other.key);
        } catch (Exception e) {
            log.warn("image operation failed", e);
        }
    }

    private void init(String title, Viewable viewable, CachedImage cachedImage,
                      boolean addTitle, boolean addListeners,
                      ImageKind imageKind, LoadPolicy loadPolicy)
            throws Exception {
        this.title = title;
        this.viewable = viewable;
        this.cachedImage = cachedImage;
        this.imageKind = imageKind == null ? ImageKind.THUMB : imageKind;
        this.loadPolicy = loadPolicy == null ? LoadPolicy.IMMEDIATE : loadPolicy;

        this.displayImage = null;
        this.imageWidth = 150;
        this.imageHeight = 150;
        this.imageLoading = false;
        this.imageLoadFailed = false;

        // Headless consumers (e.g. the web server) only need the image URL,
        // not a rendered image — they set -Dobjectview.lazyImages=true so
        // construction stays cheap and the bytes are rendered on demand.
        if (this.loadPolicy == LoadPolicy.IMMEDIATE
                && !Boolean.getBoolean("objectview.lazyImages")) {
            // A missing/unreadable image must never abort construction (and
            // thus a whole dataset load) — fall back to the failed state.
            try {
                loadDisplayImageNow();
            } catch (Exception e) {
                imageLoadFailed = true;
            }
        }

        addMouseListener(this);
        if (addListeners) {
            addMouseMotionListener(this);
        }

        applyDisplaySize();
    }

    public CachedImage getCachedImage() {
        return cachedImage;
    }

    /** Renders the (possibly SVG) image to PNG bytes for headless consumers. */
    @Override
    public byte[] pngBytes() throws Exception {
        if (cachedImage == null) {
            return null;
        }

        Image img = cachedImage.getFullImage();
        if (img == null) {
            return null;
        }

        BufferedImage bi;
        if (img instanceof BufferedImage b) {
            bi = b;
        } else {
            int w = Math.max(1, cachedImage.getFullImageWidth());
            int h = Math.max(1, cachedImage.getFullImageHeight());
            bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = bi.createGraphics();
            g.drawImage(img, 0, 0, null);
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", out);
        return out.toByteArray();
    }

    public ImageKind getImageKind() {
        return imageKind;
    }

    public LoadPolicy getLoadPolicy() {
        return loadPolicy;
    }

    public int getImageWidth() {
        return imageWidth;
    }

    public int getImageHeight() {
        return imageHeight;
    }

    public SelectionListener getSelectionListener() {
        return selectionListener;
    }

    public void setSelectionListener(SelectionListener selectionListener) {
        this.selectionListener = selectionListener;
    }

    public Viewable getViewable() {
        return viewable;
    }

    private Image imageForKind() throws Exception {
        return imageKind == ImageKind.FULL
                ? cachedImage.getFullImage()
                : cachedImage.getThumbImage();
    }

    private void loadDisplayImageNow() throws Exception {
        setDisplayImage(imageForKind());
    }

    private void setDisplayImage(Image image) {
        this.displayImage = image;

        if (image == null) {
            this.imageWidth = 150;
            this.imageHeight = 150;
        } else {
            this.imageWidth = image.getWidth(null);
            this.imageHeight = image.getHeight(null);
        }

        applyDisplaySize();
    }

    // The pane's layout footprint. imageWidth/imageHeight stay the NATURAL image
    // size (the paint/crop math needs it); the preferred size is a display box —
    // large images are capped (paintComponent scales to fit, so nothing is lost)
    // and a real minimum is set, because without one GridBagLayout collapses the
    // pane to nothing whenever a card is narrower than the image's natural size
    // (the "tiny icon" cards).
    private void applyDisplaySize() {
        int w = Math.max(1, imageWidth);
        int h = Math.max(1, imageHeight);

        int max = Math.max(w, h);
        if (max > MAX_DISPLAY_SIZE) {
            w = w * MAX_DISPLAY_SIZE / max;
            h = h * MAX_DISPLAY_SIZE / max;
        }

        setPreferredSize(new Dimension(Math.max(150, w), Math.max(150, h)));
        setMinimumSize(new Dimension(150, 150));
    }

    private void loadDisplayImageAsync() {
        if (displayImage != null || imageLoading || imageLoadFailed) {
            return;
        }

        imageLoading = true;

        new SwingWorker<Image, Void>() {
            @Override
            protected Image doInBackground() throws Exception {
                return imageForKind();
            }

            @Override
            protected void done() {
                imageLoading = false;

                try {
                    setDisplayImage(get());
                    revalidate();
                } catch (Exception e) {
                    imageLoadFailed = true;
                    log.warn("image operation failed", e);
                }

                repaint();
            }
        }.execute();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (displayImage == null) {
            if (loadPolicy == LoadPolicy.ON_PAINT) {
                loadDisplayImageAsync();
            }

            paintPlaceholder(g);
            return;
        }

        if (imageWidth <= 0 || imageHeight <= 0) {
            paintPlaceholder(g);
            return;
        }

        int xmargin = insets.left + insets.right;
        int ymargin = insets.top + insets.bottom;

        pw = getWidth() - xmargin;
        ph = getHeight() - ymargin;

        if (pw <= 0 || ph <= 0) {
            return;
        }

        if ((pw / imageWidth) * imageHeight > ph) {
            pw = (ph / imageHeight) * imageWidth;
        } else {
            ph = (pw / imageWidth) * imageHeight;
        }

        xmultiplier = imageWidth / pw;
        ymultiplier = imageHeight / ph;

        g.drawImage(displayImage, insets.left, insets.top, (int) pw, (int) ph, null);

        if (dragging) {
            Rectangle r = getDraggingRectangle();
            if (r.width > 0 && r.height > 0) {
                g.setColor(Color.BLACK);
                g.drawRect(r.x, r.y, r.width, r.height);
            }
        }
    }

    private void paintPlaceholder(Graphics g) {
        g.setColor(Color.GRAY);
        String s =
                imageLoadFailed
                        ? "image failed"
                        : imageLoading
                        ? "loading..."
                        : "image";

        g.drawRect(4, 4,
                Math.max(1, getWidth() - 8),
                Math.max(1, getHeight() - 8));
        g.drawString(s, 12, 24);
    }

    private Rectangle getDraggingRectangle() {
        int sx = startPoint.x;
        int sy = startPoint.y;
        int cx = currentPoint.x;
        int cy = currentPoint.y;

        int w = cx - sx;
        if (w < 0) {
            sx = cx;
            w = -w;
        }

        int h = cy - sy;
        if (h < 0) {
            sy = cy;
            h = -h;
        }

        return new Rectangle(sx, sy, w, h);
    }

    private Point translatePoint(Point point) {
        return new Point(
                Math.clamp(point.x, insets.left, insets.left + (int) pw),
                Math.clamp(point.y, insets.top, insets.top + (int) ph));
    }

    private void showImageView(String title, Image image, boolean addListeners) {
        if (image == null) {
            java.awt.Toolkit.getDefaultToolkit().beep();   // nothing to enlarge
            return;
        }
        CardListView v = new CardListView();
        try {
            ImagePane pane = new ImagePane(
                    title,
                    viewable,
                    new CachedImage(image),
                    addListeners,
                    false,
                    ImageKind.FULL,
                    LoadPolicy.IMMEDIATE);
            pane.dragEnabled = addListeners;
            // A still-loading image reports width/height -1; a zero/negative
            // preferred size collapses the frame to nothing. Fall back to a sane
            // default so the enlarged view is never empty.
            int w = image.getWidth(null);
            int h = image.getHeight(null);
            pane.setPreferredSize(new Dimension(
                    w > 0 ? w : 400,
                    h > 0 ? h : 400));

            v.addImagePane(title, pane);
        } catch (Exception e) {
            log.warn("image operation failed", e);
        }
        v.show(title);
    }

    private static BufferedImage copyCrop(Image source, int sx, int sy, int sw, int sh) {
        int type = hasAlpha(source)
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;

        BufferedImage cropped = new BufferedImage(sw, sh, type);
        Graphics2D g = cropped.createGraphics();
        try {
            g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, sw, sh, sx, sy, sx + sw, sy + sh, null);
        } finally {
            g.dispose();
        }
        return cropped;
    }

    private static boolean hasAlpha(Image image) {
        if (image instanceof BufferedImage) {
            return ((BufferedImage) image).getColorModel().hasAlpha();
        }
        return true;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void mouseClicked(MouseEvent event) {
        if (event.isConsumed() || event.getClickCount() != 2) return;
        event.consume();

        if (selectionListener == null) {
            try {
                // Prefer the depicted entity's name (e.g. the person) over the
                // structural field path ("All/CHEMISTRY, 117.laureatesWith...
                // portrait:null"), to match the dedicated entity window.
                String name = getName();
                String longTitle = (name != null && !name.isBlank())
                        ? name
                        : String.join(".", TitleUtils.getAncestorTitles(this)) + ":" + key;

                // The full image may not be loaded in every context (e.g. a logo
                // shown via the transform view); fall back to the painted image so
                // a double-click never opens an empty frame.
                Image full = cachedImage == null ? null : cachedImage.getFullImage();
                if (full == null) {
                    full = displayImage;
                }
                showImageView(longTitle, full, true);
            } catch (Exception e) {
                log.warn("image operation failed", e);
            }
        } else {
            selectionListener.selected(this);
        }
    }

    @Override
    public void mousePressed(MouseEvent event) {
        if (!dragEnabled || displayImage == null) {
            return;
        }

        startPoint = translatePoint(event.getPoint());
        currentPoint = startPoint;
        dragging = true;
    }

    @Override
    public void mouseDragged(MouseEvent event) {
        if (!dragging) return;

        dragged = true;
        currentPoint = translatePoint(event.getPoint());

        Rectangle r = getDraggingRectangle();
        if (r.width > 0 && r.height > 0) {
            draggingCoordinates.setText(
                    r.x + ", " + r.y + ", " + r.width + "x" + r.height + "; "
                            + imageWidth + "x" + imageHeight);
            draggingFrame.pack();
            draggingFrame.setLocationRelativeTo(this);
            draggingFrame.setVisible(true);
        }

        repaint();
    }

    @Override
    public void mouseReleased(MouseEvent event) {
        if (!dragging || !dragged) {
            dragging = false;
            dragged = false;
            draggingFrame.setVisible(false);
            return;
        }

        Rectangle r = getDraggingRectangle();

        dragging = false;
        dragged = false;
        draggingFrame.setVisible(false);

        if (r.width > 0 && r.height > 0) {
            try {
                Image fullImage = cachedImage.getFullImage();

                int fullWidth = fullImage.getWidth(null);
                int fullHeight = fullImage.getHeight(null);

                int tx = clamp((int) Math.round((r.x - insets.left) * xmultiplier), 0, fullWidth - 1);
                int ty = clamp((int) Math.round((r.y - insets.top) * ymultiplier), 0, fullHeight - 1);
                int tw = clamp((int) Math.round(r.width * xmultiplier), 1, fullWidth - tx);
                int th = clamp((int) Math.round(r.height * ymultiplier), 1, fullHeight - ty);

                BufferedImage cropped = copyCrop(fullImage, tx, ty, tw, th);

                repaint();

                String t = title + "[" + r.x + ", " + r.y + ", " + r.width + "x" + r.height + "]";
                showImageView(t, cropped, false);
            } catch (Exception e) {
                log.warn("image operation failed", e);
            }
        } else {
            repaint();
        }
    }

    @Override
    public void mouseEntered(MouseEvent event) {}

    @Override
    public void mouseExited(MouseEvent event) {}

    @Override
    public void mouseMoved(MouseEvent event) {}

    public String getTitle() {
        return title;
    }

    @Override
    public String getName() {
        return viewable == null ? title : viewable.getName();
    }

    @Override
    public String toString() {
        return getName();
    }
}
