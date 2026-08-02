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
 * <p>The linked value uses Link's per-instance {@code label|url} form. The source id
 * remains separate because not every provider uses Wikidata QIDs.</p>
 */
public abstract class Source implements Viewable {

    @Hidden
    private final String kind;

    /** The external record id (e.g. a Wikidata QID) — the source's identity; kept visible so
     *  it survives snapshot round-trips and shows alongside the named link. */
    private final String sourceId;

    /** Canonical name used by this source for the linked record. */
    @Hidden
    private final String name;

    @Hidden
    private final String recordUrl;

    /** The provider-neutral visible link. It is presentation, not identity: code that needs
     *  the external key must use {@link #sourceId()} rather than parsing this value. */
    @Link
    private final String record;

    protected Source(String kind, String sourceId, String recordUrl) {
        this(kind, sourceId, recordUrl, "");
    }

    protected Source(String kind, String sourceId, String recordUrl, String name) {
        this.kind = kind == null ? "" : kind.strip();
        this.sourceId = sourceId == null ? "" : sourceId.strip();
        this.name = name == null ? "" : name.strip();
        this.recordUrl = recordUrl == null ? "" : recordUrl.strip();
        String label = this.name.isBlank() ? this.sourceId : this.name;
        this.record = label.isBlank() || this.recordUrl.isBlank()
                ? label : label + "|" + this.recordUrl;
    }

    public final String sourceId() { return sourceId; }

    public final String url() { return recordUrl; }
    public final String kind() { return kind; }
    public final String name() { return name; }

    @Override public final String getIdentifier() {
        return kind + "\u0000" + sourceId();
    }

    @Override public final String getDisplayName() {
        return name.isBlank() ? kind : name;
    }

    @Override public String typeName() { return "Source"; }

    @Override public final FieldSet fields() { return FieldSet.of(this); }

    @Override public String toString() {
        return kind + (sourceId().isBlank() ? "" : " (" + sourceId() + ")");
    }
}
