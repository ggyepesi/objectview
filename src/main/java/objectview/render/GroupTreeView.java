package objectview.render;

import objectview.group.GroupNode;
import objectview.group.ViewableGroup;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.function.Consumer;

/** Pure tree renderer for a {@link ViewableGroup}; instance rendering belongs to its host. */
public class GroupTreeView extends JPanel {
    private final ViewableGroup<?> rootGroup;
    private final JTree tree;
    private final JScrollPane treeScrollPane;
    private final JPanel controls;
    private final JLabel status;
    private Consumer<ViewableGroup<?>> showGroupHandler;
    private Consumer<ViewableGroup<?>> selectionHandler;

    public GroupTreeView(ViewableGroup<?> rootGroup) {
        this.rootGroup = java.util.Objects.requireNonNull(rootGroup, "rootGroup");
        setLayout(new BorderLayout(4, 4));

        tree = new JTree(buildNode(rootGroup));
        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(1);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        tree.addTreeSelectionListener(event -> {
            if (selectionHandler != null) selectionHandler.accept(selectedGroup());
        });
        tree.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke("ENTER"), "showGroup");
        tree.getActionMap().put("showGroup", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                showSelectedGroup();
            }
        });
        tree.expandRow(0);
        tree.setSelectionRow(0);
        treeScrollPane = new JScrollPane(tree);

        JButton showButton = new JButton("Show instances");
        showButton.addActionListener(event -> showSelectedGroup());
        controls = new JPanel(new GridLayout(0, 3, 4, 4));
        controls.add(showButton);

        status = new JLabel(" ");
        status.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        add(status, BorderLayout.NORTH);
        add(treeScrollPane, BorderLayout.CENTER);
        add(controls, BorderLayout.SOUTH);
    }

    private static DefaultMutableTreeNode buildNode(ViewableGroup<?> group) {
        return buildNode(group, java.util.Collections.newSetFromMap(
                new java.util.IdentityHashMap<>()));
    }

    // The working tree is acyclic, but a persisted/adapted group graph could carry a
    // back-edge; a visited-identity set breaks it so a bad snapshot can't StackOverflow
    // the renderer (a revisited node is shown as a childless leaf rather than recursed).
    private static DefaultMutableTreeNode buildNode(
            ViewableGroup<?> group, java.util.Set<ViewableGroup<?>> visited) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new GroupNode(group));
        if (visited.add(group)) {
            for (ViewableGroup<?> child : group.getChildren()) {
                node.add(buildNode(child, visited));
            }
        }
        return node;
    }

    private void showSelectedGroup() {
        ViewableGroup<?> selected = selectedGroup();
        if (selected != null && showGroupHandler != null) showGroupHandler.accept(selected);
    }

    public ViewableGroup<?> getRootGroup() { return rootGroup; }
    public JTree getTree() { return tree; }
    public JScrollPane getTreeScrollPane() { return treeScrollPane; }

    public void setShowGroupHandler(Consumer<ViewableGroup<?>> handler) {
        showGroupHandler = handler;
    }

    public void setSelectionHandler(Consumer<ViewableGroup<?>> handler) {
        selectionHandler = handler;
    }

    public JButton addControl(String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        controls.add(button, Math.max(0, controls.getComponentCount() - 1));
        controls.revalidate();
        controls.repaint();
        return button;
    }

    public void setStatusText(String text) {
        status.setText(text == null || text.isBlank() ? " " : text);
    }

    public ViewableGroup<?> selectedGroup() {
        Object selected = tree.getLastSelectedPathComponent();
        return selected instanceof DefaultMutableTreeNode node
                ? getViewableGroup(node) : null;
    }

    public ViewableGroup<?> getViewableGroup(DefaultMutableTreeNode node) {
        return node != null && node.getUserObject() instanceof GroupNode groupNode
                ? groupNode.group() : null;
    }

    /** Select and optionally show a group by identity after rebuilding the tree. */
    public boolean selectGroup(ViewableGroup<?> target, boolean show) {
        if (target == null) return false;
        java.util.Enumeration<?> nodes = ((DefaultMutableTreeNode) tree.getModel().getRoot())
                .breadthFirstEnumeration();
        while (nodes.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) nodes.nextElement();
            if (getViewableGroup(node) == target) {
                javax.swing.tree.TreePath path = new javax.swing.tree.TreePath(node.getPath());
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                if (show && showGroupHandler != null) showGroupHandler.accept(target);
                return true;
            }
        }
        return false;
    }
}
