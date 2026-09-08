package kbee.rag.search;

import java.util.List;

public record EnhancedQuestion(
        String text,
        String legalText,
        List<Concept> concepts,
        List<String> propositions
) {

    public EnhancedQuestion {

        concepts =
                concepts == null
                        ? List.of()
                        : List.copyOf(concepts);

        propositions =
                propositions == null
                        ? List.of()
                        : List.copyOf(propositions);
    }
}