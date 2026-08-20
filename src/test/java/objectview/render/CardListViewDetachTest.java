package objectview.render;

import objectview.Viewable;
import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import javax.swing.JComponent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A section's registrations on a SHARED render context outlive the section unless it
 * gives them up. A reused window rebuilds its cards, and a closed window keeps nothing
 * worth resolving — so a rebuild must replace its own registration rather than add
 * another, and a close must leave none behind. Otherwise the context goes on answering
 * with cards from a virtual list nobody can reach.
 */
class CardListViewDetachTest {

    /** Counts what a section holds on this context, which is the thing that leaks. */
    static final class CountingContext extends RenderContext {
        int resolvers;
        int toggleHandlers;

        @Override public void addTopLevelResolver(Function<Object, JComponent> resolver) {
            resolvers++;
            super.addTopLevelResolver(resolver);
        }

        @Override public void removeTopLevelResolver(Function<Object, JComponent> resolver) {
            resolvers--;
            super.removeTopLevelResolver(resolver);
        }

        @Override public void addCardToggleHandler(Consumer<Viewable> handler) {
            toggleHandlers++;
            super.addCardToggleHandler(handler);
        }

        @Override public void removeCardToggleHandler(Consumer<Viewable> handler) {
            toggleHandlers--;
            super.removeCardToggleHandler(handler);
        }
    }

    static class Row extends ViewableAdapter {
        private final String name;
        Row(String name) { this.name = name; }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
    }

    private static CardListView sectionOf(RenderContext context) {
        CardListView view = new CardListView();
        view.setRenderContext(context);
        view.setViewables(List.of(new Row("one")));
        view.createCardsPanel(1);
        return view;
    }

    @Test void rebuildingReplacesTheSectionsRegistrationRatherThanAddingOne() {
        CountingContext context = new CountingContext();
        CardListView view = sectionOf(context);

        assertEquals(1, context.resolvers);
        assertEquals(1, context.toggleHandlers);

        view.createCardsPanel(1);
        view.createCardsPanel(1);

        assertEquals(1, context.resolvers, "a rebuilt section is still one section");
        assertEquals(1, context.toggleHandlers);
    }

    @Test void aClosedSectionLeavesNothingRegistered() {
        CountingContext context = new CountingContext();
        CardListView view = sectionOf(context);

        view.dispose();

        assertEquals(0, context.resolvers,
                "closing the window gives up what the section held");
        assertEquals(0, context.toggleHandlers);
    }

    @Test void aSectionShownAgainAfterClosingResolvesOnceMore() {
        CountingContext context = new CountingContext();
        CardListView view = sectionOf(context);
        view.dispose();

        view.createCardsPanel(1);

        assertEquals(1, context.resolvers, "showing it again re-registers it");
        assertEquals(1, context.toggleHandlers);
    }
}
