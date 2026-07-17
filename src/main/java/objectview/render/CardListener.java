package objectview.render;

import java.util.List;

/**
 * Notified when the set of {@link Card} cards controlled by a
 * {@link CardListView} changes after the view is already showing.
 *
 * {@link objectview.search.SearchPanel} implements this to re-snapshot the target
 * panel (column count, original order, search index) so that live-added
 * cards are searchable and sortable like the rest.
 *
 * Callbacks fire on the Event Dispatch Thread.
 */
public interface CardListener {

    /** Called after the given cards were added to the controlled target. */
    void cardsAdded(List<Card> added);

    /**
     * Called after the given cards were re-rendered in place because their
     * backing viewables changed (e.g. a query log advancing RUNNING → OK).
     * The panel instances are the same; their content is new.
     */
    void cardsUpdated(List<Card> updated);

    /**
     * Called when a single card is (re)materialized during virtualized scrolling —
     * so a listener can re-apply transient decoration a freshly-built card lacks,
     * e.g. the search highlight (otherwise lost when a card is virtualized out and
     * rebuilt on scroll-back).
     */
    default void cardMaterialized(Card card) {
    }
}
