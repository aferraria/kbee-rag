package kbee.rag.reranker;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import kbee.rag.config.InstructionProvider;
import kbee.rag.search.ExpandedSource;

@Component
public class RerankRequestBuilderOld {

    @Value("${rag.prompts.rerank}")
    private String promptName;

    private final InstructionProvider instructionProvider;

    public RerankRequestBuilderOld(
            InstructionProvider instructionProvider) {
        this.instructionProvider = instructionProvider;
    }
    

    @Value("${rag.prompts.rerank-final}")
    private String finalRerankPromptName;

    public RerankRequestOld build(
            String query,
            List<ExpandedSource> sources) {

        return new RerankRequestOld(
                instructionProvider.get(promptName),
                query,
                sources
        );
    }
    
    public RerankRequestOld buildFinal(
            String query,
            List<ExpandedSource> sources) {

        return new RerankRequestOld(
                instructionProvider.get(
                        finalRerankPromptName
                ),
                query,
                sources
        );
    }
}