package objectview.viewconfig;

import objectview.Viewable;
import objectview.media.ImagePane;

import java.util.Collection;
import java.util.Map;

public final class ViewConfigAdapter {
    private ViewConfigAdapter() {}

    public static ViewConfig fromOldArgs(Viewable q,
                                         boolean showNames,
                                         boolean showImages,
                                         boolean expand) {

        if (q == null) {
            return ViewConfig.leaf();
        }

        Class<? extends Viewable> cls = q.getClass();

        if (expand) {
            return ViewConfig.all(cls)
                             .setAddListener(true)
                             .setThumb(showImages);
        }

        return ViewConfig.of(cls)
                         .setAddListener(true)
                         .setThumb(showImages);
    }

    public static ViewConfig defaultConfigForValue(Object value) {
        if (value == null) {
            return ViewConfig.leaf();
        }

        if (value instanceof ImagePane) {
            return ViewConfig.leaf();
        }

        if (value instanceof Viewable q) {
            // IMPORTANT: compact by default, do not recurse here.
            return nameOnlyConfig(q.getClass());
        }

        if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Viewable q) {
                    return ViewConfig.all(q.getClass());
                }
            }
            return ViewConfig.leaf();
        }

        if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                if (item instanceof Viewable q) {
                    return ViewConfig.all(q.getClass());
                }
            }
            return ViewConfig.leaf();
        }

        return ViewConfig.leaf();
    }

    public static ViewConfig defaultConfigForValueType(Class<?> type) {
        if (type == null) {
            return ViewConfig.leaf();
        }

        if (ImagePane.class.isAssignableFrom(type)) {
            return ViewConfig.leaf();
        }

        if (Viewable.class.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked")
            Class<? extends Viewable> qCls = (Class<? extends Viewable>) type;
            return nameOnlyConfig(qCls);
        }

        return ViewConfig.leaf();
    }

    private static ViewConfig nameOnlyConfig(Class<? extends Viewable> cls) {
        return ViewConfig.of(cls)
                         .setAddListener(true)
                         .setThumb(true);
    }
}