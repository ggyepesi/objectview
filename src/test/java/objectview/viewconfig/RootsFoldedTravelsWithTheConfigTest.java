package objectview.viewconfig;

import objectview.ViewableAdapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test void theEmptyFieldChoiceTravelsOnceForTheWholeNestedView() throws Exception {
        ViewConfig config = ViewConfig.of(Item.class);
        config.addField("child", ViewConfig.of(Item.class));
        config.emptyFieldsVisible(false);

        ViewConfig copied = config.copy();
        assertFalse(copied.showsEmptyFields());
        assertFalse(copied.getFieldConfig("child").showsEmptyFields());

        ViewConfig literal = objectview.plan.ViewConfigDesugar.literal(
                config, objectview.plan.TypeShape.ofClass(Item.class));
        assertFalse(literal.showsEmptyFields());
        assertFalse(literal.getFieldConfig("child").showsEmptyFields());

        java.io.File file = java.io.File.createTempFile("empty-fields", ".json");
        file.deleteOnExit();
        ViewConfigJsonIO.save(file, config);
        ViewConfig loaded = ViewConfigJsonIO.fromJson(ViewConfigJsonIO.loadJson(file));
        assertFalse(loaded.showsEmptyFields());
        assertFalse(loaded.getFieldConfig("child").showsEmptyFields());
        assertNull(ViewConfigJsonIO.loadJson(file).fields.get("child").emptyFieldsVisible,
                "the one view-wide choice is written only at the root");
    }

    @Test void anOlderConfigShowsEmptyFieldsByDefault() {
        assertTrue(ViewConfig.of(Item.class).showsEmptyFields());
    }

    private static final class Item extends ViewableAdapter {
        @Override public String getIdentifier() { return "x"; }
        @Override public String getDisplayName() { return "x"; }
    }
}
