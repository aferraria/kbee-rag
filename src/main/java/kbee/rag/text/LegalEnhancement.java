package kbee.rag.text;

import java.util.List;

import kbee.rag.search.Concept;

public record LegalEnhancement(
        String text,
        List<Concept> concepts,
        List<String> propositions
) {

    public LegalEnhancement {

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