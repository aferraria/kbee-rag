package kbee.rag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kbee.rag.config.InstructionProvider;
import kbee.rag.llm.LlmRequest;
import kbee.rag.llm.LlmService;

@SpringBootTest
public class LegalTextEnhancerBatchExperimentTest {

    @Autowired
    private LlmService llmService;

    @Autowired
    private InstructionProvider instructionProvider;

    @Test
    void testBatchConceptAssignment() {

        String instructions =
                instructionProvider.get(
                        "legal-enrichment"
                );

        String data = """
                === SEGMENTO 1 ===

                TEXTO:
                La Corte consideró arbitraria la valoración
                fragmentada de la filmación y anuló la sentencia.

                TERMINOS:
                SENTENCIA
                ARBITRARIEDAD
                ARBITRARIEDAD PROBATORIA
                VIDEOFILMACION
                CONDENADO


                === SEGMENTO 2 ===

                TEXTO:
                El imputado fue condenado por el delito de robo.

                TERMINOS:
                PROCESO PENAL
                CONDENADO
                ROBO
                ARBITRARIEDAD
                VIDEOFILMACION
                """;

        LlmRequest request =
                new LlmRequest(
                        instructions,
                        data,
                        buildBatchFormat()
                );

        String response =
                llmService.generate(request)
                        .block();

        System.out.println(
                response
        );
    }
    
    
    @Test
    void testBatchConceptAssignment4Segments() {

        String instructions =
                instructionProvider.get(
                        "legal-enrichment"
                );

        String data = """
                === SEGMENTO ===
                ID: 1

                === TEXTO ===
                La Corte consideró arbitraria la valoración
                fragmentada de la filmación y anuló la sentencia.

                === TERMINOS ===
                SENTENCIA
                ARBITRARIEDAD
                ARBITRARIEDAD PROBATORIA
                VIDEOFILMACION
                CONDENADO
                ROBO
                LEGITIMACION

                === FIN SEGMENTO ===


                === SEGMENTO ===
                ID: 2

                === TEXTO ===
                El imputado fue condenado por el delito de robo.

                === TERMINOS ===
                SENTENCIA
                ARBITRARIEDAD
                VIDEOFILMACION
                CONDENADO
                ROBO
                LEGITIMACION

                === FIN SEGMENTO ===


                === SEGMENTO ===
                ID: 3

                === TEXTO ===
                La demanda fue rechazada porque el actor
                carecía de legitimación activa para promoverla.

                === TERMINOS ===
                SENTENCIA
                ARBITRARIEDAD
                VIDEOFILMACION
                CONDENADO
                ROBO
                LEGITIMACION

                === FIN SEGMENTO ===


                === SEGMENTO ===
                ID: 4

                === TEXTO ===
                La cámara confirmó la sentencia absolutoria
                al considerar insuficiente la prueba producida
                para acreditar la responsabilidad penal
                del acusado.

                === TERMINOS ===
                SENTENCIA
                SENTENCIA ABSOLUTORIA
                PRUEBA
                RESPONSABILIDAD PENAL
                ARBITRARIEDAD
                VIDEOFILMACION
                CONDENADO
                ROBO
                LEGITIMACION

                === FIN SEGMENTO ===
                """;

        LlmRequest request =
                new LlmRequest(
                        instructions,
                        data,
                        buildBatchFormat()
                );

        long start =
                System.nanoTime();

        String response =
                llmService.generate(
                        request
                )
                .block();

        double elapsedSeconds =
                (
                        System.nanoTime()
                                - start
                )
                        / 1_000_000_000.0;

        System.out.println();
        System.out.println(
                "===== BATCH 4 ====="
        );

        System.out.printf(
                "Tiempo: %.3f segundos%n",
                elapsedSeconds
        );

        System.out.println();
        System.out.println(
                response
        );
    }
    
    @Test
    void testBatchConceptAssignment4SegmentsSingleTermList() {

        String instructions =
                instructionProvider.get(
                        "legal-enrichment"
                );

        String data = """
                === TERMINOS CANDIDATOS ===

                SENTENCIA
                ARBITRARIEDAD
                ARBITRARIEDAD PROBATORIA
                VIDEOFILMACION
                CONDENADO
                ROBO
                LEGITIMACION
                SENTENCIA ABSOLUTORIA
                PRUEBA
                RESPONSABILIDAD PENAL


                === SEGMENTO ===
                ID: 1

                === TEXTO ===
                La Corte consideró arbitraria la valoración
                fragmentada de la filmación y anuló la sentencia.

                === FIN SEGMENTO ===


                === SEGMENTO ===
                ID: 2

                === TEXTO ===
                El imputado fue condenado por el delito de robo.

                === FIN SEGMENTO ===


                === SEGMENTO ===
                ID: 3

                === TEXTO ===
                La demanda fue rechazada porque el actor
                carecía de legitimación activa para promoverla.

                === FIN SEGMENTO ===


                === SEGMENTO ===
                ID: 4

                === TEXTO ===
                La cámara confirmó la sentencia absolutoria
                al considerar insuficiente la prueba producida
                para acreditar la responsabilidad penal
                del acusado.

                === FIN SEGMENTO ===
                """;

        LlmRequest request =
                new LlmRequest(
                        instructions,
                        data,
                        buildBatchFormat()
                );

        long start =
                System.nanoTime();

        String response =
                llmService.generate(
                        request
                )
                .block();

        double elapsedSeconds =
                (
                        System.nanoTime()
                                - start
                )
                        / 1_000_000_000.0;

        System.out.println();
        System.out.println(
                "===== BATCH 4 - SINGLE TERM LIST ====="
        );

        System.out.printf(
                "Tiempo: %.3f segundos%n",
                elapsedSeconds
        );

        System.out.println();
        System.out.println(
                response
        );

        System.out.println();
        System.out.println(
                "======================================"
        );

        System.out.printf(
                "INDIVIDUAL 4:          %.3f segundos%n",
                6.545
        );

        System.out.printf(
                "BATCH 4 SEPARADO:      %.3f segundos%n",
                4.970
        );

        System.out.printf(
                "BATCH 4 LISTA UNICA:   %.3f segundos%n",
                elapsedSeconds
        );

        System.out.printf(
                "SPEEDUP VS INDIVIDUAL: %.2fx%n",
                6.545 / elapsedSeconds
        );

        System.out.println(
                "======================================"
        );
    }
   
    
    @Test
    void testBatch4RealSegmentsSingleTermList() {

        String instructions =
                instructionProvider.get(
                        "legal-enrichment"
                );

        String data = """
                === TERMINOS CANDIDATOS ===

                TESTIGO
                PRUEBA TESTIMONIAL
                TESTIGO UNICO
                DECLARACION
                EMPLEADOR
                EMPLEADO
                CUESTION DE HECHO
                SENTENCIA
                FUNDAMENTOS INSUFICIENTES
                INDEMNIZACION POR DESPIDO INCAUSADO
                CONVENIO COLECTIVO DE TRABAJO
                NORMA MAS FAVORABLE
                SENTENCIA NO FIRME
                LEY LABORAL
                DEBER DE OBRAR CON PRUDENCIA
                PRINCIPIOS LABORALES
                ARBITRARIEDAD
                SENTENCIA ARBITRARIA
                SENTENCIA IRRAZONABLE
                SINDICATO
                MATERIA LABORAL
                CONTROL DE LOGICIDAD
                RECURSO EXTRAORDINARIO
                ADMISIBILIDAD
                FRAUDE LABORAL
                SOLIDARIDAD LABORAL
                CONTRATO DE TRABAJO
                LEY APLICABLE
                CENTRO COMERCIAL
                TRIBUNAL
                DERECHO DEL TRABAJO


                === SEGMENTO ===
                ID: 1

                === TEXTO ===

                En efecto, los reproches de la quejosa relativos a la
                desestimación de las declaraciones de los testigos Jorge
                Chirino y Pablo Sánchez en orden a acreditar la causal del
                distracto invocada por la empleadora -consistente en la
                marcación del reloj fichador por parte del actor a un
                compañero ausente-, se desvanecen frente a los argumentos
                esbozados por el A quo.

                En efecto, sobre ello la Sala expresó que el hecho de ser
                dependiente de la demandada no desvirtúa per se los dichos
                del testigo que ostenta esta calidad, sino que obliga a
                analizarlos con la mayor estrictez, máxime siendo uno de
                ellos el supervisor del actor.

                A ello agregó que lo relevante de este punto consistía en
                que los declarantes no resultaron testigos presenciales del
                hecho que se le imputó al trabajador, ya que nadie había
                visto a este último marcando la tarjeta, ni tampoco se había
                rendido prueba en autos de la filmación que fuera invocada
                por el testigo Sánchez, como único elemento objetivo para
                poder inculpar al actor.

                Tal como surge de la reseña que antecede, no lucen
                configurados los agravios de la recurrente, puesto que la
                prueba testimonial fue valorada por el Tribunal, aunque con
                un resultado distinto al pretendido por la quejosa y con un
                criterio que no llega a ser puesto en crisis desde la
                perspectiva constitucional.

                === FIN SEGMENTO ===


                === SEGMENTO ===
                ID: 2

                === TEXTO ===

                En ese contexto probatorio, el A quo, a la luz de las reglas
                del onus probandi, confirmó la decisión de anterior instancia
                de considerar no justificado el despido directo del actor
                atento la carencia de elementos probatorios de la causal
                invocada por la patronal.

                Por esas razones, convalidó la procedencia de las
                consecuencias indemnizatorias, remuneratorias y
                sancionatorias pertenecientes al distracto por resultar
                incausado.

                Frente a ello, la quejosa no alcanza a demostrar
                suficientemente un supuesto de arbitrariedad, en tanto sus
                cuestionamientos genéricos y globales sobre la invocada falta
                de fundamentación y el achacado análisis parcial y aislado de
                los elementos de juicio, se desvanecen al dejar incólumne el
                argumento central de la sentencia atacada; como así tampoco
                aporta otras razones de índole constitucional -con grado de
                decisividad- para que se deba imponer otro resultado en la
                causa necesariamente.

                === FIN SEGMENTO ===


                === SEGMENTO ===
                ID: 3

                === TEXTO ===

                Es de recordar, en punto a esta cuestión, que la valoración
                de la existencia o no de injuria como causal de despido, y,
                por ende, de las situaciones de hecho que rodean a esta
                evaluación, atañe únicamente a los jueces de la causa y que
                la decisión a la que se arribe excepcionalmente podrá ser
                objeto de este recurso extraordinario, excepcionalmente en
                los casos en los cuales se verifique que en tal tarea los
                sentenciantes no obraron con la prudencia que la ley laboral
                exige (artículo 242 ley de Contrato de Trabajo), lo que no
                ocurre en el caso.

                De similar modo, en cuanto a la decisión de la Sala de
                admitir diferencias salariales y la multa prevista en el
                artículo 1 de la ley 25323, en el marco de lo dispuesto en el
                artículo 29 de la Ley de Contrato de Trabajo, por considerar
                que resultaba aplicable al caso el Convenio Colectivo de
                Trabajo número 130/75 y no el 254/75 del Sindicato Obrero de
                Recolección, Barrido y Limpieza (SORBYL), tampoco surgen
                configurados los agravios invocados por la recurrente.

                === FIN SEGMENTO ===


                === SEGMENTO ===
                ID: 4

                === TEXTO ===

                En relación a lo anterior, los reparos esgrimidos por la
                impugnante al respecto, si bien encasillados en distintas
                hipótesis de arbitrariedad, tales como: carecer de fundamento
                adecuado o aparente, no guardar relación con las constancias
                de autos, valoración arbitraria de los elementos probatorios,
                basarse en la voluntad del juzgador y dogmatismo, todos
                confluyen derechamente a revelar su simple disconformidad con
                la solución otorgada por los magistrados, respecto de
                cuestiones atinentes a la aplicación de normas derecho común,
                sin lograr persuadir a este Cuerpo que el razonamiento de la
                Sala luzca irrazonable en modo tal que lo haga pasible de
                descalificación constitucional.

                En efecto, para así resolver el Tribunal, analizando la
                situación planteada, tuvo en miras que el actor desempeñaba
                sus tareas de limpieza siempre en el centro comercial
                "El Portal" y en forma ininterrumpida, realizando las mismas
                labores que el A quo estimó imprescindibles para el normal
                funcionamiento del establecimiento, y en ese escenario,
                consideró aplicable al caso lo dispuesto en el artículo 29
                del Régimen de Contrato de Trabajo.

                === FIN SEGMENTO ===
                """;

        LlmRequest request =
                new LlmRequest(
                        instructions,
                        data,
                        buildBatchFormat()
                );

        long start =
                System.nanoTime();

        String response =
                llmService.generate(
                        request
                )
                .block();

        double elapsedSeconds =
                (
                        System.nanoTime()
                                - start
                )
                        / 1_000_000_000.0;

        System.out.println();
        System.out.println(
                "===== BATCH 4 REAL - SINGLE TERM LIST ====="
        );

        System.out.printf(
                "Tiempo: %.3f segundos%n",
                elapsedSeconds
        );

        System.out.println();
        System.out.println(
                response
        );

        System.out.println();
        System.out.println(
                "=========================================="
        );
    }
    
    @Test
    void test4RealSegmentsIndividual() {

        String instructions =
                instructionProvider.get(
                        "legal-enrichment"
                );

        List<String> requests =
                List.of(

                        /*
                         * =========================================
                         * SEGMENTO 1 - fallo-50037:7
                         * =========================================
                         */
                        """
                        === TERMINOS CANDIDATOS ===

                        TESTIGO
                        PRUEBA TESTIMONIAL
                        TESTIGO UNICO
                        DECLARACION
                        EMPLEADOR
                        EMPLEADO

                        === SEGMENTO ===
                        ID: 1

                        === TEXTO ===

                        En efecto, los reproches de la quejosa relativos a la
                        desestimación de las declaraciones de los testigos Jorge
                        Chirino y Pablo Sánchez en orden a acreditar la causal del
                        distracto invocada por la empleadora -consistente en la
                        marcación del reloj fichador por parte del actor a un
                        compañero ausente-, se desvanecen frente a los argumentos
                        esbozados por el A quo.

                        En efecto, sobre ello la Sala expresó que el hecho de ser
                        dependiente de la demandada no desvirtúa per se los dichos
                        del testigo que ostenta esta calidad, sino que obliga a
                        analizarlos con la mayor estrictez, máxime siendo uno de
                        ellos el supervisor del actor.

                        A ello agregó que lo relevante de este punto consistía en
                        que los declarantes no resultaron testigos presenciales del
                        hecho que se le imputó al trabajador, ya que nadie había
                        visto a este último marcando la tarjeta, ni tampoco se había
                        rendido prueba en autos de la filmación que fuera invocada
                        por el testigo Sánchez, como único elemento objetivo para
                        poder inculpar al actor.

                        Tal como surge de la reseña que antecede, no lucen
                        configurados los agravios de la recurrente, puesto que la
                        prueba testimonial fue valorada por el Tribunal, aunque con
                        un resultado distinto al pretendido por la quejosa y con un
                        criterio que no llega a ser puesto en crisis desde la
                        perspectiva constitucional.

                        === FIN SEGMENTO ===
                        """,

                        /*
                         * =========================================
                         * SEGMENTO 2 - fallo-50037:8
                         * =========================================
                         */
                        """
                        === TERMINOS CANDIDATOS ===

                        CUESTION DE HECHO
                        SENTENCIA
                        FUNDAMENTOS INSUFICIENTES
                        INDEMNIZACION POR DESPIDO INCAUSADO

                        === SEGMENTO ===
                        ID: 2

                        === TEXTO ===

                        En ese contexto probatorio, el A quo, a la luz de las reglas
                        del onus probandi, confirmó la decisión de anterior instancia
                        de considerar no justificado el despido directo del actor
                        atento la carencia de elementos probatorios de la causal
                        invocada por la patronal.

                        Por esas razones, convalidó la procedencia de las
                        consecuencias indemnizatorias, remuneratorias y
                        sancionatorias pertenecientes al distracto por resultar
                        incausado.

                        Frente a ello, la quejosa no alcanza a demostrar
                        suficientemente un supuesto de arbitrariedad, en tanto sus
                        cuestionamientos genéricos y globales sobre la invocada falta
                        de fundamentación y el achacado análisis parcial y aislado de
                        los elementos de juicio, se desvanecen al dejar incólumne el
                        argumento central de la sentencia atacada; como así tampoco
                        aporta otras razones de índole constitucional -con grado de
                        decisividad- para que se deba imponer otro resultado en la
                        causa necesariamente.

                        === FIN SEGMENTO ===
                        """,

                        /*
                         * =========================================
                         * SEGMENTO 3 - fallo-50037:9
                         * =========================================
                         */
                        """
                        === TERMINOS CANDIDATOS ===

                        CONVENIO COLECTIVO DE TRABAJO
                        NORMA MAS FAVORABLE
                        SENTENCIA NO FIRME
                        LEY LABORAL
                        DEBER DE OBRAR CON PRUDENCIA
                        PRINCIPIOS LABORALES
                        ARBITRARIEDAD
                        SENTENCIA ARBITRARIA
                        SENTENCIA IRRAZONABLE
                        SINDICATO
                        MATERIA LABORAL
                        CONTROL DE LOGICIDAD
                        RECURSO EXTRAORDINARIO
                        ADMISIBILIDAD
                        FRAUDE LABORAL
                        SOLIDARIDAD LABORAL
                        CONTRATO DE TRABAJO
                        SENTENCIA
                        LEY APLICABLE

                        === SEGMENTO ===
                        ID: 3

                        === TEXTO ===

                        Es de recordar, en punto a esta cuestión, que la valoración
                        de la existencia o no de injuria como causal de despido, y,
                        por ende, de las situaciones de hecho que rodean a esta
                        evaluación, atañe únicamente a los jueces de la causa y que
                        la decisión a la que se arribe excepcionalmente podrá ser
                        objeto de este recurso extraordinario, excepcionalmente en
                        los casos en los cuales se verifique que en tal tarea los
                        sentenciantes no obraron con la prudencia que la ley laboral
                        exige (artículo 242 ley de Contrato de Trabajo), lo que no
                        ocurre en el caso.

                        De similar modo, en cuanto a la decisión de la Sala de
                        admitir diferencias salariales y la multa prevista en el
                        artículo 1 de la ley 25323, en el marco de lo dispuesto en el
                        artículo 29 de la Ley de Contrato de Trabajo, por considerar
                        que resultaba aplicable al caso el Convenio Colectivo de
                        Trabajo número 130/75 y no el 254/75 del Sindicato Obrero de
                        Recolección, Barrido y Limpieza (SORBYL), tampoco surgen
                        configurados los agravios invocados por la recurrente.

                        === FIN SEGMENTO ===
                        """,

                        /*
                         * =========================================
                         * SEGMENTO 4 - fallo-50037:10
                         * =========================================
                         */
                        """
                        === TERMINOS CANDIDATOS ===

                        CONTRATO DE TRABAJO
                        CENTRO COMERCIAL
                        TRIBUNAL
                        DERECHO DEL TRABAJO

                        === SEGMENTO ===
                        ID: 4

                        === TEXTO ===

                        En relación a lo anterior, los reparos esgrimidos por la
                        impugnante al respecto, si bien encasillados en distintas
                        hipótesis de arbitrariedad, tales como: carecer de fundamento
                        adecuado o aparente, no guardar relación con las constancias
                        de autos, valoración arbitraria de los elementos probatorios,
                        basarse en la voluntad del juzgador y dogmatismo, todos
                        confluyen derechamente a revelar su simple disconformidad con
                        la solución otorgada por los magistrados, respecto de
                        cuestiones atinentes a la aplicación de normas derecho común,
                        sin lograr persuadir a este Cuerpo que el razonamiento de la
                        Sala luzca irrazonable en modo tal que lo haga pasible de
                        descalificación constitucional.

                        En efecto, para así resolver el Tribunal, analizando la
                        situación planteada, tuvo en miras que el actor desempeñaba
                        sus tareas de limpieza siempre en el centro comercial
                        "El Portal" y en forma ininterrumpida, realizando las mismas
                        labores que el A quo estimó imprescindibles para el normal
                        funcionamiento del establecimiento, y en ese escenario,
                        consideró aplicable al caso lo dispuesto en el artículo 29
                        del Régimen de Contrato de Trabajo.

                        === FIN SEGMENTO ===
                        """
                );

        long totalStart =
                System.nanoTime();

        for (int i = 0;
                i < requests.size();
                i++) {

            LlmRequest request =
                    new LlmRequest(
                            instructions,
                            requests.get(i),
                            buildBatchFormat()
                    );

            long start =
                    System.nanoTime();

            String response =
                    llmService.generate(
                            request
                    )
                    .block();

            double elapsedSeconds =
                    (
                            System.nanoTime()
                                    - start
                    )
                            / 1_000_000_000.0;

            System.out.println();
            System.out.printf(
                    "===== REAL SEGMENTO %d =====%n",
                    i + 1
            );

            System.out.printf(
                    "Tiempo: %.3f segundos%n",
                    elapsedSeconds
            );

            System.out.println();
            System.out.println(
                    response
            );
        }

        double totalSeconds =
                (
                        System.nanoTime()
                                - totalStart
                )
                        / 1_000_000_000.0;

        System.out.println();
        System.out.println(
                "=========================================="
        );

        System.out.printf(
                "TOTAL INDIVIDUAL REAL: %.3f segundos%n",
                totalSeconds
        );

        System.out.printf(
                "BATCH 4 LISTA UNICA:   %.3f segundos%n",
                11.242
        );

        System.out.printf(
                "SPEEDUP:                %.2fx%n",
                totalSeconds / 11.242
        );

        System.out.println(
                "=========================================="
        );
    }
    
    
    private Map<String, Object> buildBatchFormat() {

        /*
         * =================================================
         * TERMINOS
         * =================================================
         */

        Map<String, Object> termsSchema =
                new LinkedHashMap<>();

        termsSchema.put(
                "type",
                "array"
        );

        termsSchema.put(
                "items",
                Map.of(
                        "type",
                        "string"
                )
        );

        /*
         * =================================================
         * PROPOSITIONS
         * =================================================
         */

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

        /*
         * =================================================
         * SEGMENT
         * =================================================
         */

        Map<String, Object> segmentProperties =
                new LinkedHashMap<>();

        segmentProperties.put(
                "id",
                Map.of(
                        "type",
                        "integer"
                )
        );

        segmentProperties.put(
                "terminos",
                termsSchema
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
                        "terminos",
                        "propositions"
                )
        );

        segmentSchema.put(
                "additionalProperties",
                false
        );

        /*
         * =================================================
         * SEGMENTS
         * =================================================
         */

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

        /*
         * =================================================
         * ROOT
         * =================================================
         */

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
}