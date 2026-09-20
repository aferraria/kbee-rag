package kbee.rag.text;

import java.util.List;

import kbee.rag.thesaurus.Concept;

public record BatchCandidateInput(
        int id,
        String text,
        List<Concept> candidateVoices
) {
}