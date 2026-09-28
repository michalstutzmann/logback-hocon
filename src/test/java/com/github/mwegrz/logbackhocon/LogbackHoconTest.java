package com.github.mwegrz.logbackhocon;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.util.ContextInitializer;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class LogbackHoconTest {
    @Test
    public void autoConfiguresFromApplicationConf() throws Exception {
        LoggerContext context = new LoggerContext();
        try {
            new ContextInitializer(context).autoConfig();

            // Values from src/test/resources/application.conf
            Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            assertEquals(Level.ERROR, root.getLevel());
            assertNotNull(root.getAppender("console"));
            assertNotNull(root.getAppender("file"));
            assertEquals(Level.DEBUG, context.getLogger("com.github.mwegrz").getLevel());
        } finally {
            context.stop();
        }
    }

    @Test
    public void reconfiguresGlobalContext() {
        LogbackHocon.configure(ConfigFactory.parseString(
                "logback { context-name = test-reconfigured, root.level = INFO }")
                .withFallback(ConfigFactory.defaultReference()));

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        assertEquals("test-reconfigured", context.getName());
        assertEquals(Level.INFO, root.getLevel());
        assertFalse(root.iteratorForAppenders().hasNext());
    }
}
