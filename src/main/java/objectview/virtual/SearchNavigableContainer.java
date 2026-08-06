package objectview.virtual;

import objectview.Viewable;
import objectview.field.ViewableFieldPaths;

import javax.swing.JComponent;
import java.util.List;

/**
 * Optional presentation hook used when data-centric search navigates to a hit.
 * The search engine still finds Viewables and field paths; the presentation only
 * decides how that hit becomes visible (expand a card, select a table cell, or
 * choose a matching element of a collection-valued cell).
 */
public interface SearchNavigableContainer extends VirtualizedContainer {

    JComponent revealSearchHit(
            Viewable item,
            ViewableFieldPaths.PathInfo fieldPath,
            List<String> queryTokens);

    void clearSearchHighlight();
}
