package objectview.render;

import objectview.demo.CardFrame;
import objectview.Viewable;
import objectview.ViewableAdapter;
import objectview.annotations.ListField;
import objectview.viewconfig.ViewConfig;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ListView extends JPanel {

    private final Map<String, ? extends Viewable> viewables;
    private final List<String> keys = new ArrayList<>();
    private final JList<String> list;

    public ListView(Map<String, ? extends Viewable> viewables,
                    Iterable<String> memberKeys) {
        this.viewables = viewables;

        for (String key : memberKeys) {
            if (key != null && viewables.get(key) != null) {
                keys.add(key);
            }
        }

        setLayout(new BorderLayout());

        list = new JList<>(keys.toArray(new String[0]));
        list.setCellRenderer(new ListRenderer());
        list.setFixedCellHeight(48);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    openSelected();
                }
            }
        });

        add(new JScrollPane(list), BorderLayout.CENTER);
    }

    private void openSelected() {
        String key = list.getSelectedValue();
        if (key == null) {
            return;
        }

        Viewable q = viewables.get(key);
        if (q == null) {
            return;
        }

        new CardFrame(
                q,
                ViewConfig.allWithMinorFields(q.getClass())
                          .setAddListener(true)
                          .setThumb(true));
    }

    private class ListRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel title = new JLabel();
        private final JLabel summary = new JLabel();

        ListRenderer() {
            setLayout(new BorderLayout(6, 2));
            setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
            setOpaque(true);

            title.setFont(title.getFont().deriveFont(Font.BOLD));
            summary.setForeground(Color.GRAY);

            add(title, BorderLayout.CENTER);
            add(summary, BorderLayout.SOUTH);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list,
                                                      String key,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            Viewable q = viewables.get(key);

            title.setText(q == null ? key : safeName(q));
            summary.setText(q == null ? "" : buildSummary(q));

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                title.setForeground(list.getSelectionForeground());
                summary.setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                title.setForeground(list.getForeground());
                summary.setForeground(Color.GRAY);
            }

            return this;
        }
    }

    private String buildSummary(Viewable q) {
        try {
            List<Field> fields = getListFields(q.getClass());
            List<String> parts = new ArrayList<>();

            for (Field f : fields) {
                Object value = f.get(q);
                if (value == null) {
                    continue;
                }

                String text = value instanceof Viewable qq
                        ? qq.getName()
                        : String.valueOf(value);

                if (text == null || text.isBlank()) {
                    continue;
                }

                ListField lf = f.getAnnotation(ListField.class);
                String title = lf.title();

                if (title == null || title.isBlank()) {
                    title = f.getName();
                }

                parts.add(title + ": " + text);
            }

            return String.join("   |   ", parts);
        } catch (Exception e) {
            return "";
        }
    }

    private List<Field> getListFields(Class<?> cls) {
        List<Field> fields = new ArrayList<>();

        for (Field f : ViewableAdapter.getAllFields(cls)) {
            if (f.isAnnotationPresent(ListField.class)) {
                f.setAccessible(true);
                fields.add(f);
            }
        }

        fields.sort(Comparator.comparingInt(
                f -> f.getAnnotation(ListField.class).order()));

        return fields;
    }

    private String safeName(Viewable q) {
        String name = q.getName();
        return name == null || name.isBlank()
                ? String.valueOf(q)
                : name;
    }
}