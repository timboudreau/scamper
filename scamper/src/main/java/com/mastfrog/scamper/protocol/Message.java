package com.mastfrog.scamper.protocol;

import java.util.Objects;

/**
 * A message to send, comprising the message type and a payload which
 * can be encoded by the bound Codec (by default BSON via Jackson).
 *
 * @author Tim Boudreau
 */
public final class Message<T> {

    public final MessageType type;
    public final T body;

    Message(MessageType type, T obj) {
        this.body = obj;
        this.type = type;
    }

    public String toString() {
        return type + " " + body;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 41 * hash + Objects.hashCode(this.body);
        hash = 41 * hash + Objects.hashCode(this.type);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final Message<?> other = (Message<?>) obj;
        if (!Objects.equals(this.body, other.body)) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        return true;
    }

}
