package kbee.rag.search;

import java.io.IOException;
import java.time.Instant;

import org.apache.solr.client.solrj.SolrServerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class VectorSearchExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleBadRequest(
            IllegalArgumentException exception) {

        return ResponseEntity
                .badRequest()
                .body(
                        new ApiError(
                                Instant.now(),
                                HttpStatus.BAD_REQUEST.value(),
                                exception.getMessage()
                        )
                );
    }

    @ExceptionHandler({
            SolrServerException.class,
            IOException.class
    })
    public ResponseEntity<ApiError> handleSolrError(
            Exception exception) {

        return ResponseEntity
                .status(HttpStatus.BAD_GATEWAY)
                .body(
                        new ApiError(
                                Instant.now(),
                                HttpStatus.BAD_GATEWAY.value(),
                                "No fue posible consultar Solr: "
                                + exception.getMessage()
                        )
                );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleInternalError(
            IllegalStateException exception) {

        return ResponseEntity
                .status(
                        HttpStatus.INTERNAL_SERVER_ERROR
                )
                .body(
                        new ApiError(
                                Instant.now(),
                                HttpStatus
                                        .INTERNAL_SERVER_ERROR
                                        .value(),
                                exception.getMessage()
                        )
                );
    }

    public record ApiError(
            Instant timestamp,
            int status,
            String message) {
    }
}