package com.github.mwegrz.logbackhocon;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.jmx.JMXConfigurator;
import ch.qos.logback.classic.jmx.MBeanUtil;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.CoreConstants;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.core.spi.ContextAware;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.spi.LifeCycle;
import ch.qos.logback.core.status.OnConsoleStatusListener;
import ch.qos.logback.core.status.StatusListener;
import ch.qos.logback.core.util.FileSize;
import ch.qos.logback.core.util.StatusListenerConfigHelper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;
import org.slf4j.Logger;

import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

public class HoconConfigurator extends ContextAwareBase implements Configurator {
    private static final String CONSOLE_APPENDER = "ch.qos.logback.core.ConsoleAppender";
    private static final String FILE_APPENDER = "ch.qos.logback.core.FileAppender";
    private static final String ROLLING_FILE_APPENDER = "ch.qos.logback.core.rolling.RollingFileAppender";
    private static final String ASYNC_APPENDER = "ch.qos.logback.classic.AsyncAppender";
    private static final List<String> SUPPORTED_APPENDERS =
            Arrays.asList(CONSOLE_APPENDER, FILE_APPENDER, ROLLING_FILE_APPENDER, ASYNC_APPENDER);

    private static final String TIME_BASED_ROLLING_POLICY = "ch.qos.logback.core.rolling.TimeBasedRollingPolicy";
    private static final String SIZE_AND_TIME_BASED_ROLLING_POLICY = "ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy";
    private static final String FIXED_WINDOW_ROLLING_POLICY = "ch.qos.logback.core.rolling.FixedWindowRollingPolicy";
    private static final String SIZE_BASED_TRIGGERING_POLICY = "ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy";

    @Override
    public void configure(LoggerContext context) {
        Config config = ConfigFactory.load();
        configure(context, config);
    }

    void configure(LoggerContext context, Config config) {
        if (getContext() == null) setContext(context);

        config.checkValid(ConfigFactory.defaultReference(), "logback");

        Config c = config.getConfig("logback");

        if (getBooleanIgnoreCase(c, "debug")) {
            StatusListenerConfigHelper.addOnConsoleListenerInstance(context, new OnConsoleStatusListener());
        }

        if (c.hasPath("status-listener")) {
            addStatusListener(context, c.getString("status-listener"));
        }

        if (c.hasPath("context-name")) {
            context.setName(c.getString("context-name"));
        }

        if (c.hasPath("conversion-rules")) {
            @SuppressWarnings("unchecked")
            Map<String, String> registry = (Map<String, String>) context.getObject(CoreConstants.PATTERN_RULE_REGISTRY);
            if (registry == null) {
                registry = new HashMap<>();
                context.putObject(CoreConstants.PATTERN_RULE_REGISTRY, registry);
            }
            for (Map.Entry<String, ConfigValue> e : c.getConfig("conversion-rules").root().entrySet()) {
                String conversionWord = e.getKey();
                Config crc = ((ConfigObject) e.getValue()).toConfig();
                String converterClass = crc.getString("converter-class");
                registry.put(conversionWord, converterClass);
            }
        }

        if (getBooleanIgnoreCase(c, "jmx-configurator")) {
            registerJmxConfigurator(context);
        }

        Appenders appenders = new Appenders(context, c.getConfig("appenders"));
        for (String name : c.getConfig("appenders").root().keySet()) {
            appenders.get(name);
        }

        for (Map.Entry<String, ConfigValue> e : c.getConfig("loggers").root().entrySet()) {
            String name = e.getKey();
            Config loggerConfig = ((ConfigObject) e.getValue()).toConfig();
            List<String> appenderNames = loggerConfig.getStringList("appenders");
            String level = loggerConfig.getString("level");
            ch.qos.logback.classic.Logger log = context.getLogger(name);
            log.setLevel(Level.toLevel(level));
            for (String an : appenderNames) {
                log.addAppender(appenders.get(an));
            }
        }

        String rootLevel = c.getString("root.level");
        List<String> rootAppenderNames = c.getStringList("root.appenders");
        ch.qos.logback.classic.Logger rootLog = context.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLog.setLevel(Level.toLevel(rootLevel));

        for (String an : rootAppenderNames) {
            rootLog.addAppender(appenders.get(an));
        }
    }

    private void addStatusListener(LoggerContext context, String className) {
        StatusListener listener;
        try {
            listener = (StatusListener) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Cannot instantiate status listener " + className, ex);
        }
        if (listener instanceof ContextAware) ((ContextAware) listener).setContext(context);
        context.getStatusManager().add(listener);
        if (listener instanceof LifeCycle) ((LifeCycle) listener).start();
    }

    private void registerJmxConfigurator(LoggerContext context) {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        String objectNameAsString = MBeanUtil.getObjectNameFor(context.getName(), JMXConfigurator.class);
        ObjectName objectName = MBeanUtil.string2ObjectName(context, this, objectNameAsString);
        if (objectName != null && !MBeanUtil.isRegistered(mbs, objectName)) {
            JMXConfigurator jmxConfigurator = new JMXConfigurator(context, mbs, objectName);
            try {
                mbs.registerMBean(jmxConfigurator, objectName);
            } catch (Exception ex) {
                addError("Failed to register JMXConfigurator " + objectNameAsString, ex);
            }
        }
    }

    /**
     * Builds appenders on first reference, so an appender may refer to others (e.g. AsyncAppender)
     * regardless of the order in which they are defined.
     */
    private static final class Appenders {
        private final LoggerContext context;
        private final Config config;
        private final Map<String, Appender<ILoggingEvent>> built = new HashMap<>();
        private final Set<String> inProgress = new HashSet<>();

        Appenders(LoggerContext context, Config config) {
            this.context = context;
            this.config = config;
        }

        Appender<ILoggingEvent> get(String name) {
            Appender<ILoggingEvent> appender = built.get(name);
            if (appender != null) return appender;

            if (!config.root().containsKey(name)) {
                throw new IllegalArgumentException("Unknown appender: " + name);
            }
            if (!inProgress.add(name)) {
                throw new IllegalArgumentException("Circular appender reference: " + name);
            }
            appender = create(name, ((ConfigObject) config.root().get(name)).toConfig());
            inProgress.remove(name);
            built.put(name, appender);
            return appender;
        }

        private Appender<ILoggingEvent> create(String name, Config appenderConfig) {
            String appenderClass = appenderConfig.getString("class");

            if (appenderClass.equals(CONSOLE_APPENDER)) {
                ConsoleAppender<ILoggingEvent> a = new ConsoleAppender<>();
                a.setContext(context);
                a.setName(name);
                setEncoder(a, appenderConfig);
                a.setWithJansi(getBoolean(appenderConfig, "with-jansi", false));
                a.start();
                return a;
            } else if (appenderClass.equals(FILE_APPENDER)) {
                FileAppender<ILoggingEvent> a = new FileAppender<>();
                a.setContext(context);
                a.setName(name);
                setEncoder(a, appenderConfig);
                a.setFile(appenderConfig.getString("file"));
                a.setAppend(getBoolean(appenderConfig, "append", true));
                a.start();
                return a;
            } else if (appenderClass.equals(ROLLING_FILE_APPENDER)) {
                RollingFileAppender<ILoggingEvent> a = new RollingFileAppender<>();
                a.setContext(context);
                a.setName(name);
                setEncoder(a, appenderConfig);
                if (appenderConfig.hasPath("file")) {
                    a.setFile(appenderConfig.getString("file"));
                }
                a.setAppend(getBoolean(appenderConfig, "append", true));
                setRollingPolicy(a, appenderConfig);
                a.start();
                return a;
            } else if (appenderClass.equals(ASYNC_APPENDER)) {
                AsyncAppender a = new AsyncAppender();
                a.setContext(context);
                a.setName(name);
                if (appenderConfig.hasPath("queue-size")) a.setQueueSize(appenderConfig.getInt("queue-size"));
                if (appenderConfig.hasPath("discarding-threshold")) a.setDiscardingThreshold(appenderConfig.getInt("discarding-threshold"));
                if (appenderConfig.hasPath("include-caller-data")) a.setIncludeCallerData(getBooleanIgnoreCase(appenderConfig, "include-caller-data"));
                if (appenderConfig.hasPath("never-block")) a.setNeverBlock(getBooleanIgnoreCase(appenderConfig, "never-block"));
                for (String an : appenderConfig.getStringList("appenders")) {
                    a.addAppender(get(an));
                }
                a.start();
                return a;
            } else {
                throw new UnsupportedOperationException("Unsupported appender: " + appenderClass + ". Supported appenders: " + String.join(", ", SUPPORTED_APPENDERS));
            }
        }

        private void setRollingPolicy(RollingFileAppender<ILoggingEvent> a, Config appenderConfig) {
            Config rp = appenderConfig.getConfig("rolling-policy");
            String policyClass = rp.getString("class");

            if (policyClass.equals(TIME_BASED_ROLLING_POLICY) || policyClass.equals(SIZE_AND_TIME_BASED_ROLLING_POLICY)) {
                TimeBasedRollingPolicy<ILoggingEvent> p;
                if (policyClass.equals(SIZE_AND_TIME_BASED_ROLLING_POLICY)) {
                    SizeAndTimeBasedRollingPolicy<ILoggingEvent> sp = new SizeAndTimeBasedRollingPolicy<>();
                    sp.setMaxFileSize(FileSize.valueOf(rp.getString("max-file-size")));
                    p = sp;
                } else {
                    p = new TimeBasedRollingPolicy<>();
                }
                p.setContext(context);
                p.setParent(a);
                p.setFileNamePattern(rp.getString("file-name-pattern"));
                if (rp.hasPath("max-history")) p.setMaxHistory(rp.getInt("max-history"));
                if (rp.hasPath("total-size-cap")) p.setTotalSizeCap(FileSize.valueOf(rp.getString("total-size-cap")));
                if (rp.hasPath("clean-history-on-start")) p.setCleanHistoryOnStart(getBooleanIgnoreCase(rp, "clean-history-on-start"));
                p.start();
                a.setRollingPolicy(p);
            } else if (policyClass.equals(FIXED_WINDOW_ROLLING_POLICY)) {
                FixedWindowRollingPolicy p = new FixedWindowRollingPolicy();
                p.setContext(context);
                p.setParent(a);
                p.setFileNamePattern(rp.getString("file-name-pattern"));
                if (rp.hasPath("min-index")) p.setMinIndex(rp.getInt("min-index"));
                if (rp.hasPath("max-index")) p.setMaxIndex(rp.getInt("max-index"));
                p.start();
                a.setRollingPolicy(p);

                Config tp = appenderConfig.getConfig("triggering-policy");
                String triggeringClass = tp.getString("class");
                if (!triggeringClass.equals(SIZE_BASED_TRIGGERING_POLICY)) {
                    throw new UnsupportedOperationException("Unsupported triggering policy: " + triggeringClass + ". Supported triggering policies: " + SIZE_BASED_TRIGGERING_POLICY);
                }
                SizeBasedTriggeringPolicy<ILoggingEvent> t = new SizeBasedTriggeringPolicy<>();
                t.setContext(context);
                t.setMaxFileSize(FileSize.valueOf(tp.getString("max-file-size")));
                t.start();
                a.setTriggeringPolicy(t);
            } else {
                throw new UnsupportedOperationException("Unsupported rolling policy: " + policyClass + ". Supported rolling policies: " + String.join(", ", TIME_BASED_ROLLING_POLICY, SIZE_AND_TIME_BASED_ROLLING_POLICY, FIXED_WINDOW_ROLLING_POLICY));
            }
        }

        private void setEncoder(OutputStreamAppender<ILoggingEvent> a, Config appenderConfig) {
            Encoder<ILoggingEvent> encoder = createEncoder(appenderConfig);
            if (encoder != null) a.setEncoder(encoder);
        }

        @SuppressWarnings("unchecked")
        private Encoder<ILoggingEvent> createEncoder(Config appenderConfig) {
            if (appenderConfig.hasPath("encoder.pattern")) {
                PatternLayoutEncoder encoder = new PatternLayoutEncoder();
                encoder.setPattern(appenderConfig.getString("encoder.pattern"));
                encoder.setContext(context);
                encoder.start();
                return encoder;
            } else if (appenderConfig.hasPath("encoder.class")) {
                LayoutWrappingEncoder<ILoggingEvent> encoder = newInstance(appenderConfig.getString("encoder.class"));
                encoder.setContext(context);
                if (appenderConfig.hasPath("encoder.layout.class")) {
                    Layout<ILoggingEvent> layout = newInstance(appenderConfig.getString("encoder.layout.class"));
                    layout.setContext(context);
                    layout.start();
                    encoder.setLayout(layout);
                }
                encoder.start();
                return encoder;
            } else {
                return null;
            }
        }

        @SuppressWarnings("unchecked")
        private static <T> T newInstance(String className) {
            try {
                return (T) Class.forName(className).getDeclaredConstructor().newInstance();
            } catch (Exception ex) {
                throw new IllegalArgumentException("Cannot instantiate " + className, ex);
            }
        }

        private static boolean getBoolean(Config config, String path, boolean defaultValue) {
            return config.hasPath(path) ? getBooleanIgnoreCase(config, path) : defaultValue;
        }
    }

    // Like Config.getBoolean, but case-insensitive, so ON/OFF from older configs still work.
    private static boolean getBooleanIgnoreCase(Config config, String path) {
        ConfigValue value = config.getValue(path);
        if (value.valueType() == ConfigValueType.BOOLEAN) return (Boolean) value.unwrapped();
        if (value.valueType() == ConfigValueType.STRING) {
            String s = ((String) value.unwrapped()).trim().toLowerCase(Locale.ROOT);
            if (s.equals("true") || s.equals("yes") || s.equals("on")) return true;
            if (s.equals("false") || s.equals("no") || s.equals("off")) return false;
        }
        throw new ConfigException.WrongType(value.origin(), path, "boolean", value.valueType().name());
    }

    @Override
    public void setContext(Context context) {
        if (context == null) throw new IllegalArgumentException("Context is null");
        if (!(context instanceof LoggerContext))
            throw new IllegalArgumentException("Context is not of type LoggerContext");
        super.setContext(context);
    }
}
