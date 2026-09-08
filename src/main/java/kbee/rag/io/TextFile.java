package kbee.rag.io;


import java.util.Map;
import java.util.Objects;


import java.time.OffsetDateTime;

public class TextFile {

    private final String id;

    private final String name;

    private final String title;

    private final String type;

    private final OffsetDateTime date;

    private final String text;

    private final Map<String, Object> metadata;

    public TextFile(
            String id,
            String name,
            String title,
            String type,
            OffsetDateTime date,
            String text,
            Map<String, Object> metadata) {

        this.id =
                Objects.requireNonNull(
                        id,
                        "id"
                );

        this.name =
                Objects.requireNonNull(
                        name,
                        "name"
                );

        this.title =
                title;

        this.type =
                Objects.requireNonNull(
                        type,
                        "type"
                );

        this.date =
                date;

        this.text =
                Objects.requireNonNull(
                        text,
                        "text"
                );

        this.metadata =
                metadata == null
                        ? Map.of()
                        : Map.copyOf(metadata);
    }

    public TextFile(
            String id,
            String name,
            String title,
            String type,
            OffsetDateTime date,
            String text) {

        this(
                id,
                name,
                title,
                type,
                date,
                text,
                Map.of()
        );
    }

    public String id() {

        return id;
    }

    public String name() {

        return name;
    }

    public String title() {

        return title;
    }

    public String type() {

        return type;
    }

    public OffsetDateTime date() {

        return date;
    }

    public String text() {

        return text;
    }

    public Map<String, Object> metadata() {

        return metadata;
    }

    public Object metadata(
            String property) {

        return metadata.get(
                property
        );
    }
}