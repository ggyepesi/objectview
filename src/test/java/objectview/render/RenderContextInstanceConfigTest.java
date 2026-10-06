package objectview.render;

import objectview.ViewableAdapter;
import objectview.viewconfig.ViewConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RenderContextInstanceConfigTest {

    @Test void logicalTypesSharingOneAdapterKeepTheirOwnConfigs() {
        DynamicValue person = new DynamicValue("person", "Person");
        DynamicValue position = new DynamicValue("position", "Position");
        ViewConfig personConfig = ViewConfig.leaf();
        personConfig.addField("offices", ViewConfig.leaf());
        ViewConfig positionConfig = ViewConfig.leaf();
        positionConfig.addField("holders", ViewConfig.leaf());

        RenderContext context = new RenderContext();
        context.putConfig(person, personConfig);
        context.putConfig(position, positionConfig);

        assertTrue(context.configFor(person).hasField("offices"));
        assertFalse(context.configFor(person).hasField("holders"));
        assertTrue(context.configFor(position).hasField("holders"));
        assertFalse(context.configFor(position).hasField("offices"));
    }

    @Test void instancesOfOneLogicalTypeShareOneRegisteredConfig() {
        DynamicValue first = new DynamicValue("first", "Person");
        DynamicValue second = new DynamicValue("second", "Person");
        ViewConfig personConfig = ViewConfig.leaf();
        personConfig.addField("offices", ViewConfig.leaf());
        ViewConfig conflictingLaterConfig = ViewConfig.leaf();
        conflictingLaterConfig.addField("holders", ViewConfig.leaf());

        RenderContext context = new RenderContext();
        context.putConfig(first, personConfig);
        context.putConfigIfAbsent(second, conflictingLaterConfig);

        assertTrue(context.configFor(second).hasField("offices"));
        assertFalse(context.configFor(second).hasField("holders"));
    }

    private static final class DynamicValue extends ViewableAdapter {
        private final String name;
        private final String type;
        private DynamicValue(String name, String type) {
            this.name = name;
            this.type = type;
        }
        @Override public String getIdentifier() { return name; }
        @Override public String getDisplayName() { return name; }
        @Override public String typeName() { return type; }
    }
}
