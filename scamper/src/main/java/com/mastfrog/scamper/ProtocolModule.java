/*
 * Copyright (c) 2014 Tim Boudreau
 *
 * This file is part of Scamper.
 *
 * Scamper is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.mastfrog.scamper;

import com.google.inject.AbstractModule;
import com.mastfrog.util.ConfigurationError;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Entry point for bootstrapping an SCTP client or server.
 * {@link SctpServerAndClientBuilder} is simpler for most cases, but if you want
 * full control of initialization, you can use this.
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
    private final boolean useBson;

    public ProtocolModule() {
        this(1, 8, true);
    }

    public ProtocolModule(int bossThreads, int workerThreads, boolean useBson) {
        this.bossThreads = bossThreads;
        this.workerThreads = workerThreads;
        this.useBson = useBson;
        secureRandom = new SecureRandom();
        rand = new Random(secureRandom.nextLong());
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
            if (entry.message.equals(type)) {
                throw new ConfigurationError(entry.type + " was already "
                        + "registered for " + type);
            }
        }
        entries.add(new Entry(type, handlerType));
        return this;
    }

    @Override
    protected void configure() {
        // Set the flag so code can't try to bind more handlers after we're
        // up and running - Guice doens't allow dynamic bindings
        configureRan = true;
        // Bootstrap the basics
        install(new NettyBootstrapModule(MessageDispatcher.class, bossThreads, workerThreads, useBson));
        // Used for a few things
        bind(Random.class).toInstance(rand);
        bind(SecureRandom.class).toInstance(secureRandom);
        // Collect all the types registered
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
