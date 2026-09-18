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
    

    @Value("${rag.prompts.rerank-final}")
    private String finalRerankPromptName;

    public RerankRequest build(
            String query,
            List<ExpandedSource> sources) {

        return new RerankRequest(
                instructionProvider.get(promptName),
                query,
                sources
        );
    }
    
    public RerankRequest buildFinal(
            String query,
            List<ExpandedSource> sources) {

        return new RerankRequest(
                instructionProvider.get(
                        finalRerankPromptName
                ),
                query,
                sources
        );
    }
}