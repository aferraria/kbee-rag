package kbee.rag;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.convert.request.ConvertDocumentRequest;
import ai.docling.serve.api.convert.request.options.ConvertDocumentOptions;
import ai.docling.serve.api.convert.request.options.OutputFormat;
import ai.docling.serve.api.convert.request.source.FileSource;
import ai.docling.serve.api.convert.request.target.InBodyTarget;
import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import ai.docling.serve.api.DoclingServeApi;
import ai.docling.serve.api.health.HealthCheckResponse;

public class DoclingServeTest {

    private static final String DOCLING_URL = "http://localhost:5001";

    @Test
    void shouldConnectToDocling() {

        DoclingServeApi api = DoclingServeApi.builder()
                .baseUrl(DOCLING_URL)
                .logRequests()
                .logResponses()
                .build();

        HealthCheckResponse health = api.health();

        System.out.println("Docling health: " + health);

        assertNotNull(health);
        assertNotNull(health.getStatus());
    }
    


    @Test
    void shouldConvertPdfToMarkdown() throws Exception {

        DoclingServeApi api = DoclingServeApi.builder()
                .baseUrl(DOCLING_URL)
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofMinutes(2))
                .asyncPollInterval(Duration.ofSeconds(5))
                .asyncTimeout(Duration.ofMinutes(180))
                .logRequests()
                .logResponses()
                .prettyPrint()
                .build();

        Path pdf = Path.of("src/test/resources/doc.pdf");

        assertTrue(Files.exists(pdf),
                "No existe el PDF: " + pdf.toAbsolutePath());

        byte[] pdfBytes = Files.readAllBytes(pdf);

        String base64 = Base64.getEncoder()
                .encodeToString(pdfBytes);

        FileSource source = FileSource.builder()
                .filename(pdf.getFileName().toString())
                .base64String(base64)
                .build();

        ConvertDocumentRequest request =
                ConvertDocumentRequest.builder()
                        .source(source)
                        .options(
                                ConvertDocumentOptions.builder()
                                        .toFormat(OutputFormat.MARKDOWN)
                                        .build()
                        )
                        .target(InBodyTarget.builder().build())
                        .build();

        long start = System.currentTimeMillis();

        ConvertDocumentResponse genericResponse =
                api.convertSourceAsync(request)
                        .toCompletableFuture()
                        .get(60, TimeUnit.MINUTES);

        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(genericResponse);

        InBodyConvertDocumentResponse response =
                (InBodyConvertDocumentResponse) genericResponse;

        assertNotNull(response.getDocument());

        String markdown =
                response.getDocument().getMarkdownContent();

        assertNotNull(markdown);
        assertFalse(markdown.isBlank());

        System.out.println("===== MARKDOWN =====");
        System.out.println(markdown);

        System.out.println();
        System.out.println("===== STATUS =====");
        System.out.println(response.getStatus());

        System.out.println();
        System.out.println("===== PROCESSING TIME =====");
        System.out.println(response.getProcessingTime());

        System.out.println();
        System.out.println("===== TOTAL JAVA TIME =====");
        System.out.println(elapsed / 1000.0 + " segundos");
    }
    }