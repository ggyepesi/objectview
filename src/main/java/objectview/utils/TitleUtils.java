package objectview.utils;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TitleUtils {

    public static List<String> getAncestorTitles(JComponent comp) {
        List<String> titles = new ArrayList<>();

        Container parent = comp.getParent();
        while (parent != null) {
            String title = null;
            if (parent instanceof JFrame) {
                title = ((JFrame) parent).getTitle();
            } else if (parent instanceof JDialog) {
                title = ((JDialog) parent).getTitle();
            } else if (parent instanceof JPanel) {
                Border border = ((JPanel) parent).getBorder();
                if (border instanceof TitledBorder) {
                    title = ((TitledBorder) border).getTitle();
                }
            }
            if (title != null) {
                titles.add(title);
            }
            parent = parent.getParent();
        }

        Collections.reverse(titles);
        return titles;
    }
}