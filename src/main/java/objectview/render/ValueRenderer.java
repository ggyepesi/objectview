package objectview.render;

import objectview.utils.swing.GridBagUtils;
import objectview.media.ImagePane;
import objectview.media.MediaValue;
import objectview.field.FieldProperties;
import objectview.field.FieldPath;
import objectview.Viewable;
import objectview.viewconfig.ViewConfig;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public final class ValueRenderer {
    private ValueRenderer() {
    }

    public static JComponent createFieldComponent(
            Set<Object> visited, Set<Object> ancestors, RenderContext renderContext,
            String fieldName, FieldPath fieldPath, Object value,
            ViewConfig config, boolean fill) {
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

        if (value instanceof Viewable q) {
            return viewableComponent(visited, ancestors, renderContext, fieldName, fieldPath, q, config, fill);
        }

        if (value instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return null;
            }

            if (isSimpleCollection(collection)) {
                return simpleCollectionComponent(fieldName, fieldPath, collection);
            }

            return complexCollectionComponent(visited, ancestors, renderContext, fieldName, fieldPath, collection, config, fill);
        }

        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return null;
            }

            if (isSimpleMap(map)) {
                return simpleMapComponent(fieldName, fieldPath, map);
            }

            return mapComponent(visited, ancestors, renderContext, fieldName, fieldPath, map, config, fill);
        }

        JComponent url = automaticUrlComponent(fieldName, fieldPath, value);
        if (url != null) {
            return url;
        }

        return leafComponent(fieldName, fieldPath, value);
    }

    private static JComponent imageComponent(String fieldName, FieldPath fieldPath, ImagePane imagePane) {
        JPanel panel = basePanel(fieldName, fieldPath, imagePane);

        panel.add(imagePane, GridBagUtils.weighted(0, 0, 1.0, 1.0, GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(2, 2, 2, 2)));

        return panel;
    }

    private static JComponent viewableComponent(
            Set<Object> visited, Set<Object> ancestors, RenderContext renderContext,
            String fieldName, FieldPath fieldPath, Viewable q, ViewConfig config, boolean fill) {
        return viewableComponent(visited, ancestors, renderContext, fieldName, fieldPath, q, config, fill, false);
    }

    private static JComponent viewableComponent(
            Set<Object> visited, Set<Object> ancestors, RenderContext renderContext,
            String fieldName, FieldPath fieldPath, Viewable q, ViewConfig config, boolean fill,
            boolean suppressTitle) {
        JPanel panel = basePanel(fieldName, fieldPath, q);

        Card nested = new Card(
                visited, ancestors, renderContext, false, q, config, fill, fieldPath, null, null, suppressTitle);

        if (!nested.hasRenderedConfiguredContent()) {
            return null;
        }

        panel.add(nested, GridBagUtils.weighted(0, 0, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2, 2)));

        return panel;
    }

    private static JComponent simpleCollectionComponent(String fieldName, FieldPath fieldPath, Collection<?> collection) {
        List<String> lines = collection.stream().filter(Objects::nonNull).map(String::valueOf).filter(s -> !s.isBlank()).map(s -> "• " + s).collect(Collectors.toList());

        if (lines.isEmpty()) {
            return null;
        }

        return new TextRow(fieldName, fieldPath, lines);
    }

    private static JComponent complexCollectionComponent(Set<Object> visited, Set<Object> ancestors, RenderContext renderContext, String fieldName, FieldPath fieldPath, Collection<?> collection, ViewConfig config, boolean fill) {
        JPanel panel = basePanel(fieldName, fieldPath, collection);

        int row = 0;

        for (Object item : collection) {
            JComponent child = createCollectionItemComponent(visited, ancestors, renderContext, fieldPath, item, config, fill);

            if (child == null) {
                continue;
            }

            panel.add(child, GridBagUtils.weighted(0, row++, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2, 2)));
        }

        return row == 0 ? null : panel;
    }

    private static JComponent mapComponent(Set<Object> visited, Set<Object> ancestors, RenderContext renderContext, String fieldName, FieldPath fieldPath, Map<?, ?> map, ViewConfig config, boolean fill) {
        JPanel panel = basePanel(fieldName, fieldPath, map);

        int row = 0;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            JPanel entryPanel = new JPanel(new BorderLayout(6, 0));
            entryPanel.setOpaque(false);

            JComponent keyComponent = automaticUrlComponent(
                    "", fieldPath, entry.getKey());
            if (keyComponent == null) {
                JLabel keyLabel = new JLabel(String.valueOf(entry.getKey()));
                keyLabel.setFont(keyLabel.getFont().deriveFont(Font.BOLD));
                keyComponent = keyLabel;
            }

            JComponent valueComponent = createCollectionItemComponent(visited, ancestors, renderContext, fieldPath, entry.getValue(), config, fill);

            if (valueComponent == null) {
                continue;
            }

            entryPanel.add(keyComponent, BorderLayout.WEST);
            entryPanel.add(valueComponent, BorderLayout.CENTER);

            panel.add(entryPanel, GridBagUtils.weighted(0, row++, 1.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL, new Insets(2, 2, 2, 2)));
        }

        return row == 0 ? null : panel;
    }

    private static JComponent simpleMapComponent(String fieldName, FieldPath fieldPath, Map<?, ?> map) {
        String text = map.entrySet().stream().filter(e -> e.getKey() != null || e.getValue() != null).map(e -> e.getKey() + " -> " + e.getValue()).filter(s -> !s.isBlank()).collect(Collectors.joining(", "));

        if (text.isBlank()) {
            return null;
        }

        return new TextRow(fieldName, fieldPath, text);
    }

    private static JComponent createCollectionItemComponent(Set<Object> visited, Set<Object> ancestors, RenderContext renderContext, FieldPath fieldPath, Object item, ViewConfig config, boolean fill) {
        if (item == null) {
            return null;
        }

        if (item instanceof MediaValue media) {
            ImagePane pane = MediaRenderSupport.imagePane(media);
            return pane == null ? null : imageComponent("", fieldPath, pane);
        }

        if (item instanceof ImagePane imagePane) {
            return imageComponent("", fieldPath, renderCopy(imagePane));
        }


        if (item instanceof Viewable q) {
            // A member that is itself a top-level card navigates to it instead
            // of expanding in place (see Card.collapsibleReference).
            if (renderContext != null && renderContext.isTopLevel(q)) {
                return Card.decorateReference(renderContext, new ReferenceRow(
                        "", fieldPath, q, renderContext, config, q.getName(), false, true), q);
            }

            boolean exp = renderContext != null && renderContext.isExpanded(q);

            ReferenceRow chip =
                    new ReferenceRow(
                            "", fieldPath, q, renderContext, config, q.getName(), exp);

            if (!exp) {
                return Card.decorateReference(renderContext, chip, q);
            }

            JPanel wrap = new JPanel(new GridBagLayout());
            wrap.setOpaque(false);

            wrap.add(Card.decorateReference(renderContext, chip, q), GridBagUtils.weighted(
                    0, 0, 1.0, 0.0,
                    GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                    new Insets(0, 0, 0, 0)));

            // The chip above already shows the name, so suppress the expanded
            // body's own title header (mirrors Card.collapsibleReference).
            JComponent inline = viewableComponent(
                    visited, ancestors, renderContext, "", fieldPath, q, config, fill, true);

            if (inline != null) {
                wrap.add(inline, GridBagUtils.weighted(
                        0, 1, 1.0, 0.0,
                        GridBagConstraints.NORTHWEST, GridBagConstraints.HORIZONTAL,
                        new Insets(0, 16, 4, 0)));
            }

            return wrap;
        }

        if (item instanceof Collection<?> collection) {
            if (collection.isEmpty()) {
                return null;
            }

            if (isSimpleCollection(collection)) {
                return simpleCollectionComponent("", fieldPath, collection);
            }

            return complexCollectionComponent(visited, ancestors, renderContext, "", fieldPath, collection, config, fill);
        }

        if (item instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return null;
            }

            if (isSimpleMap(map)) {
                return simpleMapComponent("", fieldPath, map);
            }

            return mapComponent(visited, ancestors, renderContext, "", fieldPath, map, config, fill);
        }

        JComponent url = automaticUrlComponent("", fieldPath, item);
        return url != null ? url : leafComponent("", fieldPath, item);
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

    private static Set<Object> copyIdentitySet(Set<Object> original) {
        Set<Object> copy = Collections.newSetFromMap(new IdentityHashMap<>());

        if (original != null) {
            copy.addAll(original);
        }

        return copy;
    }
}
