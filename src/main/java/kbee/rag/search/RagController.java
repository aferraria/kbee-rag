package kbee.rag.search;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kbee.rag.audit.Logger;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/rag")
public class RagController {


	
	static private Logger logger = Logger.getLogger(RagController.class.getName());

	private final RagService ragService;

	public RagController(RagService ragService) {

		this.ragService = ragService;
	}

//	@PostMapping("/sources")
//	public Mono<SourcesResponse> answer(@RequestBody RagRequest request) {
//
//		return ragService.rankSources(request);
//	}

	@PostMapping("/answer")
	public Mono<RagResponse> answer(
	        @RequestBody RagRequest request) {

	    System.out.println(
	            ">>> CONTROLLER /answer: " + request
	    );

	    return ragService
	            .answer(request)
	            .doOnSubscribe(subscription ->
	                    System.out.println(
	                            ">>> RAG SUBSCRIBED"
	                    )
	            )
	            .doOnNext(response ->
	                    System.out.println(
	                            ">>> RAG RESPONSE"
	                    )
	            )
	            .doOnError(error -> {
	                System.err.println(
	                        ">>> RAG ERROR: "
	                                + error.getMessage()
	                );
	                error.printStackTrace();
	            })
	            .doFinally(signal ->
	                    System.out.println(
	                            ">>> RAG FINALLY: " + signal
	                    )
	            );
	}

	@PostMapping("/document-analysis")
	public Mono<DocumentAnalysisResponse> analyzeDocument(@RequestBody DocumentAnalysisRequest request) {
	
		logger.debug("/document-analysis" + request.toString());
		 
		long startTime = System.currentTimeMillis();
		//Mono<DocumentAnalysisResponse> r=ragService.analyzeDocument(request.documentId(), request.question(), request.llm());
		logger.debug("round trip time for /document-analysis: " + (System.currentTimeMillis() - startTime) + " ms");
		
		
		return null;
		
	}
}