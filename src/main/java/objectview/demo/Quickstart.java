package objectview.demo;

import objectview.ViewableAdapter;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.util.List;

/**
 * The 30-second objectview example: define a {@link objectview.Viewable}, hand a
 * few instances to a {@link MultiView}, and show them. A reference field renders
 * as a clickable chip that scrolls to and flashes the target's card.
 */
public class Quickstart {

    /**
     * Any class becomes renderable by implementing {@code Viewable} — here via the
     * reflection base {@link ViewableAdapter}, so its public fields become card
     * rows automatically.
     */
    public static final class Book extends ViewableAdapter {
        public String title;
        public String author;
        public int year;
        public Book sequelTo;          // a reference — renders as a navigable chip

        public Book(String title, String author, int year) {
            this.title = title;
            this.author = author;
            this.year = year;
        }

        @Override public String getIdentifier()  { return title; }
        @Override public String getDisplayName() { return title; }
        @Override public String toString()       { return title; }
    }

    public static void main(String[] args) {
        Book foundation = new Book("Foundation", "Isaac Asimov", 1951);
        Book empire = new Book("Foundation and Empire", "Isaac Asimov", 1952);
        empire.sequelTo = foundation;   // chip on Empire's card → scrolls to Foundation

        SwingUtilities.invokeLater(() -> {
            MultiView view = new MultiView();
            view.addSection("Books", Book.class, List.of(foundation, empire));
            view.build(1);

            JFrame frame = new JFrame("objectview — quickstart");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(view);
            frame.setSize(700, 500);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
