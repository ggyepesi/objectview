package objectview.provenance;

import objectview.Viewable;
import objectview.annotations.Hidden;
import objectview.annotations.Link;
import objectview.field.FieldSet;

/**
 * Provenance attached to a Viewable. It is an ordinary nested Viewable field;
 * {@code @Provenance} on the owning field controls its compact presentation and
 * prevents it becoming a domain entity of its own.
 *
 * <p>The linked value uses Link's per-instance {@code label|url} form. Today the
 * canonical external identity is a Wikidata QID; a blank value represents a
 * manual/unresolved source.</p>
 */
public abstract class Source implements Viewable {

    @Hidden
    private final String kind;

    /** The visible source identity; rendered as a clickable link when present. */
    @Link
    private final String qid;

    @Hidden
    private final String recordUrl;

    protected Source(String kind, String sourceId, String recordUrl) {
        this.kind = kind == null ? "" : kind.strip();
        String id = sourceId == null ? "" : sourceId.strip();
        this.recordUrl = recordUrl == null ? "" : recordUrl.strip();
        this.qid = id.isBlank() || this.recordUrl.isBlank()
                ? id : id + "|" + this.recordUrl;
    }

    public final String sourceId() {
        int bar = qid.indexOf('|');
        return bar < 0 ? qid : qid.substring(0, bar).strip();
    }

    public final String url() { return recordUrl; }
    public final String kind() { return kind; }

    @Override public final String getIdentifier() {
        return kind + "\u0000" + sourceId();
    }

    @Override public final String getDisplayName() { return kind; }

    @Override public String typeName() { return "Source"; }

    @Override public final FieldSet fields() { return FieldSet.of(this); }

    @Override public String toString() {
        return kind + (sourceId().isBlank() ? "" : " (" + sourceId() + ")");
    }
}
