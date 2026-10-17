package objectview.render;

import objectview.ViewableAdapter;
import objectview.annotations.DisplayField;
import objectview.annotations.Inline;
import objectview.plan.MockSink;
import objectview.plan.ViewDefaults;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Expand all below" / "Collapse all below" (#398): explicit commands on one node that
 * write the same per-occurrence choices a click writes. Collapsing forgets the choices
 * below, so reopening the node opens one level again; a card is the root occurrence,
 * kept in the same store as everything below it.
 */
class DisclosureCommandsTest {

    private static final class Step extends ViewableAdapter {
        @DisplayField private final String title;
        @SuppressWarnings("unused") private final String detail;
        @Inline private final List<Step> steps = new ArrayList<>();
        Step(String title) { this.title = title; this.detail = title + " detail"; }
        @Override public String getIdentifier() { return title; }
        @Override public String getDisplayName() { return title; }
    }

    private final Step run = new Step("run");
    private final Step load = new Step("load");
    private final Step query = new Step("query");

    DisclosureCommandsTest() {
        run.steps.add(load);
        load.steps.add(query);
    }

    private String trace(RenderContext context) {
        MockSink sink = new MockSink();
        context.executor().render(run, config(context), sink);
        return sink.traceText();
    }

    private ViewConfig config(RenderContext context) {
        return ViewDefaults.newView(context.shape(run));
    }

    @Test void expandAllBelowOpensEveryLevelOfTheNode() {
        RenderContext context = new RenderContext();

        RenderContext.DisclosureCommandResult result =
                context.expandAllBelow(run, config(context));

        String trace = trace(context);
        // An empty list has nothing to open; everything else is open.
        assertFalse(trace.lines().anyMatch(line -> line.contains("folded")
                && !line.contains("(0) folded")), trace);
        assertTrue(trace.contains("query detail"), trace);
        assertFalse(result.limited());
    }

    @Test void collapseAllBelowForgetsTheChoicesSoReopeningOpensOneLevel() {
        RenderContext context = new RenderContext();
        context.setCollapsibleCards(true);
        context.expandAllBelow(run, config(context));
        assertTrue(context.isCardExpanded(run));

        context.collapseAllBelow(run, config(context));
        assertFalse(context.isCardExpanded(run), "the card itself folds");

        context.toggleCardExpanded(run);
        String trace = trace(context);
        assertTrue(trace.contains("COLLECTION steps (1) folded"),
                "reopened, it shows one level again:\n" + trace);
    }

    @Test void aCardIsTheRootOccurrenceInTheSameStore() {
        RenderContext context = new RenderContext();
        context.setCollapsibleCards(true);
        assertTrue(trace(context).startsWith("OBJECT <root> caption=\"run\" folded"),
                "a view that folds its roots starts them folded");

        context.setExpanded(run, true);
        assertTrue(context.isCardExpanded(run), "the card reads the one choice store");
        assertTrue(trace(context).startsWith("OBJECT <root> caption=\"run\" open"));

        RenderContext unfolded = new RenderContext();
        unfolded.setExpanded(run, false);
        assertTrue(trace(unfolded).startsWith("OBJECT <root> caption=\"run\" open"),
                "a view that does not fold its roots never folds them");
        assertEquals(true, unfolded.isCardExpanded(run));
    }
}
