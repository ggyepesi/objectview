package objectview.search;

import objectview.Viewable;
import objectview.render.CardListView;
import objectview.render.CardSearchBarFactory;

import javax.swing.JComponent;

/**
 * The search package's {@link CardSearchBarFactory}: builds a {@link SearchPanel}
 * wired to a card view. Discovered via {@code ServiceLoader} (see
 * {@code META-INF/services/objectview.render.CardSearchBarFactory}), so render
 * gets a search bar with no compile dependency on this package.
 */
public final class SearchPanelBarFactory implements CardSearchBarFactory {

    @Override
    public JComponent createSearchBar(CardListView view, Class<? extends Viewable> viewableType) {
        SearchPanel searchPanel = new SearchPanel(viewableType);
        searchPanel.setTarget(view.getCardsPanel(), view.getCardsScrollPane());
        searchPanel.setRenderContext(view.getRenderContext());
        view.addTargetListener(searchPanel);
        return searchPanel;
    }
}
