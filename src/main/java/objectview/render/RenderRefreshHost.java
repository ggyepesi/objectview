package objectview.render;

import java.awt.Component;
import java.awt.Container;

/**
 * A rendered object container that can rebuild itself after shared expansion
 * state changes. Both cards and column-layout rows implement this, so reference
 * and collection controls do not need to know which layout contains them.
 */
public interface RenderRefreshHost {
    void refreshRenderedContent();

    /** Refreshes the nearest rendering boundary, then asks the containing top-level
     * card to be remeasured. The root component is retained: expanding one nested
     * value must not rematerialize every sibling field in the card. */
    static void refreshAncestor(Component component) {
        RenderRefreshHost nearest = null;
        Card outermostCard = null;
        for (Container parent = component == null ? null : component.getParent();
             parent != null; parent = parent.getParent()) {
            if (parent instanceof RenderRefreshHost host) {
                if (nearest == null) nearest = host;
                if (parent instanceof Card) {
                    outermostCard = (Card) parent;
                } else {
                    host.refreshRenderedContent();
                    return;
                }
            }
        }
        if (nearest != null) nearest.refreshRenderedContent();
        if (outermostCard != null) outermostCard.notifyOwnerLayoutChanged();
    }
}
