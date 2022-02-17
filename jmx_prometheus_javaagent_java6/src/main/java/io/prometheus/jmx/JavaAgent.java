package io.prometheus.jmx;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.instrument.Instrumentation;
import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;
import org.yaml.snakeyaml.Yaml;

import javax.management.MalformedObjectNameException;

public class JavaAgent {

    static HTTPServer server;

    public static void agentmain(final String agentArgument, final Instrumentation instrumentation) throws Exception {
        String host = "0.0.0.0";
        try {
            Config config = parseConfig(agentArgument, host);
            Map yaml =  new Yaml().load(new FileInputStream(config.file));
            if (yaml.containsKey("agentDelay")){
                final int delay = (Integer) yaml.get("agentDelay");
                final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                System.out.println("Scheduling task to run in " + delay +" seconds");
                executor.schedule(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            System.err.println("Starting Premain After " + delay + "minute");
                            premain(agentArgument, instrumentation);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, delay, TimeUnit.SECONDS);
            }
            else premain(agentArgument, instrumentation);

        }
        catch (IllegalArgumentException e) {
            System.err.println("Usage: -javaagent:/path/to/JavaAgent.jar=[host:]<port>:<yaml configuration file> " + e.getMessage());
            System.exit(1);
        }


    }

    public static void premain(final String agentArgument, final Instrumentation instrumentation) throws Exception {
        // Bind to all interfaces by default (this includes IPv6).
        String host = "0.0.0.0";
        try {
            final Config config = parseConfig(agentArgument, host);
            Map yaml =  new Yaml().load(new FileInputStream(config.file));
            if (yaml.containsKey("agentDelay")){
                final int delay = (Integer) yaml.get("agentDelay");
                final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
                System.out.println("Scheduling task to run in " + delay +" seconds");
                executor.schedule(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            System.out.println("Starting Java Prometheus Exporter Agent after " + delay + "seconds");
                            startAgent(config);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }, delay, TimeUnit.SECONDS);
            }
            else  startAgent(config);
        }
        catch (IllegalArgumentException e) {
            System.err.println("Usage: -javaagent:/path/to/JavaAgent.jar=[host:]<port>:<yaml configuration file> " + e.getMessage());
            System.exit(1);
        }
    }
    public static void startAgent(final Config config) throws Exception {
        new BuildInfoCollector().register();
        new JmxCollector(new File(config.file), JmxCollector.Mode.AGENT).register();
        DefaultExports.initialize();
        server = new HTTPServer(config.socket, CollectorRegistry.defaultRegistry, true);
    }

    /**
     * Parse the Java Agent configuration. The arguments are typically specified to the JVM as a javaagent as
     * {@code -javaagent:/path/to/agent.jar=<CONFIG>}. This method parses the {@code <CONFIG>} portion.
     * @param args provided agent args
     * @param ifc default bind interface
     * @return configuration to use for our application
     */
    public static Config parseConfig(String args, String ifc) {
        Pattern pattern = Pattern.compile(
                "^(?:((?:[\\w.-]+)|(?:\\[.+])):)?" + // host name, or ipv4, or ipv6 address in brackets
                        "(\\d{1,5}):" +              // port
                        "(.+)");                     // config file

        Matcher matcher = pattern.matcher(args);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Malformed arguments - " + args);
        }

        String givenHost = matcher.group(1);
        String givenPort = matcher.group(2);
        String givenConfigFile = matcher.group(3);

        int port = Integer.parseInt(givenPort);

        InetSocketAddress socket;
        if (givenHost != null && !givenHost.isEmpty()) {
            socket = new InetSocketAddress(givenHost, port);
        }
        else {
            socket = new InetSocketAddress(ifc, port);
            givenHost = ifc;
        }

        return new Config(givenHost, port, givenConfigFile, socket);
    }

    static class Config {
        String host;
        int port;
        String file;
        InetSocketAddress socket;

        Config(String host, int port, String file, InetSocketAddress socket) {
            this.host = host;
            this.port = port;
            this.file = file;
            this.socket = socket;
        }
    }
}
