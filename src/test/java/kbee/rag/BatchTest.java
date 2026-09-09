package kbee.rag;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kbee.rag.text.LegalEnhancement;
import kbee.rag.text.LegalTextEnhancer;

@SpringBootTest
public class BatchTest  {

    @Autowired
    private LegalTextEnhancer legalTextEnhancer;

    private static final String PROMPT =
            "legal-enrichment";

    @Test
    void compareIndividualVsBatch() {

        List<String> texts =
                List.of(
                        segment1(),
                        segment2(),
                        segment3(),
                        segment4()
                );

        /*
         * =================================================
         * WARMUP
         * =================================================
         */

        legalTextEnhancer
                .enhance(
                        texts.get(0),
                        PROMPT
                )
                .block();

        /*
         * =================================================
         * INDIVIDUAL
         * =================================================
         */

        System.out.println();
        System.out.println(
                "===== INDIVIDUAL ====="
        );

        long individualStart =
                System.nanoTime();

        List<LegalEnhancement> individualResults =
                texts.stream()
                        .map(text -> {

                            long start =
                                    System.nanoTime();

                            LegalEnhancement result =
                                    legalTextEnhancer
                                            .enhance(
                                                    text,
                                                    PROMPT
                                            )
                                            .block();

                            double elapsed =
                                    elapsedSeconds(
                                            start
                                    );

                            System.out.printf(
                                    "Tiempo: %.3f s%n",
                                    elapsed
                            );

                            printEnhancement(
                                    result
                            );

                            return result;
                        })
                        .toList();

        double individualSeconds =
                elapsedSeconds(
                        individualStart
                );

        /*
         * =================================================
         * BATCH
         * =================================================
         */

        System.out.println();
        System.out.println(
                "===== BATCH 4 ====="
        );

        long batchStart =
                System.nanoTime();

        List<LegalEnhancement> batchResults =
                legalTextEnhancer
                        .enhance(
                                texts,
                                PROMPT
                        )
                        .block();

        double batchSeconds =
                elapsedSeconds(
                        batchStart
                );

        for (int i = 0;
                i < batchResults.size();
                i++) {

            System.out.println();
            System.out.printf(
                    "--- SEGMENTO %d ---%n",
                    i + 1
            );

            printEnhancement(
                    batchResults.get(i)
            );
        }

        /*
         * =================================================
         * COMPARACIÓN
         * =================================================
         */

        System.out.println();
        System.out.println(
                "=========================================="
        );

        System.out.printf(
                "INDIVIDUAL: %.3f s%n",
                individualSeconds
        );

        System.out.printf(
                "BATCH 4:    %.3f s%n",
                batchSeconds
        );

        System.out.printf(
                "AHORRO:     %.3f s%n",
                individualSeconds
                        - batchSeconds
        );

        System.out.printf(
                "REDUCCION:  %.1f %%%n",
                (
                        individualSeconds
                                - batchSeconds
                )
                        / individualSeconds
                        * 100.0
        );

        System.out.printf(
                "SPEEDUP:    %.2fx%n",
                individualSeconds
                        / batchSeconds
        );

        System.out.println(
                "=========================================="
        );

        /*
         * =================================================
         * DETALLE POR SEGMENTO
         * =================================================
         */

        for (int i = 0;
                i < texts.size();
                i++) {

            System.out.println();
            System.out.printf(
                    "===== COMPARACION SEGMENTO %d =====%n",
                    i + 1
            );

            System.out.println(
                    "--- INDIVIDUAL ---"
            );

            printEnhancement(
                    individualResults.get(i)
            );

            System.out.println(
                    "--- BATCH ---"
            );

            printEnhancement(
                    batchResults.get(i)
            );
        }
    }

    private void printEnhancement(
            LegalEnhancement enhancement) {

        if (enhancement == null) {

            System.out.println(
                    "(null)"
            );

            return;
        }

        System.out.println(
                "VOCES:"
        );

        enhancement.concepts()
                .forEach(concept ->
                        System.out.println(
                                "  - "
                                        + concept.term()
                        )
                );

        System.out.println(
                "PROPOSICIONES:"
        );

        enhancement.propositions()
                .forEach(proposition ->
                        System.out.println(
                                "  - "
                                        + proposition
                        )
                );
    }

    private double elapsedSeconds(
            long start) {

        return (
                System.nanoTime()
                        - start
        )
                / 1_000_000_000.0;
    }

    /*
     * =================================================
     * SEGMENTOS REALES
     * =================================================
     */

    private String segment1() {

        return """
                En efecto, los reproches de la quejosa relativos a la desestimación
                de las declaraciones de los testigos Jorge Chirino y Pablo Sánchez
                en orden a acreditar la causal del distracto invocada por la
                empleadora -consistente en la marcación del reloj fichador por
                parte del actor a un compañero ausente-, se desvanecen frente a
                los argumentos esbozados por el A quo.

                En efecto, sobre ello la Sala expresó que el hecho de ser
                dependiente de la demandada no desvirtúa per se los dichos del
                testigo que ostenta esta calidad, sino que obliga a analizarlos
                con la mayor estrictez, máxime siendo uno de ellos el supervisor
                del actor.

                A ello agregó que lo relevante de este punto consistía en que
                los declarantes no resultaron testigos presenciales del hecho
                que se le imputó al trabajador, ya que nadie había visto a este
                último marcando la tarjeta, ni tampoco se había rendido prueba
                en autos de la filmación que fuera invocada por el testigo
                Sánchez, como único elemento objetivo para poder inculpar al actor.

                Tal como surge de la reseña que antecede, no lucen configurados
                los agravios de la recurrente, puesto que la prueba testimonial
                fue valorada por el Tribunal, aunque con un resultado distinto
                al pretendido por la quejosa y con un criterio que no llega a ser
                puesto en crisis desde la perspectiva constitucional.
                """;
    }

    private String segment2() {

        return """
                En ese contexto probatorio, el A quo, a la luz de las reglas del
                onus probandi, confirmó la decisión de anterior instancia de
                considerar no justificado el despido directo del actor atento
                la carencia de elementos probatorios de la causal invocada por
                la patronal.

                Por esas razones, convalidó la procedencia de las consecuencias
                indemnizatorias, remuneratorias y sancionatorias pertenecientes
                al distracto por resultar incausado.

                Frente a ello, la quejosa no alcanza a demostrar suficientemente
                un supuesto de arbitrariedad, en tanto sus cuestionamientos
                genéricos y globales sobre la invocada falta de fundamentación
                y el achacado análisis parcial y aislado de los elementos de
                juicio, se desvanecen al dejar incólumne el argumento central de
                la sentencia atacada; como así tampoco aporta otras razones de
                índole constitucional -con grado de decisividad- para que se
                deba imponer otro resultado en la causa necesariamente.
                """;
    }

    private String segment3() {

        return """
                Es de recordar, en punto a esta cuestión, que la valoración de
                la existencia o no de injuria como causal de despido, y, por
                ende, de las situaciones de hecho que rodean a esta evaluación,
                atañe únicamente a los jueces de la causa y que la decisión a la
                que se arribe excepcionalmente podrá ser objeto de este recurso
                extraordinario, excepcionalmente en los casos en los cuales se
                verifique que en tal tarea los sentenciantes no obraron con la
                prudencia que la ley laboral exige (artículo 242 ley de Contrato
                de Trabajo), lo que no ocurre en el caso.

                De similar modo, en cuanto a la decisión de la Sala de admitir
                diferencias salariales y la multa prevista en el artículo 1 de
                la ley 25323, en el marco de lo dispuesto en el artículo 29 de
                la Ley de Contrato de Trabajo, por considerar que resultaba
                aplicable al caso el Convenio Colectivo de Trabajo número 130/75
                y no el 254/75 del Sindicato Obrero de Recolección, Barrido y
                Limpieza (SORBYL), tampoco surgen configurados los agravios
                invocados por la recurrente.
                """;
    }

    private String segment4() {

        return """
                En relación a lo anterior, los reparos esgrimidos por la
                impugnante al respecto, si bien encasillados en distintas
                hipótesis de arbitrariedad, tales como: carecer de fundamento
                adecuado o aparente, no guardar relación con las constancias de
                autos, valoración arbitraria de los elementos probatorios,
                basarse en la voluntad del juzgador y dogmatismo, todos
                confluyen derechamente a revelar su simple disconformidad con la
                solución otorgada por los magistrados, respecto de cuestiones
                atinentes a la aplicación de normas derecho común, sin lograr
                persuadir a este Cuerpo que el razonamiento de la Sala luzca
                irrazonable en modo tal que lo haga pasible de descalificación
                constitucional.

                En efecto, para así resolver el Tribunal, analizando la situación
                planteada, tuvo en miras que el actor desempeñaba sus tareas de
                limpieza siempre en el centro comercial "El Portal" y en forma
                ininterrumpida, realizando las mismas labores que el A quo estimó
                imprescindibles para el normal funcionamiento del establecimiento,
                y en ese escenario, consideró aplicable al caso lo dispuesto en
                el artículo 29 del Régimen de Contrato de Trabajo.
                """;
    }
}