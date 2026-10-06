package objectview.render;

import objectview.utils.swing.GridBagUtils;
import objectview.media.ImagePane;
import objectview.media.MediaValue;
import objectview.field.FieldProperties;
import objectview.field.FieldPath;
import objectview.Viewable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Paints leaf values: an image, a URL as the link or picture it denotes, plain text,
 * and a collection or map made only of plain text. Objects and object collections are
 * never painted here; they reach a card as the executor's decisions.
 */
public final class ValueRenderer {
    private ValueRenderer() {
    }

    /** One leaf value, or null when it paints nothing (a blank text, a missing image). */
    public static JComponent leaf(String fieldName, FieldPath fieldPath, Object value) {
        if (value == null) {
            return null;
        }

        // A backing media value (e.g. a Wikidata image) becomes a real ImagePane
        // here, at render time — so the data pool never has to carry Swing.
        if (value instanceof MediaValue media) {
            value = MediaRenderSupport.imagePane(media);
            if (value == null) {
                return null;
            }
        }

        if (value instanceof ImagePane imagePane) {
            return imageComponent(fieldName, fieldPath, renderCopy(imagePane));
        }

        JComponent url = automaticUrlComponent(fieldName, fieldPath, value);
        if (url != null) {
            return url;
        }

        return leafComponent(fieldName, fieldPath, value);
    }

    /** A collection of plain text values as one bulleted row; null when any member is
     *  not plain text (an object, an image, a URL, a nested collection). */
    static JComponent plainCollection(String fieldName, FieldPath fieldPath, Collection<?> collection) {
        collection = RenderSnapshot.collection(collection);
        return isSimpleCollection(collection)
                ? simpleCollectionComponent(fieldName, fieldPath, collection) : null;
    }

    /** A map of plain text values as one row; null when it is not plain text. */
    static JComponent plainMap(String fieldName, FieldPath fieldPath, Map<?, ?> map) {
        map = RenderSnapshot.map(map);
        return isSimpleMap(map) ? simpleMapComponent(fieldName, fieldPath, map) : null;
    }

    private static JComponent imageComponent(String fieldName, FieldPath fieldPath, ImagePane imagePane) {
        JPanel panel = basePanel(fieldName, fieldPath, imagePane);

        panel.add(imagePane, GridBagUtils.weighted(0, 0, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 2, 2, 2)));

        return panel;
    }

    private static JComponent simpleCollectionComponent(String fieldName, FieldPath fieldPath, Collection<?> collection) {
        List<String> lines = collection.stream().filter(Objects::nonNull).map(String::valueOf).filter(s -> !s.isBlank()).map(s -> "• " + s).collect(Collectors.toList());

        if (lines.isEmpty()) {
            return null;
        }

        return new TextRow(fieldName, fieldPath, lines);
    }

    private static JComponent simpleMapComponent(String fieldName, FieldPath fieldPath, Map<?, ?> map) {
        String text = map.entrySet().stream().filter(e -> e.getKey() != null || e.getValue() != null).map(e -> e.getKey() + " -> " + e.getValue()).filter(s -> !s.isBlank()).collect(Collectors.joining(", "));

        if (text.isBlank()) {
            return null;
        }

        return new TextRow(fieldName, fieldPath, text);
    }

    private static JComponent leafComponent(String fieldName, FieldPath fieldPath, Object value) {
        if (value == null) {
            return null;
        }

        String text = String.valueOf(value);

        if (text.isBlank()) {
            return null;
        }

        return new TextRow(fieldName, fieldPath, value);
    }

    private static boolean isSimpleCollection(Collection<?> collection) {
        for (Object item : collection) {
            if (item == null) {
                continue;
            }

            if (item instanceof Viewable) {
                return false;
            }

            if (item instanceof ImagePane || item instanceof MediaValue) {
                return false;
            }

            if (item instanceof Collection<?>) {
                return false;
            }

            if (item instanceof Map<?, ?>) {
                return false;
            }

            if (automaticUrlKind(item) != UrlKind.NONE) {
                return false;
            }
        }

        return true;
    }

    private static boolean isSimpleMap(Map<?, ?> map) {
        for (Object key : map.keySet()) {
            if (automaticUrlKind(key) != UrlKind.NONE) {
                return false;
            }
        }
        for (Object value : map.values()) {
            if (value == null) {
                continue;
            }

            if (value instanceof Viewable) {
                return false;
            }

            if (value instanceof ImagePane || value instanceof MediaValue) {
                return false;
            }

            if (value instanceof Collection<?>) {
                return false;
            }

            if (value instanceof Map<?, ?>) {
                return false;
            }

            if (automaticUrlKind(value) != UrlKind.NONE) {
                return false;
            }
        }

        return true;
    }

    /** A Swing component belongs to one layout. In particular, a table may impose a
     *  thumbnail cap without mutating an ImagePane stored in the domain or displayed by
     *  a card elsewhere. */
    private static ImagePane renderCopy(ImagePane pane) {
        return pane.clone(false, true);
    }

    private static JComponent automaticUrlComponent(
            String fieldName, FieldPath fieldPath, Object value) {
        if (!(value instanceof String raw)) return null;
        return switch (automaticUrlKind(raw)) {
            case IMAGE -> {
                ImagePane pane = MediaRenderSupport.imagePane(new UrlMediaValue(raw));
                yield pane == null ? null : imageComponent(fieldName, fieldPath, pane);
            }
            case LINK -> new LinkRow(fieldName, fieldPath, raw, "");
            case NONE -> null;
        };
    }

    /**
     * Whether this value renders as what it POINTS AT — a link, or the picture itself —
     * rather than as its own text. Asked by any layout that decides what to render
     * before delegating here: a card folds ordinary values into one painted text block,
     * and a value folded into it can never become the link or image it denotes.
     */
    public static boolean rendersAsUrl(Object value) {
        return automaticUrlKind(value) != UrlKind.NONE;
    }

    private static UrlKind automaticUrlKind(Object value) {
        if (!(value instanceof String raw) || raw.isBlank()) return UrlKind.NONE;
        try {
            java.net.URI uri = java.net.URI.create(raw.trim());
            String scheme = uri.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    || uri.getHost() == null) {
                return UrlKind.NONE;
            }
            String path = uri.getPath() == null
                    ? "" : uri.getPath().toLowerCase(java.util.Locale.ROOT);
            return IMAGE_EXTENSIONS.stream().anyMatch(path::endsWith)
                    ? UrlKind.IMAGE : UrlKind.LINK;
        } catch (IllegalArgumentException ignored) {
            return UrlKind.NONE;
        }
    }

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".svg", ".avif", ".bmp");

    private enum UrlKind { NONE, LINK, IMAGE }

    private record UrlMediaValue(String mediaUrl) implements MediaValue {
        @Override public String mediaLabel() { return mediaUrl; }
        @Override public boolean mediaSvg() {
            try {
                String path = java.net.URI.create(mediaUrl).getPath();
                return path != null && path.toLowerCase(java.util.Locale.ROOT).endsWith(".svg");
            } catch (IllegalArgumentException ignored) {
                return false;
            }
        }
    }

    private static JPanel basePanel(String fieldName, FieldPath fieldPath, Object value) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);

        if (fieldName != null && !fieldName.isBlank()) {
            panel.setBorder(BorderFactory.createTitledBorder(fieldName));
        }

        panel.putClientProperty(FieldProperties.FIELD_NAME_PROPERTY, fieldName);

        panel.putClientProperty(FieldProperties.FIELD_PATH_PROPERTY, fieldPath);

        panel.putClientProperty(FieldProperties.FIELD_VALUE_PROPERTY, value);

        return panel;
    }
}
