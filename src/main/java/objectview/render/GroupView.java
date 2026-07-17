package objectview.render;

import objectview.Viewable;
import objectview.group.GroupNode;
import objectview.group.ViewableGroup;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.Map;
import java.util.TreeMap;

public class GroupView extends JPanel {
    private final ViewableGroup<?> rootGroup;

    // lazy-created views
    private final Map<String, CardListView> groupViews = new TreeMap<>();

    // lookup by fullname
    private final Map<String, ViewableGroup<?>> groupsByFullName = new TreeMap<>();

    private JTree tree;
    private JScrollPane treeScrollPane;
    private JPanel mainPanel;

    public GroupView(ViewableGroup<?> rootGroup) {
        this.rootGroup = rootGroup;
        setLayout(new BorderLayout(4, 4));
        buildTree();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    public ViewableGroup<?> getRootGroup() {
        return rootGroup;
    }

    public JTree getTree() {
        return tree;
    }

    public JScrollPane getTreeScrollPane() {
        return treeScrollPane;
    }

    private void buildTree() {
        groupsByFullName.clear();
        groupViews.clear();

        DefaultMutableTreeNode rootNode = buildNode(rootGroup);

        tree = new JTree(rootNode);

        tree.setRootVisible(true);
        tree.setShowsRootHandles(true);
        tree.setToggleClickCount(1);

        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        tree.expandRow(0);

        tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("ENTER"), "showGroup");

        tree.getActionMap().put("showGroup", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                showSelectedGroup();
            }
        });

        treeScrollPane = new JScrollPane(tree);

        JButton showButton = new JButton("Show viewables");

        showButton.addActionListener(e -> showSelectedGroup());

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        bottom.add(showButton);

        mainPanel = new JPanel(new BorderLayout(4, 4));
        mainPanel.add(treeScrollPane, BorderLayout.CENTER);
        mainPanel.add(bottom, BorderLayout.SOUTH);

        add(mainPanel, BorderLayout.CENTER);
    }

    private DefaultMutableTreeNode buildNode(ViewableGroup<?> group) {
        groupsByFullName.put(group.getFullName(), group);

        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new GroupNode(group));

        for (ViewableGroup<?> child : group.getChildren()) {
            node.add(buildNode(child));
        }

        return node;
    }

    private void showSelectedGroup() {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();

        if (node == null) {
            return;
        }

        GroupNode groupNode = (GroupNode) node.getUserObject();

        showGroup(groupNode.getFullName());
        Card.RenderStats.print();
    }

    private void showGroup(String fullName) {
        CardListView view = groupViews.computeIfAbsent(fullName, this::createViewForGroup);
        if (view != null) {
            view.show(fullName, 2);
        }
    }

    private CardListView createViewForGroup(String fullName) {
        ViewableGroup<?> group = groupsByFullName.get(fullName);

        if (group == null || group.getMembers().isEmpty()) {
            return null;
        }

        CardListView view = new CardListView();
        for (Viewable q : group.getMembers()) {
            view.addViewable(q);
        }

        return view;
    }

    public JFrame createFrame() {
        JFrame frame = new JFrame(rootGroup.getDisplayName());

        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        frame.setContentPane(this);

        frame.setSize(1200, 700);
        frame.setLocationRelativeTo(null);
        frame.setResizable(true);
        return frame;
    }

    public void showFrame() {
        createFrame().setVisible(true);
    }

    public ViewableGroup<?> getViewableGroup(DefaultMutableTreeNode node) {
        String fullName = ((GroupNode)node.getUserObject()).getFullName();
        return groupsByFullName.get(fullName);
    }
}