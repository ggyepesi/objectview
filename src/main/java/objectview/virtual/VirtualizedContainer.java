package objectview.virtual;

import objectview.Viewable;

import javax.swing.JComponent;
import java.util.List;

public interface VirtualizedContainer {
    List<Viewable> items();
    Viewable topVisibleItem();
    JComponent navigateToTop(Viewable item);
    void setItems(List<Viewable> orderedItems);
}
