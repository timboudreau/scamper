/*
 * Copyright (c) 2015 Tim Boudreau
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
package com.mastfrog.scamper.password.crypto;

import com.google.inject.AbstractModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.scamper.Message;
import com.mastfrog.scamper.MessageHandler;
import com.mastfrog.scamper.MessageType;
import com.mastfrog.scamper.MessageTypeAndBuffer;
import com.mastfrog.scamper.MessageTypeRegistry;
import com.mastfrog.scamper.ProtocolModule;
import com.mastfrog.scamper.codec.MessageCodec;
import com.mastfrog.scamper.password.crypto.EncryptingCodecTest.M;
import com.mastfrog.util.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith(M.class)
public class EncryptingCodecTest {

    private io.netty.channel.Channel fakeChannel() {
        return new io.netty.channel.embedded.EmbeddedChannel();
    }

    private io.netty.channel.ChannelHandlerContext fakeChannelContext() {
        return fakeChannel().pipeline().firstContext();
    }

    private ByteBuf bufFor(String data) throws UnsupportedEncodingException {
        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(data.getBytes("UTF-8"));
        return buf;
    }

    private String dataFrom(ByteBuf buf) throws IOException {
        int old = buf.readerIndex();
        try (ByteBufInputStream in = new ByteBufInputStream(buf)) {
            return Streams.readString(in);
        } finally {
            buf.readerIndex(old);
        }
    }

    @Test
    public void test(MessageCodec codec, MessageTypeRegistry reg) throws Throwable {
        for (int i = 0; i < 10; i++) {
            byte[] add = new byte[i + 1];
            Arrays.fill(add, (byte) 'a');
            String testData = "ABCD 123 Hello world I have some trees in my elbow and you are blue" + new String(add);
            ByteBuf toEncode = bufFor(testData);
            assertEquals(testData, dataFrom(toEncode));
            ByteBuf encoded = codec.encode(TYPE, toEncode, fakeChannel());
            String enc = dataFrom(encoded);
            encoded.resetReaderIndex();
            System.out.println("ENCODED TO " + enc + " bytes " + enc.length());
            assertEquals(0, encoded.readerIndex());

            MessageTypeAndBuffer result = codec.decode(encoded, fakeChannelContext(), 0);
            assertEquals(TYPE, result.messageType);
            assertEquals(testData, dataFrom(result.buf));
        }
    }

    private static final MessageType TYPE = new MessageType("foo", 14, 23);

    static final class M extends AbstractModule {

        @Override
        protected void configure() {
            install(new ProtocolModule().bind(TYPE, MH.class));
            install(new EncryptionModule());
        }
    }

    @SuppressWarnings("unchecked")
    static class MH extends MessageHandler<String, String> {

        MH() {
            super(String.class);
        }

        @Override
        public Message<String> onMessage(Message<String> data, ChannelHandlerContext ctx) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
