package objectview.demo;

import objectview.ViewableAdapter;
import objectview.render.CardListView;
import objectview.render.ExpandToolbar;
import objectview.render.RenderContext;
import objectview.search.SearchPanel;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates {@link SearchPanel} — live search, sort, and field highlight —
 * alongside the bulk expand/collapse {@link ExpandToolbar}.
 *
 * <p>A small set of cross-referenced films: each card shows its director as a
 * chip and its cast as a collapsible list of chips. Cards start as birdseye
 * summaries, so:
 * <ul>
 *   <li><b>Expand all</b> opens every card to its full fields,</li>
 *   <li><b>Expand all + Recursive</b> also drills into the director and cast
 *       references in place,</li>
 *   <li><b>Collapse all</b> returns to birdseye.</li>
 * </ul>
 * Type in the search box (try {@code "space"} or {@code "1994"}) and toggle
 * "Highlight Fields" to see matches lit up, including inside the long synopsis.
 */
public class SearchDemo {

    public static final class Person extends ViewableAdapter {
        public String name = "";
        public String bornIn = "";
        public String knownFor = "";

        public Person() {}

        public Person(String name, String bornIn, String knownFor) {
            this.name = name;
            this.bornIn = bornIn;
            this.knownFor = knownFor;
        }

        @Override public String getIdentifier()  { return name; }
        @Override public String getDisplayName() { return name; }
        @Override public String toString()       { return name; }
    }

    public static final class Film extends ViewableAdapter {
        public String title = "";
        public int year;
        public double rating;
        public String genre = "";
        public String synopsis = "";
        public Person director;
        public List<Person> cast = new ArrayList<>();

        public Film() {}

        @Override public String getIdentifier()  { return title; }
        @Override public String getDisplayName() { return title; }
        @Override public String toString()       { return title; }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(SearchDemo::launch);
    }

    private static void launch() {
        // A pool of people, shared across films so references cross-link.
        Person nolan   = new Person("Christopher Nolan", "London", "Inception");
        Person spielberg = new Person("Steven Spielberg", "Cincinnati", "Jaws");
        Person scott   = new Person("Ridley Scott", "South Shields", "Alien");
        Person villeneuve = new Person("Denis Villeneuve", "Quebec", "Arrival");
        Person kubrick = new Person("Stanley Kubrick", "New York", "2001: A Space Odyssey");

        Person hanks   = new Person("Tom Hanks", "Concord", "Forrest Gump");
        Person mcconaughey = new Person("Matthew McConaughey", "Uvalde", "Interstellar");
        Person weaver  = new Person("Sigourney Weaver", "New York", "Alien");
        Person adams   = new Person("Amy Adams", "Vicenza", "Arrival");
        Person bale    = new Person("Christian Bale", "Haverfordwest", "The Dark Knight");
        Person hardy   = new Person("Tom Hardy", "London", "Mad Max: Fury Road");
        Person chastain = new Person("Jessica Chastain", "Sacramento", "Zero Dark Thirty");

        List<Film> films = new ArrayList<>();
        films.add(film("Inception", 2010, 8.8, "Sci-Fi",
                "A thief who steals corporate secrets through dream-sharing "
                        + "technology is given the inverse task of planting an idea.",
                nolan, hardy, bale));
        films.add(film("Interstellar", 2014, 8.7, "Sci-Fi",
                "A team of explorers travel through a wormhole in space in an "
                        + "attempt to ensure humanity's survival.",
                nolan, mcconaughey, chastain));
        films.add(film("The Dark Knight", 2008, 9.0, "Action",
                "Batman raises the stakes in his war on crime with the help of "
                        + "allies against the anarchist Joker.",
                nolan, bale));
        films.add(film("Dunkirk", 2017, 7.8, "War",
                "Allied soldiers are surrounded and evacuated during a fierce "
                        + "battle in the Second World War.",
                nolan, hardy));
        films.add(film("Arrival", 2016, 7.9, "Sci-Fi",
                "A linguist works with the military to communicate with alien "
                        + "lifeforms after twelve spacecraft land worldwide.",
                villeneuve, adams));
        films.add(film("Blade Runner 2049", 2017, 8.0, "Sci-Fi",
                "A young blade runner discovers a long-buried secret that leads "
                        + "him to track down former blade runner Rick Deckard.",
                villeneuve, hardy));
        films.add(film("Alien", 1979, 8.5, "Horror",
                "The crew of a commercial spacecraft encounter a deadly "
                        + "extraterrestrial after investigating a distress call.",
                scott, weaver));
        films.add(film("The Martian", 2015, 8.0, "Sci-Fi",
                "An astronaut becomes stranded on Mars and must draw on ingenuity "
                        + "to survive and signal that he is alive.",
                scott, chastain));
        films.add(film("Gladiator", 2000, 8.5, "Action",
                "A betrayed Roman general seeks vengeance against the corrupt "
                        + "emperor who murdered his family and sent him into slavery.",
                scott, hardy));
        films.add(film("Saving Private Ryan", 1998, 8.6, "War",
                "Following the Normandy landings, a group of soldiers go behind "
                        + "enemy lines to retrieve a paratrooper.",
                spielberg, hanks));
        films.add(film("Catch Me If You Can", 2002, 8.1, "Drama",
                "A con man is chased by an FBI agent as he successfully forges "
                        + "checks worth millions while posing as a pilot and doctor.",
                spielberg, hanks));
        films.add(film("2001: A Space Odyssey", 1968, 8.3, "Sci-Fi",
                "After discovering a mysterious monolith, humanity sets off on a "
                        + "voyage to Jupiter with the sentient computer HAL 9000.",
                kubrick, weaver));
        films.add(film("The Shining", 1980, 8.4, "Horror",
                "A family heads to an isolated hotel where a sinister presence "
                        + "drives the father into violence as his son sees the future.",
                kubrick, bale));

        RenderContext context = new RenderContext();
        context.setInPlaceNavigation(true);
        context.setCollapsibleCards(true);          // birdseye cards → "expand all" opens them

        CardListView view = new CardListView();
        view.setRenderContext(context);
        for (Film f : films) {
            view.addViewable(f);
        }
        view.createCardsPanel(1);

        SearchPanel search = new SearchPanel(Film.class);
        search.setTarget(view.getCardsPanel(), view.getCardsScrollPane());
        search.setRenderContext(context);
        view.addTargetListener(search);

        ExpandToolbar expand = new ExpandToolbar(context, view::refreshBuiltCards);
        expand.setBorder(BorderFactory.createTitledBorder("Bulk expand / collapse"));

        JPanel top = new JPanel(new BorderLayout());
        top.add(search, BorderLayout.CENTER);
        top.add(expand, BorderLayout.SOUTH);

        JFrame frame = new JFrame("objectview — SearchDemo");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(top, BorderLayout.NORTH);
        frame.add(view.getCardsScrollPane(), BorderLayout.CENTER);
        frame.setSize(820, 760);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static Film film(String title, int year, double rating, String genre,
                             String synopsis, Person director, Person... cast) {
        Film f = new Film();
        f.title = title;
        f.year = year;
        f.rating = rating;
        f.genre = genre;
        f.synopsis = synopsis;
        f.director = director;
        for (Person p : cast) {
            f.cast.add(p);
        }
        return f;
    }
}
