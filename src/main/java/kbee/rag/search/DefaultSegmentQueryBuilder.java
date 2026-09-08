package kbee.rag.search;

import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

@Service
public class DefaultSegmentQueryBuilder
        implements SegmentQueryBuilder {

    private final QuestionEnhancer questionEnhancer;

    public DefaultSegmentQueryBuilder(
            QuestionEnhancer questionEnhancer) {

        this.questionEnhancer =
                questionEnhancer;
    }

    @Override
    public Mono<ExtendedSegmentSearchRequest> build(
            SegmentSearchRequest request) {

        return questionEnhancer
                .enhance(
                        request.query()
                )
                .map(enhancedQuestion ->
                        new ExtendedSegmentSearchRequest(
                                request,
                                enhancedQuestion.legalText(),
                                enhancedQuestion.concepts(),
                                enhancedQuestion.propositions()
                        )
                );
    }
}