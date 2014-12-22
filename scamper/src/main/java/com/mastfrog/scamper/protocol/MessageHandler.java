package com.mastfrog.scamper.protocol;

import io.netty.channel.ChannelHandlerContext;

/**
 * Handles inbound messages, and can optionally reply with a message.
 * Annotate your implementation with &#064;Singleton if you do not want one
 * created for each request. You can also ask for an instance of Sender to
 * be created 
 *
 * @author Tim Boudreau
 */
public abstract class MessageHandler<T, M> {

    private final Class<M> messageType;

    /**
     * Create a new message handler.
     * @param messageType The type of object that should be decoded from
     * the bytes of the inbound message packet subsequent to the message type
     * header.
     */
    public MessageHandler(Class<M> messageType) {
        this.messageType = messageType;
    }

    /**
     * Called when an inbound message is received.
     * 
     * @param data The message to send
     * @param ctx The channel context, from which the remote address and
     * other information can be retrieved.
     * @return A message to send in reply, or null to send no reply.
     */
    public abstract Message<T> onMessage(Message<M> data, ChannelHandlerContext ctx);

    public Class<M> messageType() {
        return messageType;
    }
}
