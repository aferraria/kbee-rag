package kbee.rag.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kbee.rag.audit.Logger;
import kbee.rag.search.DocumentAnalysisRequest;
import kbee.rag.search.DocumentAnalysisResponse;
import kbee.rag.search.QueryAnalysis;
import kbee.rag.search.QueryAnalysisRequest;
import kbee.rag.search.RagRequest;
import kbee.rag.search.RagResponse;
import kbee.rag.search.RagService;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/rag")
public class RagController extends RagBaseController {

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
	public Mono<RagResponse> answer(@RequestBody RagRequest request) {

		logger.debug(">>> CONTROLLER /answer: " + request);

		return ragService.answer(request).doOnSubscribe(subscription -> 
			logger.debug(">>> RAG SUBSCRIBED"))
				.doOnNext(response ->logger.error(">>> RAG RESPONSE"))
				.doOnError(error -> {
										logger.error(">>> RAG ERROR: " + error.getMessage());
										error.printStackTrace();
									})
				.doFinally(signal -> logger.debug(">>> RAG FINALLY: " + signal));
	}

	@PostMapping("/document-analysis")
	public Mono<DocumentAnalysisResponse> analyzeDocument(@RequestBody DocumentAnalysisRequest request) {

		logger.debug("/document-analysis" + request.toString());

		// Mono<DocumentAnalysisResponse>
		return ragService.analyze(request);
		// request.llm());

		//return null;

	}

	@PostMapping("/queryanalysis")
	public Mono<QueryAnalysis> queryAnalysis(@RequestBody QueryAnalysisRequest request) {

		logger.debug("/queryanalysis " + request.toString());

		return ragService.queryAnalysis(request);
	}
}