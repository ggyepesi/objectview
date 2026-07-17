package objectview.render;

import objectview.Viewable;

import javax.swing.JComponent;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Builds a search bar bound to a {@link CardListView}, so render can offer
 * "open these cards in a window with search" without depending on the search
 * package. The interface lives in {@code render}; an implementation in
 * {@code search} is discovered on the classpath via {@link ServiceLoader}
 * (reflective, so no compile-time render → search edge), mirroring the
 * {@link objectview.media.ImageBlurrer} / {@code SvgRasterizer} pattern.
 *
 * <p>If no implementation is present, no search bar is added (the window still
 * opens). A host may override discovery with {@link #setActive}.
 */
public interface CardSearchBarFactory {

    /**
     * Build a search bar for {@code view} (whose members are of
     * {@code viewableType}), fully wired to filter/highlight that view, or
     * {@code null} for no bar.
     */
    JComponent createSearchBar(CardListView view, Class<? extends Viewable> viewableType);

    AtomicReference<CardSearchBarFactory> ACTIVE = new AtomicReference<>();

    /** Override the classpath-discovered factory (null re-enables discovery). */
    static void setActive(CardSearchBarFactory factory) {
        ACTIVE.set(factory);
    }

    /** The active factory: an explicit override, else the first one discovered
     *  on the classpath, else {@code null} (no search bar). */
    static CardSearchBarFactory active() {
        CardSearchBarFactory factory = ACTIVE.get();
        if (factory == null) {
            factory = ServiceLoader.load(CardSearchBarFactory.class)
                    .findFirst()
                    .orElse(null);
            ACTIVE.compareAndSet(null, factory);
        }
        return ACTIVE.get();
    }
}
