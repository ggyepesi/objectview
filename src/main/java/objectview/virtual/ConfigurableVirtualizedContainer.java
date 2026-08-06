package objectview.virtual;

import objectview.viewconfig.ViewConfig;
import objectview.Viewable;
import java.util.function.Function;

public interface ConfigurableVirtualizedContainer
        extends VirtualizedContainer {
    /**
     * Supplies the effective field configuration for each row/item. The name is
     * deliberately presentation-neutral: a virtualized card list rebuilds cards,
     * while a table rebuilds columns from the same configuration.
     */
    void setViewConfigResolver(Function<Viewable, ViewConfig> resolver);
}
