package kbee.rag.reranker;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import kbee.rag.config.InstructionProvider;
import kbee.rag.search.ExpandedSource;

@Component
public class RerankRequestBuilder {

    @Value("${rag.prompts.rerank}")
    private String promptName;

    private final InstructionProvider instructionProvider;

    public RerankRequestBuilder(
            InstructionProvider instructionProvider) {
        this.instructionProvider = instructionProvider;
    }

    public RerankRequest build(
            String query,
            List<ExpandedSource> sources) {

        return build(null, query, sources);
    }

    public RerankRequest build(
            String llmProvider,
            String query,
            List<ExpandedSource> sources) {

        return new RerankRequest(
                instructionProvider.get(llmProvider, promptName),
                query,
                sources,
                llmProvider
        );
    }
}