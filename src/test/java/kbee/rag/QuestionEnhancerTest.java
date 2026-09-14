package kbee.rag;


import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kbee.rag.event.QuestionEnhancedEvent;
import kbee.rag.search.EnhancedQuestion;
import kbee.rag.search.QuestionEnhancer;
import kbee.rag.segment.SegmentEnhancer;
import kbee.rag.segment.TextSegment;

@SpringBootTest
class QuestionEnhancerTest {


    private final QuestionEnhancer questionEnhancer;

    QuestionEnhancerTest(
            @Autowired
            QuestionEnhancer questionEnhancer) {

        this.questionEnhancer =
                questionEnhancer;

    }
    
    @Test
    void shouldExtractVoices() throws Exception {

 
        String q1 =
                """
        Precedente sobre requisitos que exige la Corte Suprema de Santa Fe para anular una renuncia a reclamar daños por vicios de la voluntad
                """;
        
        String q2 =
                """
        Precedente sobre responsabilidad del Estado provincial por inundaciones
                """;
        
        String q3 =
                """
        Antecedentes acerca de un policía que mató a ladrones en Rosario. Fue condenado por una filmación y luego la Corte revirtió la sentencia.
                """;
        
        String q4 =
                """
		La presente queja habrá de prosperar parcialmente.
                """;
        
        String q5 =
                """
        Precedente sobre si honorarios profesionales son obligación de valor u obligación de dinero una vez que la regulación queda firme"
                """;
        
        String q6 =
                """
Precedente sobre posibilidad de reajuste de regulación de honorarios profesionales
                """;
        
        
        String q7 =
                """
Precedente donde se realice una interpretación de la ley Ley 12183                """;

        
        String question = q7 		;
        
        long start = System.currentTimeMillis();
        
       EnhancedQuestion enhanced =
                questionEnhancer
                        .enhance(question)
                        .doOnNext(embeddingText -> {
                            System.out.println(
                                    embeddingText
                            );
                        })
                        .block(); 
        
        long end = System.currentTimeMillis();
        
        System.out.println(enhanced);
        
    	System.out.println("Time: "+(end-start)/1000);

    }
}