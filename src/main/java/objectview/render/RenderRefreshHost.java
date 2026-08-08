package objectview.render;

import objectview.Viewable;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;

/**
 * A rendered object container that can rebuild itself after shared expansion
 * state changes. Both cards and column-layout rows implement this, so reference
 * and collection controls do not need to know which layout contains them.
 *
 * <p>It is also the handle on <em>the component that renders one instance</em>:
 * search highlights the instance and selection resolves its owner through this
 * contract, so neither has to know whether the layout is a card or a row.
 */
public interface RenderRefreshHost {
    void refreshRenderedContent();

    /** The instance this component renders. */
    Viewable renderedInstance();

    /** Applies the search-hit tint, or clears it with {@code null}. Painted by
     *  the implementation so it can composite under selection. */
    void setHighlightColor(Color color);

    /** Whether a search-hit tint is currently applied. */
    boolean isHighlighted();

    /** Expands whatever hides the value at {@code path} — a collapsed collection,
     *  a reference chip — so a match there is actually visible. Returns true when
     *  state changed and the host must be refreshed. Presentations that reveal
     *  before the component is built (see SearchNavigableContainer) keep the
     *  default. */
    default boolean revealPath(objectview.field.FieldPath path) {
        return false;
    }

    /** The nearest containing render host, or null. Mouse events do not bubble
     *  in Swing, so a listener on a child resolves its owning instance here. */
    static RenderRefreshHost hostOf(Component component) {
        for (Component current = component; current != null;
             current = current.getParent()) {
            if (current instanceof RenderRefreshHost host) {
                return host;
            }
        }
        return null;
    }

    /** Refreshes the nearest containing render host, if any. */
    static void refreshAncestor(Component component) {
        for (Container parent = component == null ? null : component.getParent();
             parent != null; parent = parent.getParent()) {
            if (parent instanceof RenderRefreshHost host) {
                host.refreshRenderedContent();
                return;
            }
        }
    }
}
