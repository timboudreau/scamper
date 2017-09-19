package com.mastfrog.scamper.compression;

import com.google.inject.AbstractModule;
import com.mastfrog.giulius.tests.GuiceRunner;
import com.mastfrog.giulius.tests.TestWith;
import com.mastfrog.scamper.Message;
import com.mastfrog.scamper.MessageHandler;
import com.mastfrog.scamper.MessageType;
import com.mastfrog.scamper.MessageTypeAndBuffer;
import com.mastfrog.scamper.MessageTypeRegistry;
import com.mastfrog.scamper.ProtocolModule;
import com.mastfrog.scamper.compression.CompressingCodecTest.M;
import com.mastfrog.util.Streams;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 *
 * @author Tim Boudreau
 */
@RunWith(GuiceRunner.class)
@TestWith(M.class)
public class CompressingCodecTest {

    private io.netty.channel.Channel fakeChannel() {
        return new io.netty.channel.embedded.EmbeddedChannel();
    }

    private io.netty.channel.ChannelHandlerContext fakeChannelContext() {
        return fakeChannel().pipeline().context("x");
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
    public void test(CompressingCodec codec, MessageTypeRegistry reg) throws Throwable {
        String testData = "ABCD 123 Hello world I have some trees in my elbow and you are blue";
        ByteBuf toEncode = bufFor(testData);
        assertEquals(testData, dataFrom(toEncode));
        ByteBuf encoded = codec.encode(TYPE, toEncode, fakeChannel());
//        System.out.println("ENCODED TO " + dataFrom(encoded) + " bytes " + dataFrom(encoded));
//        assertEquals(codec.magicNumber(), encoded.readByte());
//        assertEquals(TYPE, reg.forByteBuf(encoded));
//        encoded.resetReaderIndex();
        assertEquals(0, encoded.readerIndex());

        MessageTypeAndBuffer result = codec.decode(encoded, fakeChannelContext(), 0);
        assertEquals(TYPE, result.messageType);
        assertEquals(testData, dataFrom(result.buf));
    }

    private static final MessageType TYPE = new MessageType("foo", 14, 23);

    static final class M extends AbstractModule {

        @Override
        protected void configure() {
            install(new ProtocolModule().bind(TYPE, MH.class));
            install(new CompressionModule());
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
