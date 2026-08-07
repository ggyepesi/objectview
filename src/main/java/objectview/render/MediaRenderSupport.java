package objectview.render;

import objectview.media.ImagePane;
import objectview.media.MediaValue;

/**
 * The shared rendering boundary between a data-only {@link MediaValue} and Swing.
 * Card and table layouts deliberately remain different components, but they must
 * agree on how a media source is interpreted and how its full image is opened.
 */
public final class MediaRenderSupport {
    private MediaRenderSupport() {}

    /** Creates the same lazy thumbnail pane used by card rendering. */
    public static ImagePane imagePane(MediaValue media) {
        if (!hasSource(media)) return null;
        try {
            return new ImagePane(
                    media.mediaLabel(), media.mediaUrl(), null,
                    false, media.mediaSvg(), false);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean hasSource(MediaValue media) {
        return media != null && media.mediaUrl() != null && !media.mediaUrl().isBlank();
    }
}
