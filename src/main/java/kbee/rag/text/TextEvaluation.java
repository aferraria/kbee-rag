package kbee.rag.text;

import java.util.List;

public record TextEvaluation(
        List<String> voices,
        List<String> propositions
) {
}