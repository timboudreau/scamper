package com.mastfrog.scamper.protocol;

import com.google.common.collect.ImmutableSet;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;
import com.mastfrog.giulius.Dependencies;
import com.mastfrog.giulius.DependenciesBuilder;
import com.mastfrog.guicy.annotations.Namespace;
import com.mastfrog.scamper.ChannelConfigurer;
import com.mastfrog.scamper.Init;
import com.mastfrog.scamper.SctpClient;
import com.mastfrog.scamper.SctpServer;
import com.mastfrog.settings.Settings;
import com.mastfrog.settings.SettingsBuilder;
import com.mastfrog.util.Checks;
import com.mastfrog.util.ConfigurationError;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.sctp.SctpChannelOption;
import io.netty.channel.sctp.nio.NioSctpChannel;
import io.netty.channel.sctp.nio.NioSctpServerChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Builder for SCTP servers and clients.
 *
 * @author Tim Boudreau
 */
public class SctpServerAndClientBuilder {

    private int port = 8007;
    private String host = "127.0.0.1";
    private Set<OptionEntry<?>> options = new LinkedHashSet<>();
    private int eventThreads = 1;
    private int workerThreads = -1;
    private final List<ProtocolModule.Entry> bindings = new LinkedList<>();
    private final List<Module> modules = new LinkedList<>();
    private final List<Settings> settings = new LinkedList<>();
    private boolean built;
    private final String settingsName;

    public SctpServerAndClientBuilder() {
        this("scamper");
    }

    /**
     * Create a new builder.
     *
     * @param settingsName Settings (port and other configuration that can be
     * found using Guice's &#064;Named annotation) are looked up by merging
     * properties files named $settingsName.properties in /etc /opt/local/etc,
     * ~/ and ./ if they are present. If command-line arguments are passed to
     * the build methods, these can be overridden there
     */
    public SctpServerAndClientBuilder(String settingsName) {
        Checks.notNull("settingsName", settingsName);
        this.settingsName = settingsName;
        option(ChannelOption.TCP_NODELAY, true);
    }

    /**
     * Set the host - only useful if you're building a client.
     *
     * @param host The host
     * @return This
     */
    public SctpServerAndClientBuilder withHost(String host) {
        this.host = host;
        return this;
    }

    private void checkBuilt() {
        if (built) {
            throw new ConfigurationError("build method already called");
        }
        built = true;
    }

    private Dependencies buildInjector(boolean forServer, String... cmdlineArgs) throws IOException {
        checkBuilt();
        Settings settings = settings(cmdlineArgs);
        DependenciesBuilder builder = deps(settings);
        Dependencies deps = builder.build();
        return deps;
    }

    /**
     * Build an sctp server
     *
     * @param cmdlineArgs Command-line arguments in the form
     * <code>--name value</code> to parse
     * @return A control object which can be used to get the server or shut it
     * down
     * @throws IOException if something goes wrong
     */
    public Control<SctpServer> buildServer(String... cmdlineArgs) throws IOException {
        Dependencies deps = buildInjector(true, cmdlineArgs);
        return new ControlImpl<SctpServer>(deps.getInstance(SctpServer.class), deps);
    }

    /**
     * Build an sctp client
     *
     * @param cmdlineArgs Command-line arguments in the form
     * <code>--name value</code> to parse
     * @return A control object which can be used to get the client or shut it
     * down
     * @throws IOException if something goes wrong
     */
    public Control<SctpClient> buildClient(String... cmdlineArgs) throws IOException {
        Dependencies deps = buildInjector(false, cmdlineArgs);
        return new ControlImpl<SctpClient>(deps.getInstance(SctpClient.class), deps);
    }

    /**
     * Build an object which can send messages to an sctp server
     *
     * @param cmdlineArgs Command-line arguments in the form
     * <code>--name value</code> to parse
     * @return A control object which can be used to get the server or shut it
     * down
     * @throws IOException if something goes wrong
     */
    public Control<Sender> buildSender(String... cmdlineArgs) throws IOException {
        Dependencies deps = buildInjector(false, cmdlineArgs);
        return new ControlImpl<Sender>(deps.getInstance(Sender.class), deps);
    }

    private ProtocolModule protoModule() {
        ProtocolModule m = new ProtocolModule(eventThreads, workerThreads);
        for (ProtocolModule.Entry e : this.bindings) {
            m.addEntry(e);
        }
        return m;
    }

    private DependenciesBuilder deps(Settings settings) {
        DependenciesBuilder builder = Dependencies.builder().add(settings, Namespace.DEFAULT);
        for (Module m : this.modules) {
            builder.add(m);
        }
        builder.add(protoModule());
        builder.add(new Mod(options));
        return builder;
    }

    private static class ControlImpl<T> implements Control<T> {

        private final T object;
        private final Dependencies deps;

        public ControlImpl(T object, Dependencies deps) {
            this.object = object;
            this.deps = deps;
        }

        @Override
        public void shutdown() {
            deps.shutdown();
        }

        @Override
        public T get() {
            return object;
        }

        @Override
        public Dependencies getInjector() {
            return deps;
        }
    }

    static final class Mod extends AbstractModule {

        private final Set<OptionEntry<?>> entries;

        public Mod(Set<OptionEntry<?>> entries) {
            this.entries = entries;
        }

        @Override
        protected void configure() {
            TypeLiteral<Set<OptionEntry<?>>> e = new TypeLiteral<Set<OptionEntry<?>>>() {
            };
            bind(e).toInstance(entries);
            bind(ChannelConfigurer.class).to(Config.class);
        }

        static class Config extends ChannelConfigurer {

            private final Set<OptionEntry<?>> entries;

            @Inject
            public Config(@Named(value = "boss") EventLoopGroup boss, @Named("worker") EventLoopGroup worker, Provider<ChannelHandlerAdapter> handler, Set<OptionEntry<?>> entries) {
                super(boss, worker, handler);
                this.entries = ImmutableSet.copyOf(entries);
            }

            protected ServerBootstrap init(ServerBootstrap b) {
                Init init = new Init(handler);
                b = b.group(group, worker)
                        .channel(NioSctpServerChannel.class)
                        .option(ChannelOption.SO_BACKLOG, 1000);
                for (OptionEntry<?> entry : entries) {
                    b = entry.apply(b);
                }
                b.handler(new LoggingHandler(LogLevel.INFO))
                        .childHandler(init);
                return b;
            }

            protected Bootstrap init(Bootstrap b) {
                Init init = new Init(handler);
                b = b.group(group).channel(NioSctpChannel.class)
                        .option(SctpChannelOption.SCTP_NODELAY, true);
                for (OptionEntry<?> entry : entries) {
                    b = entry.apply(b);
                }
                return b.handler(init);
            }

        }

    }

    private Settings settings(String... cmdlineArgs) throws IOException {
        SettingsBuilder b = new SettingsBuilder(settingsName)
                .add("port", "" + port)
                .add("host", host)
                .addDefaultLocations();
        for (Settings s : this.settings) {
            b.add(s);
        }
        b.parseCommandLineArguments(cmdlineArgs);
        return b.build();
    }

    private boolean hasOption(ChannelOption<?> opt) {
        for (OptionEntry<?> e : this.options) {
            if (e.option.equals(opt)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Map a message type to a handler which will receive messages of that type
     *
     * @param type The type
     * @param handlerType The handler type (will be instantiated by Guice)
     * @return this
     */
    public SctpServerAndClientBuilder bind(MessageType type, Class<? extends MessageHandler<?, ?>> handlerType) {
        for (ProtocolModule.Entry entry : bindings) {
            if (entry.type.equals(type)) {
                throw new ConfigurationError(entry.type + " was already "
                        + "registered for " + type);
            }
        }
        bindings.add(new ProtocolModule.Entry(type, handlerType));
        return this;
    }

    /**
     * Set a channel option for the NioSctpChannel.
     *
     * @param <T> The option type
     * @param option The option
     * @param value The value to set the option to
     * @return this
     */
    public <T> SctpServerAndClientBuilder option(ChannelOption<T> option, T value) {
        Checks.notNull("option", option);
        Checks.notNull("value", value);
        OptionEntry<T> entry = new OptionEntry<>(option, value);
        options.remove(entry);
        options.add(entry);
        return this;
    }

    /**
     * Set the port. For clients, this is the port that will be connected to.
     * For servers it is the local port that will be bound.
     *
     * @param port The port
     * @return
     */
    public SctpServerAndClientBuilder onPort(int port) {
        Checks.nonZero("port", port);
        Checks.nonNegative("port", port);
        if (port > 65535) {
            throw new IllegalArgumentException("Invalid port " + port);
        }
        this.port = port;
        return this;
    }

    /**
     * Set the number of event threads that will initially receieve incoming
     * events.
     *
     * @param eventThreads The number of threads
     * @return this
     */
    public SctpServerAndClientBuilder withEventThreads(int eventThreads) {
        Checks.nonZero("eventThreads", eventThreads);
        Checks.nonNegative("eventThreads", eventThreads);
        this.eventThreads = eventThreads;
        return this;
    }

    /**
     * Set the number of worker threads (applies to servers only) that will be
     * used to post-process incoming messages.
     *
     * @param workerThreads The number of threads
     * @return this
     */
    public SctpServerAndClientBuilder withWorkerThreads(int workerThreads) {
        Checks.nonZero("workerThreads", workerThreads);
        Checks.nonNegative("workerThreads", workerThreads);
        this.workerThreads = workerThreads;
        return this;
    }

    static class OptionEntry<T> {

        private final ChannelOption<T> option;
        private final T value;

        public OptionEntry(ChannelOption<T> option, T value) {
            this.option = option;
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof OptionEntry && ((OptionEntry) o).option.equals(option);
        }

        @Override
        public int hashCode() {
            return option.id();
        }

        Bootstrap apply(Bootstrap bootstrap) {
            return bootstrap.option(option, value);
        }

        ServerBootstrap apply(ServerBootstrap bootstrap) {
            return bootstrap.option(option, value);
        }

        public String toString() {
            return option + "=" + value;
        }
    }
}
