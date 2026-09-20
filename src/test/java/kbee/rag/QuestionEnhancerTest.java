package kbee.rag;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kbee.rag.search.EnhancedQuestion;
import kbee.rag.search.QuestionEnhancer;

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
Precedente sobre si la Corte Suprema puede revisar una sentencia que atribuye negligencia probatoria al trabajador
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
- Precedente donde se realice una interpretación de la ley Ley 12183
                """;
        
        String q8 =

        		"""
Destaca que los honorarios en cuestión habían sido regulados mediante auto de fecha 19.08.2008 en 30,83 jus, equivalentes en aquel entonces a $4.523,08, determinándose el interés moratorio a una tasa del 12% anual; entiende que la denegación del reajuste peticionado prescinde del actual contexto inflacionario, a la vez que se aparta de lo normado en la ley arancelaria acerca del valor actualizado del jus, careciendo asimismo de toda conexión con el precio de subasta del inmueble involucrado en el proceso incidental de marras; todo ello -prosigue- con grave afectación de sus derechos fundamentales de propiedad y justa retribución, proporcional al esfuerzo desplegado y a los intereses económicos comprometidos.\n\nAlega que la decisión recurrida carece de motivación adecuada, tanto en punto al planteo de inconstitucionalidad de las normas que prohíben la actualización o indexación de deudas dinerarias, como en relación a la postulación acerca de la naturaleza del crédito por honorarios como obligación de valor y, asimismo, respecto de la aplicabilidad del artículo 8, inciso h), de la ley 6767, al igual que en torno al mantenimiento de la misma tasa de interés.

""";

        
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
                        .doOnError(error -> {
                            System.err.println(
                                    "ERROR: " + error.getMessage()
                            );
                            error.printStackTrace();
                        })
                        .block(); 
        
        long end = System.currentTimeMillis();
        
        System.out.println(enhanced);
        
    	System.out.println("Time: "+(end-start)/1000);

    }
}