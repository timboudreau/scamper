package com.mastfrog.scamper.protocol;

import com.google.inject.AbstractModule;
import com.mastfrog.scamper.Address;
import com.mastfrog.settings.Settings;
import com.mastfrog.util.ConfigurationError;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Entry point for bootstrapping an SCTP client or server.
 *
 * @author Tim Boudreau
 */
public class ProtocolModule extends AbstractModule {

//    private final Address address;
    private final Random rand;
    private final SecureRandom secureRandom;
    private boolean configureRan;
    private final int bossThreads;
    private final int workerThreads;

//    public ProtocolModule(Settings settings) {
//        String addr = settings.getString("myAddress");
//        if (addr == null) {
//            addr = "127.0.0.1";
//        }
//        int port = settings.getInt("port", 8007);
//        this.address = new Address(addr, port);
    public ProtocolModule() {
        this(2, -1);
    }

    public ProtocolModule(int bossThreads, int workerThreads) {
        secureRandom = new SecureRandom();
        rand = new Random(secureRandom.nextLong());
        this.bossThreads = bossThreads;
        this.workerThreads = workerThreads;
    }

    void addEntry(Entry entry) {
        bind(entry.message, entry.type);
    }

    private final List<Entry> entries = new LinkedList<>();

    /**
     * Add a handler type which will receive messages that match the passed
     * message type.
     *
     * @param type The message type
     * @param handlerType The handler that should be instantiated to handle them
     * @return this
     */
    public ProtocolModule bind(MessageType type, Class<? extends MessageHandler<?, ?>> handlerType) {
        if (configureRan) {
            throw new IllegalStateException("Cannot bind after injector creation");
        }
        for (Entry entry : entries) {
            if (entry.type.equals(type)) {
                throw new ConfigurationError(entry.type + " was already "
                        + "registered for " + type);
            }
        }
        entries.add(new Entry(type, handlerType));
        return this;
    }

    @Override
    protected void configure() {
        configureRan = true;
        install(new NettyBootstrapModule(MessageAdapter.class, bossThreads, workerThreads));
//        bind(Address.class).toInstance(address);
        bind(Random.class).toInstance(rand);
        bind(SecureRandom.class).toInstance(secureRandom);
        Set<MessageType> allTypes = new HashSet<>();
        MessageHandlerMapping.Builder bldr = new MessageHandlerMapping.Builder();
        for (Entry e : entries) {
            allTypes.add(e.message);
            bldr.add(e.message, e.type);
        }
        bind(MessageHandlerMapping.class).toInstance(bldr.build());
        bind(MessageTypeRegistry.class).toInstance(new MessageTypeRegistry(allTypes));
    }

    static final class Entry {

        final MessageType message;
        final Class<? extends MessageHandler<?, ?>> type;

        public Entry(MessageType message, Class<? extends MessageHandler<?, ?>> type) {
            this.message = message;
            this.type = type;
        }
    }
}
