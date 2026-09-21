package kbee.rag;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import java.io.File;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.annotation.JsonIgnore;

import kbee.rag.audit.Logger;
import kbee.rag.audit.ServerConstant;

/**

 */

@Component
public class AppStartupApplicationRunner implements ApplicationRunner {

	static private Logger logger = Logger.getLogger(AppStartupApplicationRunner.class.getName());
	static private Logger startupLogger = Logger.getLogger("StartupLogger");

	@Autowired
	@JsonIgnore
	private final ApplicationContext appContext;

	public AppStartupApplicationRunner(ApplicationContext appContext) {
		this.appContext = appContext;
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {

		if (startupLogger.isDebugEnabled()) {
			startupLogger.debug("Command line args:");
			args.getNonOptionArgs().forEach(item -> startupLogger.debug(item));
		}

		Locale.setDefault(Locale.forLanguageTag("es"));

		startupLogger.info(ServerConstant.SEPARATOR);

		// Settings settings = getAppContext().getBean(Settings.class);
		// startupLogger.info("App name -> " + settings.getAppName());
		// startupLogger.info("Port -> " + settings.getPort());
		//startupLogger.info(ServerConstant.SEPARATOR);

		for (String s : args.getSourceArgs()) {
			logger.debug(s);
		}
	}

	public ApplicationContext getAppContext() {
		return appContext;
	}
}
