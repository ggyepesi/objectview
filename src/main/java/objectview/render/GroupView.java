package objectview.render;

import objectview.Viewable;
import objectview.group.ViewableGroup;
import objectview.field.FieldSchema;
import objectview.viewconfig.FieldTypeSource;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Standalone browser composed from the shared {@link GroupTreeView} outline.
 */
public class GroupView extends JPanel {
    private final GroupMembersView groupMembersView;
    private final GroupTreeView treeView;
    private final Viewable fieldConfigurationSample;
    private final FieldTypeSource fieldTypes;
    private Consumer<ViewableGroup<?>> showGroupObserver = ignored -> { };
    private CardListView activeMemberView;

    public GroupView(ViewableGroup<?> rootGroup) {
        this(rootGroup, new RenderContext());
    }

    /** Uses the host's ordinary render context, so fields shown from the group obey the
     * same links, top-level references and navigation as every neighbouring card. */
    public GroupView(ViewableGroup<?> rootGroup, RenderContext renderContext) {
        this(rootGroup, null, null, null, null, renderContext);
    }

    /** {@code fieldSchemas}: an instance's own schema; {@code typeSchemas}: a declared
     * type's, by name. */
    public GroupView(
            ViewableGroup<?> rootGroup,
            Viewable fieldConfigurationSample,
            FieldTypeSource fieldTypes,
            java.util.function.Function<Viewable, FieldSchema> fieldSchemas,
            java.util.function.Function<String, FieldSchema> typeSchemas) {
        this(rootGroup, fieldConfigurationSample, fieldTypes, fieldSchemas, typeSchemas,
                new RenderContext());
    }

    private GroupView(
            ViewableGroup<?> rootGroup,
            Viewable fieldConfigurationSample,
            FieldTypeSource fieldTypes,
            java.util.function.Function<Viewable, FieldSchema> fieldSchemas,
            java.util.function.Function<String, FieldSchema> typeSchemas,
            RenderContext renderContext) {
        super(new BorderLayout());
        this.fieldConfigurationSample = fieldConfigurationSample;
        this.fieldTypes = fieldTypes;
        if (fieldSchemas != null) renderContext.setFieldSchemaResolver(fieldSchemas);
        if (typeSchemas != null) renderContext.setTypeSchemaResolver(typeSchemas);
        groupMembersView = new GroupMembersView(
                rootGroup, group -> memberView(group, renderContext),
                JSplitPane.HORIZONTAL_SPLIT, false, 0.3, false, renderContext);
        treeView = groupMembersView.groups();
        add(groupMembersView, BorderLayout.CENTER);
    }

    private JComponent memberView(
            ViewableGroup<?> group, RenderContext renderContext) {
        showGroupObserver.accept(group);
        if (activeMemberView != null) {
            activeMemberView.dispose();
            activeMemberView = null;
        }
        if (group == null || group.getMembers().isEmpty()) {
            return new JLabel("  This group has no instances.");
        }
        CardListView view = new CardListView();
        view.setFieldConfiguration(fieldConfigurationSample, fieldTypes);
        view.setRenderContext(renderContext);
        view.setInPlaceNavigation(true);
        for (Viewable member : group.getMembers()) view.addViewable(member);
        view.createCardsPanel(2);
        activeMemberView = view;
        return view.getCardsScrollPane();
    }

    public JPanel getMainPanel() { return treeView; }
    public ViewableGroup<?> getRootGroup() { return treeView.getRootGroup(); }
    public JTree getTree() { return treeView.getTree(); }
    public JScrollPane getTreeScrollPane() { return treeView.getTreeScrollPane(); }
    public ViewableGroup<?> selectedGroup() { return treeView.selectedGroup(); }
    public ViewableGroup<?> getViewableGroup(DefaultMutableTreeNode node) {
        return treeView.getViewableGroup(node);
    }
    public void setShowGroupHandler(Consumer<ViewableGroup<?>> handler) {
        showGroupObserver = handler == null ? ignored -> { } : handler;
    }
    public void setSelectionHandler(Consumer<ViewableGroup<?>> handler) {
        treeView.setSelectionHandler(handler);
    }
    public JButton addControl(String label, Runnable action) {
        return treeView.addControl(label, action);
    }
    public void setStatusText(String text) { treeView.setStatusText(text); }

    public JFrame createFrame() {
        JFrame frame = new JFrame(getRootGroup().getDisplayName());
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setContentPane(this);
        frame.setSize(1200, 700);
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
        return frame;
    }

    public void showFrame() { createFrame().setVisible(true); }
}
