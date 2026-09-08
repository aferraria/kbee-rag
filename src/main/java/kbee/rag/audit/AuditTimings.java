package kbee.rag.audit;

import java.time.Duration;

public record AuditTimings(

        Duration total,
        Duration questionEnhancement,
        Duration search,
        Duration sourceExpansion,
        Duration rerank,
        Duration answerGeneration

) {}