package objectview.search;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import objectview.ViewableAdapter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The class visible in a shared configuration dialog is the only class Apply changes. */
class MultiSearchBarTest {

    private static final class DynamicGraphRecord extends ViewableAdapter {
        @Override public String getIdentifier() { return "Q1"; }
        @Override public String getDisplayName() { return "accepted office"; }
        @Override public String typeName() { return "PositionReplacementExpansion"; }
    }

    @Test void aDynamicSectionIsNamedByItsSemanticClassNotItsJavaCarrier() {
        DynamicGraphRecord sample = new DynamicGraphRecord();
        SearchPanel panel = new SearchPanel(DynamicGraphRecord.class, sample);

        assertEquals("PositionReplacementExpansion", panel.sectionTypeName());
    }

    @Test void applyAffectsOnlyTheSelectedClassTab() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Position", new JPanel());
        tabs.addTab("PositionWithHolders", new JPanel());
        tabs.addTab("OfficeHolding", new JPanel());
        tabs.setSelectedIndex(1);
        List<String> applied = new ArrayList<>();

        MultiSearchBar.applySelected(
                tabs,
                List.of("Position", "PositionWithHolders", "OfficeHolding"),
                applied::add);

        assertEquals(List.of("PositionWithHolders"), applied);
    }
}
