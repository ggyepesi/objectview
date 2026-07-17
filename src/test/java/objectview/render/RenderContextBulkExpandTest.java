package objectview.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bulk expand/collapse model that {@link ExpandToolbar} drives: "this level"
 * moves cards only; "recursive" also moves references and collections; collapse
 * overrides default-expanded items; and an explicit per-item toggle still wins.
 */
class RenderContextBulkExpandTest {

    @Test
    void expandThisLevelMovesCardsButNotReferencesOrCollections() {
        RenderContext c = new RenderContext();
        Object card = new Object(), ref = new Object(), coll = new Object();

        c.setBulkExpand(RenderContext.BulkExpand.EXPAND, false);

        assertTrue(c.isCardExpanded(card, false),
                "cards follow a this-level bulk expand");
        assertFalse(c.isExpanded(ref, false),
                "references do NOT follow a this-level (non-recursive) bulk");
        assertFalse(c.isCollectionExpanded(coll, false),
                "collections do NOT follow a this-level (non-recursive) bulk");
    }

    @Test
    void expandRecursiveMovesReferencesAndCollectionsToo() {
        RenderContext c = new RenderContext();
        Object card = new Object(), ref = new Object(), coll = new Object();

        c.setBulkExpand(RenderContext.BulkExpand.EXPAND, true);

        assertTrue(c.isCardExpanded(card, false));
        assertTrue(c.isExpanded(ref, false), "references follow a recursive bulk expand");
        assertTrue(c.isCollectionExpanded(coll, false),
                "collections follow a recursive bulk expand");
    }

    @Test
    void collapseAllOverridesDefaultExpandedItems() {
        RenderContext c = new RenderContext();

        c.setBulkExpand(RenderContext.BulkExpand.COLLAPSE, true);

        assertFalse(c.isCardExpanded(new Object(), true),
                "collapse bulk overrides a default-expanded card");
        assertFalse(c.isExpanded(new Object(), true),
                "collapse bulk overrides a default-expanded reference");
        assertFalse(c.isCollectionExpanded(new Object(), true),
                "collapse bulk overrides a default-expanded collection");
    }

    @Test
    void perItemToggleWinsOverBulk() {
        RenderContext c = new RenderContext();
        Object ref = new Object();

        c.setBulkExpand(RenderContext.BulkExpand.EXPAND, true);
        c.setExpanded(ref, false);   // manual collapse of one reference

        assertFalse(c.isExpanded(ref, false),
                "an explicit per-item state wins over the bulk mode");
    }

    @Test
    void settingBulkClearsPriorPerItemOverrides() {
        RenderContext c = new RenderContext();
        Object ref = new Object();

        c.setExpanded(ref, true);                                   // stale override
        c.setBulkExpand(RenderContext.BulkExpand.COLLAPSE, true);   // should clear it

        assertFalse(c.isExpanded(ref, false),
                "setBulkExpand clears prior overrides so the bulk applies uniformly");
    }
}
