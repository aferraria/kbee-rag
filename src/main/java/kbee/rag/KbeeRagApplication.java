package kbee.rag;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class KbeeRagApplication {
    public static void main(String[] args) {
        System.out.println(
                "Argumentos recibidos: "
                + Arrays.toString(args)
        );

        
        
        SpringApplication.run(KbeeRagApplication.class, args);
    }
}
