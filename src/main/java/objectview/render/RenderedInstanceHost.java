package objectview.render;

import objectview.Viewable;
import objectview.field.FieldPath;

import java.awt.Color;
import java.awt.Component;

/**
 * The root component rendering one Viewable instance, independent of layout.
 * Cards and column rows implement this so selection and search can address the
 * instance without knowing its presentation.
 */
public interface RenderedInstanceHost extends RenderRefreshHost {

    Viewable renderedInstance();

    /** Applies the search-hit tint, or clears it with {@code null}. */
    void setHighlightColor(Color color);

    boolean isHighlighted();

    /** Reveals a value hidden along {@code path}; true means refresh is needed. */
    default boolean revealPath(FieldPath path) {
        return false;
    }

    /** The nearest containing rendered-instance root, or null. */
    static RenderedInstanceHost hostOf(Component component) {
        for (Component current = component; current != null;
             current = current.getParent()) {
            if (current instanceof RenderedInstanceHost host) return host;
        }
        return null;
    }
}
