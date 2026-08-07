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
