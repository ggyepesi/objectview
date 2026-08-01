package objectview.virtual;

import objectview.viewconfig.ViewConfig;
import objectview.Viewable;
import java.util.function.Function;

public interface ConfigurableVirtualizedContainer
        extends VirtualizedContainer {
    void setCardConfig(ViewConfig config);
    void setCardConfigResolver(Function<Viewable, ViewConfig> resolver);
}
