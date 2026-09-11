package kbee.rag.search;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(
            RagService ragService) {

        this.ragService =
                ragService;
    }

    @PostMapping("/sources")
    public Mono<SourcesResponse> answer(
            @RequestBody RagRequest request) {

        return ragService.rankSources(
                request
        );
    }
    
    
    @PostMapping("/answer")
    public Mono<RagResponse> rerank(
            @RequestBody RagRequest request) {

        return ragService.answer(
                request
        );
    }
    
//    @PostMapping("/search")
//    public ResponseEntity<List<SegmentSearchResult>> search(
//            @RequestBody RagRequest request)
//            throws SolrServerException, IOException {
//
//        return ResponseEntity.ok(
//                ragService.search(request)
//        );
//    }
    
    @PostMapping("/document-analysis")
    public Mono<DocumentAnalysisResponse> analyzeDocument(
            @RequestBody DocumentAnalysisRequest request) {

        return ragService.analyzeDocument(
                request.documentId(),
                request.question()
        );
    }
}