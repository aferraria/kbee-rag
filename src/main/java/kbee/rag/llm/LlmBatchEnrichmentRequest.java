package kbee.rag.llm;

import java.util.List;

import kbee.rag.text.TextEnhanced;

public interface LlmBatchEnrichmentRequest 
	extends LlmRequest<List<TextEnhanced>> {

}
