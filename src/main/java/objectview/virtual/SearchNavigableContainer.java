package objectview.virtual;

import objectview.Viewable;
import objectview.field.ViewableFieldPaths;

import javax.swing.JComponent;
import java.util.List;

/**
 * Optional presentation hook used when data-centric search navigates to a hit.
 * The search engine still finds Viewables and field paths; the presentation only
 * decides how that hit becomes VISIBLE — expand a collapsed card, expand the
 * collection or reference holding the match, scroll the row into view.
 *
 * <p>Revealing is all it does. The hit tint and the field/text highlights are
 * applied by the search itself through the shared render-host contract, so a
 * presentation never grows a second highlighting scheme of its own.
 */
public interface SearchNavigableContainer extends VirtualizedContainer {

    JComponent revealSearchHit(
            Viewable item,
            ViewableFieldPaths.PathInfo fieldPath,
            List<String> queryTokens);
}
