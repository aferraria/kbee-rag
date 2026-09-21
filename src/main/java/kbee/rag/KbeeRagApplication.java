package kbee.rag;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import jakarta.annotation.PostConstruct;
import kbee.rag.audit.Logger;
import kbee.rag.audit.ServerConstant;

@ConfigurationPropertiesScan
@SpringBootApplication
public class KbeeRagApplication {

	static private Logger logger = Logger.getLogger(KbeeRagApplication.class.getName());
	static private Logger std_logger = Logger.getLogger("StartupLogger");

	public static void main(String[] args) {
		 std_logger.debug("Args: " + Arrays.toString(args));
		SpringApplication.run(KbeeRagApplication.class, args);
	}

	@PostConstruct
	public void onInitialize() {

		std_logger.debug(this.getClass().getName() + " is starting up...");
		std_logger.info("");

		for (String s : BannerUtil.generateBanner("KBEE"))
			std_logger.info(s);

		std_logger.info("");
		std_logger.info("version: " + "0.2b");
		std_logger.info(ServerConstant.SEPARATOR);
		std_logger.info("This software is licensed under the Apache License, Version 2.0");
		std_logger.info("http://www.apache.org/licenses/LICENSE-2.0");

		initShutdownMessage();

	}

	
	private void initShutdownMessage() {
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				std_logger.info(ServerConstant.SEPARATOR);
				std_logger.info("");
				std_logger.info("'Dulce et decorum est pro patria mori'...Shuting down... goodbye.");
				std_logger.info("");
			}
		});
	}

}
