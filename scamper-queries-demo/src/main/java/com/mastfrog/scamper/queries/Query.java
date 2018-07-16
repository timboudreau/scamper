package com.mastfrog.scamper.queries;

import com.mastfrog.scamper.Address;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastfrog.util.preconditions.Checks;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 *
 * @author Tim Boudreau
 */
public class Query<Q, A> {

    public final long id;
    public final Q query;
    public final byte timeToLive;
    public final byte hops;
    public final Address origin;
    public final byte[] signature;
    public final long timestamp;
    public final Answer<A>[] answers;
    public final Address originallySentTo;

    public Query(@JsonProperty("id") long id,
            @JsonProperty("query") Q query,
            @JsonProperty("timeToLive") byte timeToLive,
            @JsonProperty("hops") byte hops,
            @JsonProperty("origin") Address origin,
            @JsonProperty("signature") byte[] signature,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("answers") Answer<A>[] answers,
            @JsonProperty("originallySentTo") Address originallySentTo) {
        Checks.notNull("query", query);
        Checks.notNull("origin", query);
        Checks.notNull("originallySentTo", originallySentTo);
        this.id = id;
        this.query = query;
        this.timeToLive = timeToLive;
        this.hops = hops;
        this.origin = origin;
        this.signature = signature == null ? new byte[0] : signature;
        this.timestamp = timestamp;
        this.answers = answers == null ? new Answer[0] : answers;
        this.originallySentTo = originallySentTo;
    }

    public Query incrementHops() {
        return new Query(id, query, timeToLive, (byte) (hops + 1), origin, signature, timeToLive, answers, originallySentTo);
    }

    public Query withAnswer(Answer<A> answer) {
        Answer[] answers;
        if (this.answers == null || this.answers.length == 0) {
            answers = new Answer[]{answer};
        } else {
            answers = new Answer[this.answers.length + 1];
            System.arraycopy(this.answers, 0, answers, 0, this.answers.length);
            answers[answers.length - 1] = answer;
        }
        return new Query(id, query, timeToLive, hops, origin, signature, timeToLive, answers, originallySentTo);
    }

    public boolean isValid() {
        Set<Address> sentTo = new HashSet<>();
        sentTo.add(originallySentTo);
        for (Answer a : answers) {
            for (Address ad : a.cc) {
                if (!ad.equals(a.origin)) {
                    sentTo.add(ad);
                }
            }
        }
        for (Answer a : answers) {
            // Ensure an answer can't make itself valid by cc'ing itself
            if (!sentTo.contains(a.origin)) {
                return false;
            }
        }
        return true;
    }

    @JsonIgnore
    public boolean isExpired() {
        return hops >= timeToLive;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 59 * hash + (int) (this.id ^ (this.id >>> 32));
        hash = 59 * hash + Objects.hashCode(this.query);
        hash = 59 * hash + this.timeToLive;
        hash = 59 * hash + this.hops;
        hash = 59 * hash + Objects.hashCode(this.origin);
        hash = 59 * hash + Arrays.hashCode(this.signature);
        hash = 59 * hash + (int) (this.timestamp ^ (this.timestamp >>> 32));
        hash = 59 * hash + Arrays.deepHashCode(this.answers);
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
        final Query<?, ?> other = (Query<?, ?>) obj;
        if (this.id != other.id) {
            return false;
        }
        if (!Objects.equals(this.query, other.query)) {
            return false;
        }
        if (this.timeToLive != other.timeToLive) {
            return false;
        }
        if (this.hops != other.hops) {
            return false;
        }
        if (!Objects.equals(this.origin, other.origin)) {
            return false;
        }
        if (!Arrays.equals(this.signature, other.signature)) {
            return false;
        }
        if (this.timestamp != other.timestamp) {
            return false;
        }
        if (!Arrays.deepEquals(this.answers, other.answers)) {
            return false;
        }
        return true;
    }


}
