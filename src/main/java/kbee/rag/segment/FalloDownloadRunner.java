package kbee.rag.segment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;


@Component
@ConditionalOnProperty(
        name = "fallo-download.enabled",
        havingValue = "true"
)
public class FalloDownloadRunner
        implements CommandLineRunner {

    private static final String BASE_URL =
            "https://portal.justiciasantafe.gov.ar/bdj/index.php"
            + "?pg=bus&m=busqueda&c=busqueda&a=get&id=";

    private static final int MAX_ID = 80000;

    private final HttpClient httpClient =
            HttpClient.newBuilder()
                    .followRedirects(
                            HttpClient.Redirect.NORMAL
                    )
                    .build();

    @Override
    public void run(String... args)
            throws Exception {

        Path directory =
                Path.of("fallos");

        Files.createDirectories(directory);

        for (int id = 6720; id <= MAX_ID; id++) {

            try {

                download(id, directory);
                Thread.currentThread().sleep(Duration.ofMillis(100));

            } catch (Exception e) {

                System.err.println(
                        "Error id="
                                + id
                                + ": "
                                + e.getMessage()
                );

                // seguimos con el próximo
            }
        }

        System.out.println("Proceso terminado.");
    }

    private void download(
            int id,
            Path directory)
            throws Exception {

        String url =
                BASE_URL + id;

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header(
                                "User-Agent",
                                "Mozilla/5.0"
                        )
                        .GET()
                        .build();

        HttpResponse<String> response =
                httpClient.send(
                        request,
                        HttpResponse.BodyHandlers.ofString(
                                StandardCharsets.UTF_8
                        )
                );

        if (response.statusCode() != 200) {

            System.out.println(
                    id + " -> HTTP "
                            + response.statusCode()
            );

            return;
        }

        Document document =
                Jsoup.parse(
                        response.body()
                );

        String text =
                document.body()
                        .wholeText()
                        .trim();

        if (!isValidFallo(text)) {

            System.out.println(
                    id + " -> no existe"
            );

            return;
        }

        Path output =
                directory.resolve(
                        "fallo-" + id + ".txt"
                );

        Files.writeString(
                output,
                text,
                StandardCharsets.UTF_8
        );

        System.out.println(
                id + " -> OK"
        );
    }

    private boolean isValidFallo(
            String text) {

        if (text == null
                || text.isBlank()) {

            return false;
        }

        /*
         * Por ahora una validación mínima.
         * Después podemos ajustarla viendo qué
         * devuelve el sitio para un ID inexistente.
         */
        return text.length() > 100;
    }
}