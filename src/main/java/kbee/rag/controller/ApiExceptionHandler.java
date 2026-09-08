package kbee.rag.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import kbee.rag.model.ApiError;
import kbee.rag.solr.service.SolrQueryException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(SolrQueryException.class)
    public ResponseEntity<ApiError> solr(SolrQueryException e){return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ApiError("SOLR_QUERY_ERROR",e.getMessage()));}
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e){
        String m=e.getBindingResult().getFieldErrors().stream().findFirst().map(x->x.getField()+": "+x.getDefaultMessage()).orElse("Solicitud inválida");
        return ResponseEntity.badRequest().body(new ApiError("VALIDATION_ERROR",m));
    }
}
