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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.mastfrog.giulius.ShutdownHookRegistry;
import static com.mastfrog.scamper.ProtocolModule.GUICE_BINDING_SCAMPER_BOSS_THREADS;
import static com.mastfrog.scamper.ProtocolModule.GUICE_BINDING_SCAMPER_CODEC;
import static com.mastfrog.scamper.ProtocolModule.GUICE_BINDING_SCAMPER_WORKER_THREADS;
import com.mastfrog.util.Codec;
import de.undercouch.bson4jackson.BsonFactory;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Does the basics of initializing various Netty classes, and ensures that if
 * Dependencies.shutdown() is called, all Netty-related thread-pools and
 * connections are shut down cleanly.
 * <p>
 * If you are using ProtocolModule, it will take care of installing one of these
 * on its own.
 *
 * @author Tim Boudreau
 */
final class NettyBootstrapModule extends AbstractModule {

    private final Class<? extends Netty5Handler> adap;
    private final int bossThreads;
    private final int workerThreads;
    private final DataEncoding encoding;
    private final List<com.fasterxml.jackson.databind.Module> jacksonModules;

    public NettyBootstrapModule(Class<? extends Netty5Handler> adap, int bossThreads, int workerThreads, DataEncoding encoding, List<com.fasterxml.jackson.databind.Module> jacksonModules) {
        this.adap = adap;
        this.bossThreads = bossThreads;
        this.workerThreads = workerThreads;
        this.encoding = encoding;
        this.jacksonModules = jacksonModules;
    }

    @Override
    protected void configure() {
        bind(DataEncoding.class).toInstance(encoding);
        switch (encoding) {
            case BSON:
                BsonFactory bsonFactory = new BsonFactory();
                bind(BsonFactory.class).toInstance(bsonFactory);
                ObjectMapper mapper = new ObjectMapper(bsonFactory);
                for (com.fasterxml.jackson.databind.Module m : jacksonModules) {
                    mapper.registerModule(m);
                }
                bind(ObjectMapper.class).annotatedWith(Names.named(GUICE_BINDING_SCAMPER_CODEC)).toInstance(mapper);
                bind(Codec.class).annotatedWith(Names.named(GUICE_BINDING_SCAMPER_CODEC)).to(CodecImpl.class);
                break;
            case JSON:
                ObjectMapper mapper2 = new ObjectMapper();
                for (com.fasterxml.jackson.databind.Module m : jacksonModules) {
                    mapper2.registerModule(m);
                }
                bind(ObjectMapper.class).annotatedWith(Names.named(GUICE_BINDING_SCAMPER_CODEC)).toInstance(mapper2);
                bind(Codec.class).annotatedWith(Names.named(GUICE_BINDING_SCAMPER_CODEC)).to(CodecImpl.class);
                break;
            case JAVA_SERIALIZATION :
                bind(Codec.class).annotatedWith(Names.named(GUICE_BINDING_SCAMPER_CODEC)).to(SerializationCodec.class);
                break;
            default :
                throw new AssertionError(encoding);
        }
        bind(Netty5Handler.class).annotatedWith(Names.named("dispatcher")).to(adap);
        bind(Netty5Handler.class).annotatedWith(Names.named("processor")).to(InboundMessageDecoder.class);
        bind(EventLoopGroup.class).annotatedWith(Names.named(GUICE_BINDING_SCAMPER_BOSS_THREADS)).toInstance(new NioEventLoopGroup(bossThreads));
        bind(EventLoopGroup.class).annotatedWith(Names.named(GUICE_BINDING_SCAMPER_WORKER_THREADS)).toInstance(workerThreads == -1 ? new NioEventLoopGroup() : new NioEventLoopGroup(workerThreads));
        bind(ByteBufAllocator.class).annotatedWith(Names.named(GUICE_BINDING_SCAMPER_CODEC)).toInstance(new PooledByteBufAllocator(true));
        bind(ShutdownHandler.class).asEagerSingleton();
    }

    static class ShutdownHandler implements Runnable {

        private final EventLoopGroup boss;
        private final EventLoopGroup worker;

        @Inject
        ShutdownHandler(@Named(GUICE_BINDING_SCAMPER_BOSS_THREADS) EventLoopGroup boss, @Named(GUICE_BINDING_SCAMPER_WORKER_THREADS) EventLoopGroup worker, ShutdownHookRegistry reg) {
            reg.add(this);
            this.boss = boss;
            this.worker = worker;
        }

        @Override
        public void run() {
            try {
                boss.shutdownGracefully();
            } finally {
                worker.shutdownGracefully();
            }
        }
    }

    private static final class CodecImpl implements Codec {

        private final ObjectMapper mapper;

        @Inject
        public CodecImpl(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public <T> String writeValueAsString(T t) throws IOException {
            return mapper.writeValueAsString(t);
        }

        @Override
        public <T> void writeValue(T t, OutputStream out) throws IOException {
            if (t instanceof Message) {
                throw new IllegalArgumentException("Serializing message is a bug");
            }
            mapper.writeValue(out, t);
        }

        @Override
        public <T> byte[] writeValueAsBytes(T t) throws IOException {
            return mapper.writeValueAsBytes(t);
        }

        @Override
        public <T> T readValue(InputStream in, Class<T> type) throws IOException {
            return mapper.readValue(in, type);
        }
    }

    private static final class SerializationCodec implements Codec {

        @Override
        public <T> String writeValueAsString(T t) throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public <T> void writeValue(T t, OutputStream out) throws IOException {
            try (ObjectOutputStream oout = new ObjectOutputStream(out)) {
                oout.writeObject(t);
            }
        }

        @Override
        public <T> byte[] writeValueAsBytes(T t) throws IOException {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public <T> T readValue(InputStream in, Class<T> type) throws IOException {
            try (ObjectInputStream oin = new ObjectInputStream(in)) {
                return type.cast(oin.readObject());
            } catch (ClassNotFoundException ex) {
                throw new IOException(ex);
            }
        }

    }
}
