package kbee.rag;


import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kbee.rag.event.QuestionEnhancedEvent;
import kbee.rag.search.QuestionEnhancer;
import kbee.rag.segment.SegmentEnhancer;
import kbee.rag.segment.TextSegment;

@SpringBootTest
class TextEnhancerTest {


    private final SegmentEnhancer segmentEnhancer;

    TextEnhancerTest(
            @Autowired
            SegmentEnhancer segmentEnhancer) {

        this.segmentEnhancer =
                segmentEnhancer;
        

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
        		Antecedentes acerca de un policía que mató a ladrones en Rosario. Fue condenado por una filmación y luego la Corte revirtió la sentencia.
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
        
T. 2025, SENTENCIA NRO. 330

En la Provincia de Santa Fe, a los once días del mes de junio del año dos mil veinticinco, los señores Ministros de la Corte Suprema de Justicia de la Provincia, doctores Daniel Aníbal Erbetta, Rafael Francisco Gutiérrez, Eduardo Guillermo Spuler y Margarita Elsa Zabalza, con la presidencia de su titular doctor Roberto Héctor Falistocco, acordaron dictar sentencia en los autos caratulados "SBRISSA, Daniel Antonio contra PROVINCIA DE SANTA FE -RCA- (CUIJ 21-17455462-2) sobre RECURSO DE INCONSTITUCIONALIDAD (PARCIALMENTE CONCEDIDO POR LA CÁMARA)" (Expte. C.S.J. CUIJ N° 21-17455462-2). Se resolvió someter a decisión las siguientes cuestiones: PRIMERA: ¿es admisible el recurso interpuesto? SEGUNDA: en su caso, ¿es procedente? TERCERA: en consecuencia, ¿qué resolución corresponde dictar? Asimismo, se emitieron los votos en el orden que realizaron el estudio de la causa, o sea doctores Gutiérrez, Spuler, Erbetta, Falistocco y Zabalza.
Segment :I.1. Surge de las constancias de autos que Daniel Antonio Sbrissa interpuso recurso contencioso administrativo contra la Provincia de Santa Fe tendente a obtener que se disponga la nulidad del decreto 3316/17; y que, en su lugar, se ordene a la Caja provincial que efectúe el reajuste de su haber previsional debido a la ausencia de razonable proporcionalidad con el sueldo de un agente en actividad, abonándosele el retroactivo generado desde dos años previos al reclamo administrativo; con más intereses y costas.

Expuso -en síntesis- que prestó servicios en la órbita del Ministerio de Seguridad de la Provincia durante 25 años, y 5 años en el ámbito nacional, todos ellos computables a los fines jubilatorios; que obtuvo el beneficio de jubilación ordinaria en el año 2006; que el 28.8.2013 presentó un reclamo administrativo con el fin de obtener el reajuste de su haber por no guardar razonable proporcionalidad con el sueldo de un agente en actividad; y que tal reclamo fue finalmente rechazado a través del decreto que impugna.

Dijo que su haber jubilatorio "no acompañó los aumentos de sueldos que fueron otorgados a las personas en actividad, con el mismo cargo y antigüedad que el que poseía, desvirtuándose la garantía de movilidad que debe otorgarse a los beneficios de la Seguridad Social, según el mandato constitucional" .

Rechazó, asimismo, la quita efectuada en su haber jubilatorio, con base en el precedente "Lagger" de esta Corte.
Segment :Por último, sostuvo que para establecer las diferencias con el sueldo de un agente en actividad deben computarse todos los rubros que liquida la Provincia de Santa Fe.

b. Al contestar la demanda, la Provincia de Santa Fe argumentó -en suma- que "no hay hasta el momento elemento probatorio alguno del cual surja que la aplicación del sistema de movilización de los haberes de pasividad haya provocado durante el período que reclama el Sr. Sbrissa una irrazonable desproporción entre los dos términos que se invocan en la comparación para arribar a la conclusión de que se verifica la desproporción que torna confiscatorio el haber".

Señaló que en el sistema de la ley 6915 ni la evolución del cargo desempeñado por el pasivo al momento de obtener la jubilación, ni la remuneración del cargo luego del cese, son pautas para lograr el reajuste de su haber, sino que ello ocurre a partir de la aplicación de los coeficientes sectoriales que fija el Poder Ejecutivo en función de las variaciones de las remuneraciones del personal en actividad, y sólo si su utilización produce una irrazonable desproporción puede admitirse limitadamente el reajuste, lo que no se encuentra probado en autos.

Añadió que si la implementación de este sistema legal genera afectaciones de índole constitucional, la teoría de la razonable proporcionalidad brinda el sistema de clausura para recomponer los haberes de pasividad que hayan resultado ilegítimamente afectados por la aplicación del mecanismo en cuestión.
                """;
        
        String segmentText9 =
        """
        Mediante resolución N° 62 de fecha 5 de junio de 2024 (f. 1165) la Sala Segunda de la Cámara de Apelación en lo Civil y Comercial de Santa Fe declaró admisible el recurso de inconstitucionalidad deducido por la Provincia de Santa Fe contra la sentencia N° 72 emitida por dicho Tribunal en fecha 3 de mayo de 2023 (fs. 1099/1118).

        El nuevo examen de admisibilidad -que corresponde a esta Corte efectuar por imperio del artículo 11 de la ley 7055, con los principales a la vista-, me conduce a ratificar esa conclusión, de conformidad con lo dictaminado por el señor Procurador General Subrogante (fs. 1176). 

        Voto, pues, por la afirmativa.

        A la misma cuestión, el señor Ministro doctor Gutiérrez, el señor Presidente doctor Falistocco, la señora Ministra doctora Gastaldi y el señor Ministro doctor Erbetta expresaron idénticos fundamentos a los vertidos por el señor Ministro doctor Spuler y votaron en igual sentido.
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
        
        String text = q3;      		
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
                        List.of()
                );

        
        long start = System.currentTimeMillis();
        
        TextSegment enhanced=
                segmentEnhancer
                        .enhance(segment)
                        .doOnNext(embeddingText -> {
                            System.out.println(
                                    embeddingText
                            );
                        })
                        .block(); 
        
        long end = System.currentTimeMillis();
        
        System.out.println(text);
        
    	System.out.println("Time: "+(end-start)/1000);

    

}
}