package objectview.search;

import objectview.Viewable;
import objectview.render.CardListView;
import objectview.render.CardSearchBarFactory;
import objectview.viewconfig.FieldTypeSource;

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
        return createSearchBar(view, viewableType, null, null);
    }

    @Override
    public JComponent createSearchBar(
            CardListView view,
            Class<? extends Viewable> viewableType,
            Viewable sample,
            FieldTypeSource fieldTypes) {
        SearchPanel searchPanel = new SearchPanel(
                viewableType, sample, null, java.util.List.of(), fieldTypes);
        searchPanel.setTarget(view.getCardsPanel(), view.getCardsScrollPane());
        searchPanel.setRenderContext(view.getRenderContext());
        view.addTargetListener(searchPanel);
        return searchPanel;
    }
}
