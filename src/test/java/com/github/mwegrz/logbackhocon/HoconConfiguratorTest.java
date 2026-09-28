package com.github.mwegrz.logbackhocon;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.jmx.JMXConfigurator;
import ch.qos.logback.classic.jmx.MBeanUtil;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.layout.EchoLayout;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import ch.qos.logback.core.status.OnErrorConsoleStatusListener;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusListener;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class HoconConfiguratorTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private LoggerContext context;

    @Before
    public void setUp() {
        context = new LoggerContext();
    }

    @After
    public void tearDown() {
        context.stop();
    }

    private void configure(String hocon) {
        Config config = ConfigFactory.parseString(hocon).withFallback(ConfigFactory.defaultReference()).resolve();
        new HoconConfigurator().configure(context, config);
    }

    private Logger root() {
        return context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    }

    private static String quote(File file) {
        return "\"" + file.getAbsolutePath().replace("\\", "\\\\") + "\"";
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void isRegisteredAsServiceLoaderConfigurator() {
        Iterator<Configurator> it = ServiceLoader.load(Configurator.class).iterator();
        boolean found = false;
        while (it.hasNext()) {
            if (it.next() instanceof HoconConfigurator) found = true;
        }
        assertTrue(found);
    }

    @Test
    public void appliesReferenceDefaults() {
        configure("");
        assertEquals(Level.WARN, root().getLevel());
        assertFalse(root().iteratorForAppenders().hasNext());
    }

    @Test
    public void setsContextName() {
        configure("logback.context-name = my-app");
        assertEquals("my-app", context.getName());
    }

    @Test
    public void configuresRootLevelAndConsoleAppender() {
        configure(
                "logback {\n" +
                "  appenders.console {\n" +
                "    class = ch.qos.logback.core.ConsoleAppender\n" +
                "    with-jansi = false\n" +
                "    encoder.pattern = \"%msg%n\"\n" +
                "  }\n" +
                "  root { level = INFO, appenders = [console] }\n" +
                "}");

        assertEquals(Level.INFO, root().getLevel());
        Appender<ILoggingEvent> appender = root().getAppender("console");
        assertTrue(appender instanceof ConsoleAppender);
        assertTrue(appender.isStarted());
    }

    @Test
    public void configuresNamedLoggers() {
        configure(
                "logback {\n" +
                "  appenders.console { class = ch.qos.logback.core.ConsoleAppender, with-jansi = false }\n" +
                "  loggers {\n" +
                "    \"com.example\" { level = DEBUG, appenders = [console] }\n" +
                "  }\n" +
                "}");

        Logger logger = context.getLogger("com.example");
        assertEquals(Level.DEBUG, logger.getLevel());
        assertNotNull(logger.getAppender("console"));
        assertTrue(logger.isDebugEnabled());
        assertFalse(context.getLogger("com.other").isDebugEnabled());
    }

    @Test
    public void fileAppenderWritesWithPattern() throws Exception {
        File file = new File(tmp.getRoot(), "app.log");
        configure(
                "logback {\n" +
                "  appenders.file {\n" +
                "    class = ch.qos.logback.core.FileAppender\n" +
                "    file = " + quote(file) + "\n" +
                "    append = false\n" +
                "    encoder.pattern = \"%level|%logger|%msg%n\"\n" +
                "  }\n" +
                "  root { level = INFO, appenders = [file] }\n" +
                "}");

        assertTrue(root().getAppender("file") instanceof FileAppender);
        context.getLogger("test").info("hello");
        context.getLogger("test").debug("filtered out");
        context.stop();

        assertEquals("INFO|test|hello\n", read(file).replace("\r\n", "\n"));
    }

    @Test
    public void fileAppenderAppendsWhenConfigured() throws Exception {
        File file = new File(tmp.getRoot(), "app.log");
        Files.write(file.toPath(), "existing\n".getBytes(StandardCharsets.UTF_8));
        configure(
                "logback {\n" +
                "  appenders.file {\n" +
                "    class = ch.qos.logback.core.FileAppender\n" +
                "    file = " + quote(file) + "\n" +
                "    append = true\n" +
                "    encoder.pattern = \"%msg%n\"\n" +
                "  }\n" +
                "  root { level = INFO, appenders = [file] }\n" +
                "}");

        context.getLogger("test").info("new");
        context.stop();

        assertEquals("existing\nnew\n", read(file).replace("\r\n", "\n"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void consoleAppenderUsesEncoderAndLayoutClasses() {
        configure(
                "logback {\n" +
                "  appenders.console {\n" +
                "    class = ch.qos.logback.core.ConsoleAppender\n" +
                "    with-jansi = false\n" +
                "    encoder.class = ch.qos.logback.core.encoder.LayoutWrappingEncoder\n" +
                "    encoder.layout.class = ch.qos.logback.core.layout.EchoLayout\n" +
                "  }\n" +
                "  root.appenders = [console]\n" +
                "}");

        ConsoleAppender<ILoggingEvent> appender = (ConsoleAppender<ILoggingEvent>) root().getAppender("console");
        LayoutWrappingEncoder<ILoggingEvent> encoder = (LayoutWrappingEncoder<ILoggingEvent>) appender.getEncoder();
        assertTrue(encoder.getLayout() instanceof EchoLayout);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void registersConversionRules() {
        configure("logback.conversion-rules.foo.converter-class = com.example.FooConverter");

        Map<String, String> registry = (Map<String, String>) context.getObject(CoreConstants.PATTERN_RULE_REGISTRY);
        assertEquals("com.example.FooConverter", registry.get("foo"));
    }

    @Test
    public void rejectsUnsupportedAppenderClass() {
        try {
            configure("logback.appenders.x.class = com.example.UnknownAppender");
            fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            assertTrue(e.getMessage().contains("com.example.UnknownAppender"));
        }
    }

    @Test(expected = ConfigException.ValidationFailed.class)
    public void rejectsConfigNotMatchingReference() {
        configure("logback.appenders = not-an-object");
    }

    @Test
    public void debugAddsConsoleStatusListener() {
        configure("logback.debug = true");
        assertTrue(hasStatusListener(OnConsoleStatusListener.class));
    }

    @Test
    public void debugIsOffByDefault() {
        configure("");
        assertFalse(hasStatusListener(OnConsoleStatusListener.class));
    }

    @Test
    public void addsConfiguredStatusListener() {
        configure("logback.status-listener = ch.qos.logback.core.status.OnErrorConsoleStatusListener");
        assertTrue(hasStatusListener(OnErrorConsoleStatusListener.class));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownStatusListener() {
        configure("logback.status-listener = com.example.UnknownListener");
    }

    @Test
    public void registersJmxConfigurator() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName(MBeanUtil.getObjectNameFor("jmx-test", JMXConfigurator.class));
        try {
            configure("logback { context-name = jmx-test, jmx-configurator = on }");
            assertTrue(mbs.isRegistered(name));
        } finally {
            if (mbs.isRegistered(name)) mbs.unregisterMBean(name);
        }
    }

    @Test
    public void jmxConfiguratorIsOffByDefault() throws Exception {
        configure("logback.context-name = jmx-default-test");
        ObjectName name = new ObjectName(MBeanUtil.getObjectNameFor("jmx-default-test", JMXConfigurator.class));
        assertFalse(ManagementFactory.getPlatformMBeanServer().isRegistered(name));
    }

    @Test
    public void acceptsUpperCaseBooleans() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName name = new ObjectName(MBeanUtil.getObjectNameFor("jmx-upper-test", JMXConfigurator.class));
        try {
            configure("logback { context-name = jmx-upper-test, jmx-configurator = ON, debug = OFF }");
            assertTrue(mbs.isRegistered(name));
            assertFalse(hasStatusListener(OnConsoleStatusListener.class));
        } finally {
            if (mbs.isRegistered(name)) mbs.unregisterMBean(name);
        }
    }

    @Test(expected = ConfigException.WrongType.class)
    public void rejectsNonBooleanFlag() {
        configure("logback.debug = maybe");
    }

    private boolean hasStatusListener(Class<? extends StatusListener> type) {
        for (StatusListener l : context.getStatusManager().getCopyOfStatusListenerList()) {
            if (type.isInstance(l)) return true;
        }
        return false;
    }

    @Test
    public void setContextRejectsNull() {
        try {
            new HoconConfigurator().setContext(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void setContextStoresLoggerContext() {
        HoconConfigurator configurator = new HoconConfigurator();
        configurator.setContext(context);
        assertSame(context, configurator.getContext());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void consoleAppenderDefaultsWithJansiToFalse() {
        configure(
                "logback {\n" +
                "  appenders.console.class = ch.qos.logback.core.ConsoleAppender\n" +
                "  root.appenders = [console]\n" +
                "}");
        ConsoleAppender<ILoggingEvent> appender = (ConsoleAppender<ILoggingEvent>) root().getAppender("console");
        assertFalse(appender.isWithJansi());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void fileAppenderDefaultsAppendToTrue() {
        File file = new File(tmp.getRoot(), "app.log");
        configure(
                "logback {\n" +
                "  appenders.file {\n" +
                "    class = ch.qos.logback.core.FileAppender\n" +
                "    file = " + quote(file) + "\n" +
                "  }\n" +
                "  root.appenders = [file]\n" +
                "}");
        FileAppender<ILoggingEvent> appender = (FileAppender<ILoggingEvent>) root().getAppender("file");
        assertTrue(appender.isAppend());
    }

    @Test
    public void rejectsUnknownAppenderReferenceByName() {
        try {
            configure("logback.root.appenders = [missing]");
            fail("Expected an exception for unknown appender 'missing'");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("missing"));
        }
    }

    @Test
    public void asyncAppenderWrapsAppenderDefinedInAnyOrder() {
        // "a-async" sorts before "z-console" and is built first
        configure(
                "logback {\n" +
                "  appenders {\n" +
                "    z-console { class = ch.qos.logback.core.ConsoleAppender, with-jansi = false }\n" +
                "    a-async { class = ch.qos.logback.classic.AsyncAppender, appenders = [z-console] }\n" +
                "  }\n" +
                "  root.appenders = [a-async]\n" +
                "}");

        AsyncAppender async = (AsyncAppender) root().getAppender("a-async");
        assertNotNull(async);
        assertNotNull(async.getAppender("z-console"));
    }

    @Test
    public void rollingFileAppenderWithTimeBasedPolicy() throws Exception {
        File file = new File(tmp.getRoot(), "app.log");
        configure(
                "logback {\n" +
                "  appenders.rolling {\n" +
                "    class = ch.qos.logback.core.rolling.RollingFileAppender\n" +
                "    file = " + quote(file) + "\n" +
                "    encoder.pattern = \"%msg%n\"\n" +
                "    rolling-policy {\n" +
                "      class = ch.qos.logback.core.rolling.TimeBasedRollingPolicy\n" +
                "      file-name-pattern = " + quote(new File(tmp.getRoot(), "app.%d{yyyy-MM-dd}.log")) + "\n" +
                "      max-history = 7\n" +
                "      total-size-cap = 10MB\n" +
                "    }\n" +
                "  }\n" +
                "  root { level = INFO, appenders = [rolling] }\n" +
                "}");

        RollingFileAppender<?> appender = (RollingFileAppender<?>) root().getAppender("rolling");
        assertTrue(appender.isStarted());
        TimeBasedRollingPolicy<?> policy = (TimeBasedRollingPolicy<?>) appender.getRollingPolicy();
        assertEquals(7, policy.getMaxHistory());
        assertSame(policy, appender.getTriggeringPolicy());

        context.getLogger("test").info("rolled");
        context.stop();
        assertEquals("rolled\n", read(file).replace("\r\n", "\n"));
    }

    @Test
    public void rollingFileAppenderWithSizeAndTimeBasedPolicy() {
        File file = new File(tmp.getRoot(), "app.log");
        configure(
                "logback {\n" +
                "  appenders.rolling {\n" +
                "    class = ch.qos.logback.core.rolling.RollingFileAppender\n" +
                "    file = " + quote(file) + "\n" +
                "    encoder.pattern = \"%msg%n\"\n" +
                "    rolling-policy {\n" +
                "      class = ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy\n" +
                "      file-name-pattern = " + quote(new File(tmp.getRoot(), "app.%d{yyyy-MM-dd}.%i.log")) + "\n" +
                "      max-file-size = 1MB\n" +
                "    }\n" +
                "  }\n" +
                "  root.appenders = [rolling]\n" +
                "}");

        RollingFileAppender<?> appender = (RollingFileAppender<?>) root().getAppender("rolling");
        assertTrue(appender.isStarted());
        assertTrue(appender.getRollingPolicy() instanceof SizeAndTimeBasedRollingPolicy);
    }

    @Test
    public void rollingFileAppenderWithFixedWindowPolicyRollsOnSize() throws Exception {
        File file = new File(tmp.getRoot(), "app.log");
        configure(
                "logback {\n" +
                "  appenders.rolling {\n" +
                "    class = ch.qos.logback.core.rolling.RollingFileAppender\n" +
                "    file = " + quote(file) + "\n" +
                "    encoder.pattern = \"%msg%n\"\n" +
                "    rolling-policy {\n" +
                "      class = ch.qos.logback.core.rolling.FixedWindowRollingPolicy\n" +
                "      file-name-pattern = " + quote(new File(tmp.getRoot(), "app.%i.log")) + "\n" +
                "      min-index = 1\n" +
                "      max-index = 3\n" +
                "    }\n" +
                "    triggering-policy {\n" +
                "      class = ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy\n" +
                "      max-file-size = 1KB\n" +
                "    }\n" +
                "  }\n" +
                "  root { level = INFO, appenders = [rolling] }\n" +
                "}");

        RollingFileAppender<?> appender = (RollingFileAppender<?>) root().getAppender("rolling");
        assertTrue(appender.isStarted());
        FixedWindowRollingPolicy policy = (FixedWindowRollingPolicy) appender.getRollingPolicy();
        assertEquals(3, policy.getMaxIndex());
        assertTrue(appender.getTriggeringPolicy() instanceof SizeBasedTriggeringPolicy);

        // SizeBasedTriggeringPolicy checks the file size at most every few events, so write plenty
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < 100; i++) line.append('x');
        for (int i = 0; i < 500; i++) context.getLogger("test").info(line.toString());
        context.stop();

        assertTrue(new File(tmp.getRoot(), "app.1.log").exists());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void rejectsUnsupportedRollingPolicy() {
        configure(
                "logback.appenders.rolling {\n" +
                "  class = ch.qos.logback.core.rolling.RollingFileAppender\n" +
                "  rolling-policy.class = com.example.UnknownPolicy\n" +
                "}");
    }

    @Test
    public void rejectsCircularAppenderReferences() {
        try {
            configure(
                    "logback.appenders {\n" +
                    "  a { class = ch.qos.logback.classic.AsyncAppender, appenders = [b] }\n" +
                    "  b { class = ch.qos.logback.classic.AsyncAppender, appenders = [a] }\n" +
                    "}");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Circular"));
        }
    }

    @Test
    public void statusMessagesGoToContextStatusManager() {
        HoconConfigurator configurator = new HoconConfigurator();
        configurator.setContext(context);
        configurator.addInfo("info");
        configurator.addWarn("warn");
        configurator.addError("error", new RuntimeException());

        List<Status> statuses = context.getStatusManager().getCopyOfStatusList();
        assertEquals(3, statuses.size());
        assertEquals("error", statuses.get(2).getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void keepsExistingConversionRules() {
        Map<String, String> existing = new HashMap<>();
        existing.put("bar", "com.example.BarConverter");
        context.putObject(CoreConstants.PATTERN_RULE_REGISTRY, existing);

        configure("logback.conversion-rules.foo.converter-class = com.example.FooConverter");

        Map<String, String> registry = (Map<String, String>) context.getObject(CoreConstants.PATTERN_RULE_REGISTRY);
        assertEquals("com.example.BarConverter", registry.get("bar"));
        assertEquals("com.example.FooConverter", registry.get("foo"));
    }
}
