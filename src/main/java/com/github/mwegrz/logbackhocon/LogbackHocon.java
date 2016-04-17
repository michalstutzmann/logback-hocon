package com.github.mwegrz.logbackhocon;

import ch.qos.logback.classic.LoggerContext;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogbackHocon {
    public static void configure(Config config) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();
        new HoconConfigurator().configure(context, config);
        Logger logger = LoggerFactory.getLogger(LogbackHocon.class);
        logger.debug("Configured");
    }
}
