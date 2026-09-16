package kbee.rag;


import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kbee.rag.event.QuestionEnhancedEvent;
import kbee.rag.search.QuestionEnhancer;
import kbee.rag.segment.SegmentEnhancer;
import kbee.rag.segment.TextSegment;
import kbee.rag.text.LegalEnhancement;
import kbee.rag.text.LegalTextEnhancer;

@SpringBootTest
class TextEnhancerTest {


	private final SegmentEnhancer segmentEnhancer;

	private final LegalTextEnhancer legalTextEnhancer;

	TextEnhancerTest(
	        @Autowired SegmentEnhancer segmentEnhancer,
	        @Autowired LegalTextEnhancer legalTextEnhancer) {

	    this.segmentEnhancer =
	            segmentEnhancer;

	    this.legalTextEnhancer =
	            legalTextEnhancer;
	}
    
    @Test
    void shouldExtractVoices() throws Exception {

        String segmentText0 =
                """
                La defensa sostuvo que la condena se había basado
                principalmente en una filmación obtenida mediante
                cámaras de seguridad.

                La Corte consideró arbitraria la valoración
                fragmentada de dicha prueba y anuló la sentencia.
                """;
    	
        String segmentText1 =
                """
        		La presente queja habrá de prosperar parcialmente.
                """;
        
        String segmentText2 =
                """
Finalmente, con relación a los rubros indemnizatorios demandados, el Colegiado concedió por las consecuencias patrimoniales la suma de $185541,51 por el rubro \"gastos de reparación del inmueble\"; $20000 por la pérdida de bienes muebles; $390000 por el rubro \"desvalorización del inmueble\"; y por las consecuencias no patrimoniales, la suma de $80000 por cada uno de los demandantes."
                """;
        
        String segmentText3 =
                """
        Disconformes con este último pronunciamiento, tanto los actores como la demandada opusieron la pertinente queja ante la Sala Segunda de la Cámara de Apelación en lo Civil y Comercial de la ciudad de Santa Fe, que los juzgó mal denegados y, como consecuencia, los concedió.
        
                """;
        
        String segmentText4 =
                """
        		Para más, agregó que aquella conformidad incluso había sido exteriorizada en forma expresa por la parte al manifestar que cumpliría con lo allí requerido, lo que, a su vez, diera lugar a un nuevo decreto también consentido por aquélla. Añadió que la misma carga se había impuesto en la audiencia de trámite, sin que tampoco frente a ello hubiera habido ninguna diligencia ni actuación en consecuencia.\n\nConforme lo expuesto, la Sala juzgó que, dado el particular iter procesal antes referenciado, y tomando en cuenta tanto la conformidad tácita de la demandante como también su aceptación expresa con los decretos dictados que impusieran como requisito la notificación antes reseñada, entonces no podía entenderse que el Juez hubiera denegado el despacho de la prueba pericial sino que, por el contrario, su frustración había sido imputable tan sólo a la propia actora. Negligencia ésta última que, puso en evidencia el Tribunal, se había reiterado en la instancia apelatoria, oportunidad en la que tampoco había solicitado la apertura de la causa a prueba no obstante haber resultado perdidosa en la primigenia sentencia.
        		""";

        String segmentText5 =
                """
        		Menciona que la Suprema Corte de Justicia de la Provincia de Buenos Aires, en cambio, ha declarado la inconstitucionalidad sobreviniente del artículo 7 de la ley 23928, al juzgar que si bien la prohibición indexatoria había sido razonable en un contexto económico estable, la persistencia del criterio nominalista en el ambiente inflacionario actual devendría irrazonable y lesiva para los derechos de los acreedores; proclama que el criterio de ese Tribunal resulta trasladable al presente caso y que, por el contrario, el mantenimiento de las prohibiciones establecidas en los artículos 7 y 10 de la ley 23928, tal como fue resuelto por la Sala, traduce un desconocimiento de la realidad económica con grave afectación de sus derechos de raigambre constitucional.\n\nAfirma también que la Sala incurrió en incongruencia, por omisión de pronunciamiento respecto de la postulada aplicación al caso de lo normado en el artículo 8, inciso h), de la ley 6767; añade que el Tribunal no brindó fundamento alguno en orden a explicitar por qué el \"sub examine\" no sería subsumible en el citado precepto.
                """;
        
        String segmentText6 =
                """
        A lo que cabe agregar que la compareciente tampoco controvierte los demás argumentos desarrollados por la Alzada, ahora en oportunidad de examinar los agravios apelatorios, y en donde evidenció que los cuestionamientos, interpuestos mediante escrito de fojas 149/152, no alcanzaban a conmover lo decidido por el Juez de grado cuando señaló la absoluta carencia de prueba ninguna que demostrara la supuesta patología laboral que se denunciaba ni tampoco el grado de incapacidad.\n\nEn este marco, la Cámara añadió, como circunstancia llamativa, que en su demanda la accionante no había referido a incapacidad laboral permanente ninguna; que tampoco había acompañado ningún certificado médico que pudiera brindar algún aval a sus alegaciones; y que, finalmente, la única documentación trascendente acompañada no consistía más que en un formulario en donde, para más, se indicaba la ausencia de incapacidad.\n\nTales fundamentos, que también dieron sustento a lo resuelto por el Tribunal al confirmar la sentencia de grado, no son cuestionados por la recurrente, quien no efectúa siquiera referencias al respecto. Respuesta que, al no ser puesta en cuestión en el presente remedio extraordinario, se mantiene incólume.
                """;

        String segmentText7 =
                """
        T. 2025, SENTENCIA NRO. 52\n\n En la Provincia de Santa Fe, a los seis días del mes de marzo del año dos mil veinticinco, los señores Ministros de la Corte Suprema de Justicia de la Provincia, doctores Daniel Aníbal Erbetta, María Angélica Gastaldi, Rafael Francisco Gutiérrez y Eduardo Guillermo Spuler, con la Presidencia de su titular doctor Roberto Héctor Falistocco, acordaron dictar sentencia en los autos \"MALDONADO, JORGE ALBERTO Y OTROS contra PROVINCIA DE SANTA FE -DAÑOS Y PERJUICIOS- (CUIJ N° 21-12061123-9) sobre RECURSO DE INCONSTITUCIONALIDAD (CONCEDIDO POR LA CÁMARA)\" (Expte. C.S.J. CUIJ N°: 21-12061123-9). Se decidió someter a decisión las siguientes cuestiones: PRIMERA: ¿es admisible el recurso interpuesto?; SEGUNDA: en su caso, ¿es procedente?; y TERCERA: en consecuencia, ¿qué resolución corresponde dictar? Asimismo, se emitieron los votos en el orden que realizaron el estudio de la causa, o sea, doctores Spuler, Gutiérrez, Falistocco, Gastaldi y Erbetta.
                """;
        	
        String segmentText8 =
                """
        		En tal sentido, este Tribunal expuso que si bien el \"principio nominalista\" -que rige desde el año 1991 con la entrada en vigencia de la ley 23928, y que había sido 
        		reiteradamente convalidado por la Corte Suprema de Justicia de la Nación (Fallos: 329:385; 333:447; 339:1583)- veda la actualización o indexación de deudas dinerarias, 
        		el sistema diseñado por el legislador provincial mediante la ley 12851 -con la instauración, en el ámbito de la regulación de estipendios profesionales, de la unidad \"jus\",
        		 equivalente al 2% de la remuneración total (deducidos los adicionales porcentuales particulares) asignada al cargo de Juez de Primera Instancia de Distrito- constituye un mecanismo 
        		 indirecto de recomposición del capital correspondiente al crédito por honorarios, que se enmarca en la categoría de las \"obligaciones de valor\" y que, por tanto, queda al margen del 
        		 principio nominalista de la ley 23928 hasta su conversión en deuda de dinero, transformación que tendrá lugar al adquirir firmeza la regulación respectiva -momento en el cual, además, 
        		 se tornará exigible el pago de la obligación-; y que de allí en adelante, al entrar en funcionamiento la prohibición de los artículos 7 y 10 de la ley 23928, la integridad del crédito 
        		 pasará a ser resguardada mediante el interés a devengarse con la mora del deudor, con arreglo a lo normado en el mismo artículo 32 de la ley 6767 antes citado;
        		                """;
        
        String segmentText9 =
        """
        		        Con base en el relato efectuado precedentemente, se adelanta que merecen favorable acogida los 
        		        agravios vinculados con la arbitrariedad del pronunciamiento impugnado por rechazar la falta de acción a pesar de la renuncia 
        		        expresa formulada por los ahora actores.\n\n
        		        En efecto, teniendo en cuenta que se halla fuera de toda discusión que los accionantes recibieron el pago de la \"ayuda extraordinaria\" contemplada en el 
        		        régimen reparatorio especial establecido por ley 12183 (modif. por ley 12259), la cuestión planteada es sustancialmente análoga a la considerada y 
        		        resuelta por este Tribunal en los precedentes \"Villa\" y \"Ulrich\" (A. y S. nro. 82 y nro. 83, año 2024), a cuyos 
        		        fundamentos se remite en lo pertinente, y se dan aquí por reproducidos por razones de economía procesal.\n\nSentada en dichos 
        		        precedentes la disponibilidad de los derechos patrimoniales en juego, resta aquí añadir que en aquellos fallos esta Corte descartó que la vulnerabilidad, 
        		        urgencia o necesidad fueran fundamentos suficientes para declarar la inconstitucionalidad del artículo 7 de la ley 12183, y 
        		        expresó que tales circunstancias podrían a lo sumo ser ponderadas por los Sentenciantes, eventualmente, a fin de examinar la existencia de algún 
        		        vicio de la voluntad nulificante de los respectivos actos jurídicos de renuncia. 
        		        Se hizo especial énfasis en que una solución de ese tipo debía basarse en esfuerzos argumentales y 
        		        probatorios específicos que en tal sentido hubiese desplegado el accionante.
        """;
        
        
        String segmentText10 =
        """
        En la presente causa los hechos pueden reseñarse de la siguiente manera
        """;
        
        String segmentText11 =
        """
        La queja por denegación del recurso de inconstitucionalidad interpuesta por la actora contra la resolución N° 522 de fecha 4 de octubre de 2017, dictada por la Cámara de lo Contencioso Administrativo N° 1, en los autos caratulados "D'ANDREA, Juan Carlos contra PROVINCIA DE SANTA FE -Recurso Contencioso Administrativo- (Expte. 456/07)" (Expte. C.S.J. CUIJ: 21-00511603-9); y,
        """;

        String q3 =
                """
        Antecedentes acerca de un policía que mató a ladrones en Rosario. Fue condenado por una filmación y luego la Corte revirtió la sentencia.
                """;
        
        String text = segmentText9      		
        		;
        
        
        TextSegment segment =
                new TextSegment(
                        "fallo-test",
                        "FALLO DE PRUEBA",
                        OffsetDateTime.parse(
                                "2024-11-26T00:00:00-03:00"
                        ),
                        "CONSIDERANDO",
                        "CONSIDERANDO",
                        "CONSIDERANDO",
                        1,
                        1,
                        text,
                        text,
                        "fallo",
                        List.of(),
                        List.of(),
                        Map.of()
                );

  
        /*
         * ==========================================
         * INDIVIDUAL
         * ==========================================
         */

        long individualStart =
                System.currentTimeMillis();

        TextSegment individual =
                segmentEnhancer
                        .enhance(
                                segment
                        )
                        .block();

        long individualEnd =
                System.currentTimeMillis();

          /*
         * ==========================================
         * BATCH
         *
         * IMPORTANTE:
         * usamos EXACTAMENTE el mismo segmento.
         * ==========================================
         */

        long batchStart =
                System.currentTimeMillis();

        List<TextSegment> batch =
                segmentEnhancer
                        .enhance(
                                List.of(
                                        segment
                                )
                        )
                        .block();

        long batchEnd =
                System.currentTimeMillis();

  
        TextSegment batchSegment =
                batch.get(0);

        /*
         * ==========================================
         * RESULTADOS
         * ==========================================
         */

        System.out.println();
        System.out.println(
                "=========================================="
        );
        System.out.println(
                "INDIVIDUAL"
        );
        System.out.println(
                "=========================================="
        );

        System.out.println(
                individual
        );

        System.out.println();

        System.out.printf(
                "Individual time: %.3f s%n",
                (individualEnd - individualStart)
                        / 1000.0
        );

        System.out.println();
        System.out.println(
                "=========================================="
        );
        System.out.println(
                "BATCH"
        );
        System.out.println(
                "=========================================="
        );

        System.out.println(
                batchSegment
        );

        System.out.println();

        System.out.printf(
                "Batch time: %.3f s%n",
                (batchEnd - batchStart)
                        / 1000.0
        );

      
    }
    
    //@Test
    void shouldEnhanceFourSegmentsIndependently() {
    	
    	List<String> texts = List.of(

    	        """
    	        T. 2025, SENTENCIA NRO. 450

    	        Provincia de Santa Fe, 29 de julio del año 2025.
    	        """,

    	        """
    	        La queja por denegación del recurso de inconstitucionalidad interpuesto
    	        por el abogado Norberto Francisco José Berlanga contra el auto número
    	        233 de fecha 15 de octubre de 2024, dictado por la Sala Primera
    	        -integrada- de la Cámara de Apelación en lo Civil y Comercial de la
    	        ciudad de Santa Fe, en autos "VERONESE, CLAUDIA contra RECORD
    	        PUBLICISTAS S.R.L. Y OTROS -INCID DE INOP DE INSC BIEN FAM
    	        (CUIJ 21-00834525-9)" (Expte. C.S.J. CUIJ Nº: 21-00516437-8); y,
    	        """,

    	        """
    	        Mediante resolución 233 del 15 de octubre de 2024, la Sala Primera
    	        integrada de la Cámara de Apelación en lo Civil y Comercial de Santa Fe
    	        rechazó el recurso de reposición deducido por el doctor Berlanga contra
    	        la providencia de fecha 06.08.2024 dictada por el Vocal de trámite
    	        -quien, a su turno, había desestimado la petición del letrado orientada
    	        al reajuste de los honorarios regulados por la actuación profesional
    	        desarrollada en la segunda instancia de un incidente concursal-.

    	        Contra tal pronunciamiento interpone el curial recurso de
    	        inconstitucionalidad, con invocación de las causales previstas en el
    	        artículo 1 -incisos 2° y 3°- de la ley 7055, tachándolo de arbitrario,
    	        contrario a la Constitución y a la ley arancelaria, y carente de
    	        motivación suficiente.

    	        En fundamentación del recurso impetrado, reseña que los presentes se
    	        originaron a partir de su solicitud, formulada ante el Tribunal de
    	        Alzada, orientada al reajuste de los honorarios regulados por su labor
    	        profesional desplegada en el marco de un incidente concursal en segunda
    	        instancia, el cual tenía por objeto -recuerda- la declaración de
    	        inoponibilidad de la inscripción como bien de familia de un inmueble de
    	        la fallida, en orden a posibilitar su ulterior subasta en el trámite de
    	        quiebra liquidativa; remarca que aquella petición se sustentaba en lo
    	        establecido en los artículos 8, inciso h), y 32 de la ley 6737
    	        -y sus modificatorias-.
    	        """,

    	        """
    	        Destaca que los honorarios en cuestión habían sido regulados mediante
    	        auto de fecha 19.08.2008 en 30,83 jus, equivalentes en aquel entonces a
    	        $4.523,08, determinándose el interés moratorio a una tasa del 12% anual;
    	        entiende que la denegación del reajuste peticionado prescinde del actual
    	        contexto inflacionario, a la vez que se aparta de lo normado en la ley
    	        arancelaria acerca del valor actualizado del jus, careciendo asimismo
    	        de toda conexión con el precio de subasta del inmueble involucrado en
    	        el proceso incidental de marras; todo ello -prosigue- con grave
    	        afectación de sus derechos fundamentales de propiedad y justa
    	        retribución, proporcional al esfuerzo desplegado y a los intereses
    	        económicos comprometidos.

    	        Alega que la decisión recurrida carece de motivación adecuada, tanto en
    	        punto al planteo de inconstitucionalidad de las normas que prohíben la
    	        actualización o indexación de deudas dinerarias, como en relación a la
    	        postulación acerca de la naturaleza del crédito por honorarios como
    	        obligación de valor y, asimismo, respecto de la aplicabilidad del
    	        artículo 8, inciso h), de la ley 6767, al igual que en torno al
    	        mantenimiento de la misma tasa de interés.
    	        """
    	);
    	

        List<String> texts2 = List.of(

                // ID 1
                """
                La presente queja habrá de prosperar parcialmente.
                """,

                // ID 2
                """
                Liminarmente corresponde señalar que si bien el presentante dice
                encuadrar su impugnación en los incisos 2° y 3° del artículo 1
                de la ley 7055, lo concreto es que no se observa que en el caso
                se encuentre cuestionada la inteligencia de un precepto de la
                Constitución en sí mismo, pues todos sus planteos giran en torno
                a la alegada arbitrariedad del fallo de la Sala y su supuesta
                incompatibilidad con los derechos y garantías fundamentales que
                se afirman vulnerados, resultando por tanto subsumibles en la
                hipótesis prevista en el inciso 3° de la norma citada, bajo cuya
                óptica corresponde que sean analizados.

                A su vez, debe recordarse que el memorial del recurso de
                inconstitucionalidad no es susceptible de ser mejorado ni ampliado
                en la queja, la cual debe fundarse en relación a la motivación del
                auto denegatorio.
                """,

                // ID 3
                """
                Sentado lo anterior, es posible advertir que la mayoría de las
                causales de descalificación propuestas en el memorial recursivo,
                de entre las cuestiones que oportunamente se plantearon y
                mantuvieron en autos, no pueden trasponer el umbral de
                admisibilidad de la vía establecida en la ley 7055, en tanto no
                logran superar el nivel de la simple discrepancia respecto del
                resultado de un debate en torno a la interpretación y alcance de
                normas arancelarias, quedando comprendidas dentro del amplio
                margen que en la materia se confiere a la razonable prudencia
                de los jueces de la causa.
                """,

                // ID 4
                """
                En efecto, en lo tocante al achaque de falta de fundamentación
                enderezado contra la denegación del reajuste de honorarios
                pretendido en los términos del artículo 32 de la ley 6737,
                en vinculación con el rechazo del planteo de inconstitucionalidad
                de los artículos 7 y 10 de la ley 23928 y con las postulaciones
                en torno a la naturaleza de la obligación, se advierte que lo
                decidido por la Sala aparece expresamente sustentado en la
                doctrina sentada por este Cuerpo.

                Así, en el citado precedente de esta Corte se abordó la cuestión
                de la validez constitucional de la denominada "unidad jus"
                prevista en el artículo 32 de la ley 6767 en confrontación con
                la prohibición de indexar obligaciones dinerarias dispuesta por
                ley 23928, propiciándose una razonable compatibilidad de las
                normas en juego.
                """
        );

        List<LegalEnhancement> result =
                legalTextEnhancer
                        .enhance(
                                texts,
                                "segment-batch-enrichment"
                        )
                        .block();

        assertNotNull(result);
        assertEquals(4, result.size());

        result.forEach(enhancement -> {
            assertNotNull(enhancement);
            assertNotNull(enhancement.concepts());
            assertNotNull(enhancement.propositions());
        });

        System.out.println();
        System.out.println("===== BATCH RESULT =====");

        for (int i = 0; i < result.size(); i++) {

            LegalEnhancement enhancement =
                    result.get(i);

            System.out.println();
            System.out.println(
                    "===== SEGMENT " + (i + 1) + " ====="
            );

            System.out.println("Concepts:");

            enhancement.concepts()
                    .forEach(System.out::println);

            System.out.println("Propositions:");

            enhancement.propositions()
                    .forEach(System.out::println);
        }
    }

    

}
