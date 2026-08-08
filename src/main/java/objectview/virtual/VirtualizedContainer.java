package objectview.virtual;

import objectview.Viewable;

import javax.swing.JComponent;
import java.util.List;
import java.util.function.BiConsumer;

public interface VirtualizedContainer {
    List<Viewable> items();

    /**
     * Visits the currently materialized item/component pairs without forcing
     * new materialization. Clients remain independent of whether a component
     * represents a card, a row, or another presentation.
     */
    default void forEachMaterialized(BiConsumer<Viewable, JComponent> visitor) {}

    /**
     * Registers the single listener notified as components are materialized, so a
     * component built later (scrolled back into view) can be brought up to date —
     * the search re-applies a lost highlight here, for cards and rows alike.
     */
    default void setMaterializationListener(
            java.util.function.Consumer<JComponent> listener) {}

    Viewable topVisibleItem();
    JComponent navigateToTop(Viewable item);
    void setItems(List<Viewable> orderedItems);
}
