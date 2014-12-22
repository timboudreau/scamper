package com.mastfrog.scamper.queries;

import com.mastfrog.scamper.Address;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Arrays;
import java.util.Objects;

/**
 *
 * @author Tim Boudreau
 */
public class Answer<A> {
    public final A answer;
    public final Address origin;
    public final byte[] signature;
    public final short timeOffset;
    public final byte hop;
    public final Address[] cc;

    public Answer(@JsonProperty(value = "answer") A answer, @JsonProperty(value = "origin") Address origin, @JsonProperty(value = "signature") byte[] signature, @JsonProperty(value = "timeOffset") short timeOffset, @JsonProperty(value = "hop") byte hop, @JsonProperty(value = "cc") Address[] cc) {
        this.answer = answer;
        this.origin = origin;
        this.signature = signature;
        this.timeOffset = timeOffset;
        this.hop = hop;
        this.cc = cc;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 79 * hash + Objects.hashCode(this.answer);
        hash = 79 * hash + Objects.hashCode(this.origin);
        hash = 79 * hash + Arrays.hashCode(this.signature);
        hash = 79 * hash + this.timeOffset;
        hash = 79 * hash + this.hop;
        hash = 79 * hash + Arrays.deepHashCode(this.cc);
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
        final Answer<?> other = (Answer<?>) obj;
        if (!Objects.equals(this.answer, other.answer)) {
            return false;
        }
        if (!Objects.equals(this.origin, other.origin)) {
            return false;
        }
        if (!Arrays.equals(this.signature, other.signature)) {
            return false;
        }
        if (this.timeOffset != other.timeOffset) {
            return false;
        }
        if (this.hop != other.hop) {
            return false;
        }
        if (!Arrays.deepEquals(this.cc, other.cc)) {
            return false;
        }
        return true;
    }

}
