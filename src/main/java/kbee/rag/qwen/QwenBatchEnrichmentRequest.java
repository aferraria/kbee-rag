package kbee.rag.qwen;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import kbee.rag.config.InstructionProvider;
import kbee.rag.llm.LlmBatchEnrichmentRequest;
import kbee.rag.llm.LlmRequestBuilder;
import kbee.rag.llm.LlmService;
import kbee.rag.text.BatchCandidateInput;
import kbee.rag.text.TextEnhanced;
import kbee.rag.thesaurus.Concept;
import reactor.core.publisher.Mono;


public class QwenBatchEnrichmentRequest
	extends AbstractQwenEnrichmentRequest<List<TextEnhanced>> 
	implements LlmBatchEnrichmentRequest {
	
    List<BatchCandidateInput> batch;
    
    private ObjectMapper objectMapper;

    private final LlmService llm;

    private final InstructionProvider instructionProvider;

    private QwenBatchEnrichmentRequest(
            LlmService llm,
            InstructionProvider instructionProvider,
            ObjectMapper objectMapper,
            List<BatchCandidateInput> batch) {

        this.llm =
                llm;

        this.instructionProvider =
                instructionProvider;
        
        this.objectMapper =
        		objectMapper;

        this.batch = batch;
    }


    public List<BatchCandidateInput> batch() {
        return batch;
    }

    public static Builder builder() {

        return new Builder();
    }

    @Override
    public String instructions() {

        return instructionProvider.get(
                "qwen",
                "text_batch_enrichment"
        );
    }

    @Override
    public String input() {
        return buildInput();
    }

    @Override
    public Map<String, Object> format() {
        return buildFormat();
    }


    @Override
    public Mono<List<TextEnhanced>> execute() {

        return llm.generate(this)
                .map(response -> {

                    BatchEvaluation evaluation =
                            parseBatchEvaluation(
                                    response
                            );

                    Map<Integer, BatchTextEvaluation> evaluationsById =
                            new LinkedHashMap<>();

                    if (evaluation.segments() != null) {

                        for (BatchTextEvaluation evaluationItem :
                                evaluation.segments()) {

                            if (evaluationItem == null) {
                                continue;
                            }

                            evaluationsById.putIfAbsent(
                                    evaluationItem.id(),
                                    evaluationItem
                            );
                        }
                    }

                    return batch.stream()
                            .map(input -> {

                                BatchTextEvaluation evaluationItem =
                                        evaluationsById.get(
                                                input.id()
                                        );

                                if (evaluationItem == null) {

                                    return new TextEnhanced(
                                            input.text(),
                                            List.of(),
                                            List.of()
                                    );
                                }

                                List<Concept> concepts =
                                        reconstructVoices(
                                                input.candidateVoices(),
                                                evaluationItem.voices()
                                        );

                                List<String> propositions =
                                        normalizePropositions(
                                                evaluationItem.propositions()
                                        );

                                return new TextEnhanced(
                                        input.text(),
                                        concepts,
                                        propositions
                                );
                            })
                            .toList();
                });
    }

    private String buildInput() {

        StringBuilder data =
                new StringBuilder();

        for (BatchCandidateInput input :
                batch) {

            if (input.text() == null
                    || input.text().isBlank()) {

                continue;
            }

            List<String> voices = buildLlmCandidates(input.candidateVoices());
            
            String voicesText =
                    voices.isEmpty()
                            ? "(sin voces)"
                            : String.join(
                                    "\n",
                                    voices
                            );

            data.append(
                    "=== SEGMENTO ===\n"
            );

            data.append(
                    "ID: "
            );

            data.append(
                    input.id()
            );

            data.append(
                    "\n\n"
            );

            data.append(
                    "=== TEXTO ===\n\n"
            );

            data.append(
                    input.text()
            );

            data.append(
                    "\n\n"
            );

            data.append(
                    "=== VOCES CANDIDATAS ===\n\n"
            );

            data.append(
                    voicesText
            );

            data.append(
                    "\n\n"
            );

            data.append(
                    "=== FIN SEGMENTO ===\n\n"
            );
        }

        return data
                .toString()
                .trim();
    }
    
    
   
    
    private Map<String, Object> buildFormat() {

        Map<String, Object> voicesSchema =
                new LinkedHashMap<>();

        voicesSchema.put(
                "type",
                "array"
        );

        voicesSchema.put(
                "items",
                Map.of(
                        "type",
                        "string"
                )
        );

        voicesSchema.put(
                "maxItems",
                60
        );

        Map<String, Object> propositionsSchema =
                new LinkedHashMap<>();

        propositionsSchema.put(
                "type",
                "array"
        );

        propositionsSchema.put(
                "items",
                Map.of(
                        "type",
                        "string"
                )
        );

        propositionsSchema.put(
                "maxItems",
                20
        );

        Map<String, Object> segmentProperties =
                new LinkedHashMap<>();

        segmentProperties.put(
                "id",
                Map.of(
                        "type",
                        "integer",
                        "minimum",
                        1,
                        "maximum",
                        batch.size()
                )
        );

        segmentProperties.put(
                "voices",
                voicesSchema
        );

        segmentProperties.put(
                "propositions",
                propositionsSchema
        );

        Map<String, Object> segmentSchema =
                new LinkedHashMap<>();

        segmentSchema.put(
                "type",
                "object"
        );

        segmentSchema.put(
                "properties",
                segmentProperties
        );

        segmentSchema.put(
                "required",
                List.of(
                        "id",
                        "voices",
                        "propositions"
                )
        );

        segmentSchema.put(
                "additionalProperties",
                false
        );

        Map<String, Object> segmentsSchema =
                new LinkedHashMap<>();

        segmentsSchema.put(
                "type",
                "array"
        );

        segmentsSchema.put(
                "items",
                segmentSchema
        );

        segmentsSchema.put(
                "maxItems",
                batch.size()
        );

        Map<String, Object> properties =
                new LinkedHashMap<>();

        properties.put(
                "segments",
                segmentsSchema
        );

        Map<String, Object> schema =
                new LinkedHashMap<>();

        schema.put(
                "type",
                "object"
        );

        schema.put(
                "properties",
                properties
        );

        schema.put(
                "required",
                List.of(
                        "segments"
                )
        );

        schema.put(
                "additionalProperties",
                false
        );

        return schema;
    }
   
    
    private BatchEvaluation parseBatchEvaluation(
            String response) {

        if (response == null
                || response.isBlank()) {

            return new BatchEvaluation(
                    List.of()
            );
        }

        String json =
                response.trim();

        try {

            return objectMapper.readValue(
                    json,
                    BatchEvaluation.class
            );

        } catch (Exception firstException) {

            System.err.println(
                    "===== INVALID BATCH LLM JSON ====="
            );

            System.err.println(
                    "length = "
                            + json.length()
            );

            System.err.println(
                    "tail = "
                            + json.substring(
                                    Math.max(
                                            0,
                                            json.length() - 1000
                                    )
                            )
            );

            String repaired =
                    repairJson(
                            json
                    );

            try {

                return objectMapper.readValue(
                        repaired,
                        BatchEvaluation.class
                );

            } catch (Exception secondException) {

                throw new IllegalStateException(
                        "No se pudo interpretar "
                                + "la respuesta batch del LLM: "
                                + response,
                        secondException
                );
            }
        }
    }
   


   
 
   
			    public static class Builder
			    implements LlmRequestBuilder<List<TextEnhanced>> {

			private InstructionProvider instructionProvider;
			
	        private ObjectMapper objectMapper;
			
			private List<BatchCandidateInput> batch;
			
			private LlmService llm;
			
			public Builder instructionProvider(
			        InstructionProvider instructionProvider) {
			
			    this.instructionProvider =
			            instructionProvider;
			
			    return this;
			}
			
	        public Builder objectMapper(
	                ObjectMapper objectMapper) {

	            this.objectMapper =
	                    objectMapper;

	            return this;
	        }
			
			@Override
			public Builder input(
			        Object input) {
			
			    if (input instanceof List<?> list) {
			
			        this.batch =
			                list.stream()
			                        .map(
			                                BatchCandidateInput.class::cast
			                        )
			                        .toList();
			
			        return this;
			    }
			
			    throw new IllegalArgumentException(
			            "Input no soportado: "
			                    + (input == null
			                            ? "null"
			                            : input.getClass().getName())
			    );
			}
			
			@Override
			public Builder llm(
			        LlmService llm) {
			
			    this.llm =
			            llm;
			
			    return this;
			}
			
			@Override
			public QwenBatchEnrichmentRequest build() {
			
			    return new QwenBatchEnrichmentRequest(
			            llm,
			            instructionProvider,
			            objectMapper,
			            batch
			    );
			}
			}
    

    private record BatchTextEvaluation(
            int id,
            List<String> voices,
            List<String> propositions
    ) {
    }

    private record BatchEvaluation(
            List<BatchTextEvaluation> segments
    ) {
    }


}