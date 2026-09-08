package kbee.rag.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import kbee.rag.model.SolrQueryRequest;
import kbee.rag.model.SolrQueryResponse;
import kbee.rag.solr.service.SolrQueryService;

@RestController
@RequestMapping("/api/solr")
public class SolrQueryController {
    private final SolrQueryService service;
    public SolrQueryController(SolrQueryService service){this.service=service;}
    
    @PostMapping(value="/query",consumes=MediaType.APPLICATION_JSON_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public SolrQueryResponse query(@Valid @RequestBody SolrQueryRequest request){
    	return service.query(request);
    }
    
    @PostMapping(value="/raw-query",consumes=MediaType.TEXT_PLAIN_VALUE,produces=MediaType.APPLICATION_JSON_VALUE)
    public SolrQueryResponse rawQuery(@RequestBody String query){return service.rawQuery(query);}
}
