package kbee.rag;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import kbee.rag.io.ApiFile;
import kbee.rag.io.JudicialFileParser;
import kbee.rag.io.PathFile;
import kbee.rag.io.TextFile;

class JudicialFileParserTest {

    @Test
    void parsePathFile() {

        Path path =
                Path.of(
                        "C:\\Home\\Alejo\\eclipse\\worskapce-cloud\\kbee-solr-api\\fallos\\fallo-53348.txt"
                );

        ApiFile file =
                new PathFile(
                        path
                );

        JudicialFileParser parser =
                new JudicialFileParser();

        List<TextFile> files =
                parser.parse(file)
                        .collectList()
                        .block();

        System.out.println();
        System.out.println(
                "===== RESULTADO ====="
        );

        for (TextFile textFile : files) {

            System.out.println();
            System.out.println(
                    "----------------------------------------"
            );

            System.out.println(
                    "NAME: "
                            + textFile.name()
            );

            System.out.println(
                    "METADATA:"
            );

            textFile.metadata()
                    .forEach(
                            (property, value) ->
                                    System.out.println(
                                            "  "
                                                    + property
                                                    + " = "
                                                    + value
                                    )
                    );

            System.out.println();
            System.out.println(
                    "TEXT:"
            );

            System.out.println(
                    textFile.text()
            );
        }
    }
}