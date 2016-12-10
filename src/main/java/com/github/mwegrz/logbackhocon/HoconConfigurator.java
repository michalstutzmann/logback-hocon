package com.github.mwegrz.logbackhocon;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.*;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.core.status.Status;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import org.slf4j.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HoconConfigurator implements Configurator {
    private LoggerContext context = null;

    @Override
    public void configure(LoggerContext context) {
        Config config = ConfigFactory.load();
        configure(context, config);
    }

    void configure(LoggerContext context, Config config) {
        config.checkValid(ConfigFactory.defaultReference(), "logback");

        Config c = config.getConfig("logback");

        if (c.hasPath("status-listener")) {
            //context.addListener();
        }

        if (c.hasPath("debug")) {
            //context.
        }

        if (c.hasPath("context-name")) {
            context.setName(c.getString("context-name"));
        }

        if (c.hasPath("conversion-rules")) {
            Map<String, String> registry = null;
                    //(Map<String, String>) this.context.getObject(CoreConstants.PATTERN_RULE_REGISTRY);
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

        //ConfigurationDelegate delegate = new ConfigurationDelegate();
        //delegate.setContext(context);
        //if (c.getBoolean("jmx-configurator")) delegate.jmxConfigurator();

        Map<String, Appender<ILoggingEvent>> appenders = new HashMap<>();
        for (Map.Entry<String, ConfigValue> e : c.getConfig("appenders").root().entrySet()) {
            String name = e.getKey();
            Config appenderConfig = ((ConfigObject) e.getValue()).toConfig();
            String appenderClass = appenderConfig.getString("class");

            if(appenderClass.equals("ch.qos.logback.core.ConsoleAppender")) {
                ConsoleAppender<ILoggingEvent> a = new ConsoleAppender<ILoggingEvent>();
                if(appenderConfig.hasPath("encoder.pattern")) {
                    PatternLayoutEncoder encoder = createPatternLayoutEncoder(context, appenderConfig.getString("encoder.pattern"));
                    a.setEncoder(encoder);
                } else if (appenderConfig.hasPath("encoder.class")) {
                    String encoderClass = appenderConfig.getString("encoder.class");
                    try {
                        LayoutWrappingEncoder<ILoggingEvent> encoder = (LayoutWrappingEncoder<ILoggingEvent>) Class.forName(encoderClass).newInstance();
                        if(appenderConfig.hasPath("encoder.layout.class")) {
                            String lc = appenderConfig.getString("encoder.layout.class");
                            try {
                                Layout<ILoggingEvent> layout = (Layout<ILoggingEvent>) Class.forName(lc).newInstance();
                                layout.setContext(context);
                                encoder.setLayout(layout);
                            } catch (Exception ex) {
                                throw new IllegalArgumentException(ex);
                            }
                        }
                        encoder.setContext(context);
                        a.setEncoder(encoder);
                    } catch (Exception ex) {
                        throw new IllegalArgumentException(ex);
                    }
                }

                a.setWithJansi(appenderConfig.getBoolean("with-jansi"));
                a.setName(name);
                a.setContext(context);
                a.start();
                appenders.put(name, a);
            } else if (appenderClass.equals("ch.qos.logback.core.FileAppender")) {
                FileAppender<ILoggingEvent> a = new FileAppender<ILoggingEvent>();
                a.setName(name);
                if(appenderConfig.hasPath("encoder.pattern")) {
                    PatternLayoutEncoder encoder = createPatternLayoutEncoder(context, appenderConfig.getString("encoder.pattern"));
                    a.setEncoder(encoder);
                }
                a.setContext(context);
                a.setFile(appenderConfig.getString("file"));
                a.setAppend(appenderConfig.getBoolean("append"));
                a.start();
                appenders.put(name, a);
            } else if (appenderClass.equals("ch.qos.logback.core.RollingFileAppender")) {
                RollingFileAppender<ILoggingEvent> a = new RollingFileAppender<ILoggingEvent>();
                a.setName(name);
                if(appenderConfig.hasPath("encoder.pattern")) {
                    PatternLayoutEncoder encoder = createPatternLayoutEncoder(context, appenderConfig.getString("encoder.pattern"));
                    a.setEncoder(encoder);
                }
                a.setContext(context);
                a.setFile(appenderConfig.getString("file"));
                a.setAppend(appenderConfig.getBoolean("append"));

                a.start();
                appenders.put(name, a);
            } else if (appenderClass.equals("ch.qos.logback.classic.AsyncAppender")) {
                List<String> appenderNames = appenderConfig.getStringList("appenders");
                AsyncAppender a = new AsyncAppender();
                for (String an : appenderNames) {
                    a.addAppender(appenders.get(an));
                }
                a.setContext(context);
                a.start();
                appenders.put(name, a);
            } else {
                throw new UnsupportedOperationException("Unsupported appender: " + appenderClass + ". Supported appenders: ch.qos.logback.core.ConsoleAppender and ch.qos.logback.core.FileAppender");
            }
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

    private PatternLayoutEncoder createPatternLayoutEncoder(Context context, String encoderPattern) {
        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setPattern(encoderPattern);
        encoder.setContext(context);
        encoder.start();
        return encoder;
    }

    @Override
    public void setContext(Context context) {
        if(context == null) throw new IllegalArgumentException("Context is null");
        if(!(context instanceof LoggerContext))
            throw new IllegalArgumentException("Context is not of type LoggerContext");
        this.context = (LoggerContext) context;
    }

    @Override
    public Context getContext() {
        return context;
    }

    @Override
    public void addStatus(Status status) {
      throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void addInfo(String msg) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void addInfo(String msg, Throwable ex) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void addWarn(String msg) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void addWarn(String msg, Throwable ex) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void addError(String msg) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void addError(String msg, Throwable ex) {
        throw new UnsupportedOperationException("Not supported");
    }
}
