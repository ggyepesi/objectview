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

    /**
     * Scrolls a virtualized member matching {@code tokens} on {@code path} into view.
     *
     * <p>Expanding the collection is not enough once the collection is virtualized:
     * the matching member has no component at all until its own list scrolls to it,
     * so search finds an honest hit, fails to locate a row for it, and badges it as
     * hidden. Revealing the PATH and revealing the MEMBER are therefore two steps,
     * and only the host knows whether the second one has anywhere to go.
     *
     * @return true when a member was scrolled to, so the caller can look again
     */
    default boolean revealPathMember(
            FieldPath path, java.util.List<String> tokens, int occurrence) {
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
