package objectview.viewconfig;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A presentation setting survives every path that rebuilds a config (#407). */
class RootsFoldedTravelsWithTheConfigTest {

    @Test void itSurvivesACopyTheLiteralFormAndJson() throws Exception {
        ViewConfig config = ViewConfig.of(Item.class).rootsFolded(true);

        assertEquals(Boolean.TRUE, config.copy().rootsFolded());
        assertEquals(Boolean.TRUE, objectview.plan.ViewConfigDesugar.literal(
                config, objectview.plan.TypeShape.ofClass(Item.class)).rootsFolded());

        java.io.File file = java.io.File.createTempFile("roots-folded", ".json");
        file.deleteOnExit();
        ViewConfigJsonIO.save(file, config);
        assertEquals(Boolean.TRUE,
                ViewConfigJsonIO.fromJson(ViewConfigJsonIO.loadJson(file)).rootsFolded());
    }

    @Test void aConfigSavedBeforeTheSettingExistedLeavesItToTheView() throws Exception {
        java.io.File file = java.io.File.createTempFile("roots-unset", ".json");
        file.deleteOnExit();
        ViewConfigJsonIO.save(file, ViewConfig.of(Item.class));
        assertNull(ViewConfigJsonIO.fromJson(ViewConfigJsonIO.loadJson(file)).rootsFolded());
    }

    private static final class Item extends ViewableAdapter {
        @Override public String getIdentifier() { return "x"; }
        @Override public String getDisplayName() { return "x"; }
    }
}
