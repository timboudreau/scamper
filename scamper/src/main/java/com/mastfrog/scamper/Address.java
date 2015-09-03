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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mastfrog.util.Checks;
import com.mastfrog.util.collections.CollectionUtils;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * Wrapper for an address, serializable as JSON, and capable of representing a
 * multi-homed set of addresses.
 *
 * @author Tim Boudreau
 */
public class Address implements Iterable<Address> {

    public final String host;
    public final int port;
    public final Address[] secondaries;

    @JsonCreator
    public Address(@JsonProperty("host") String host, @JsonProperty("port") int port, @JsonProperty("secondaries") Address... secondaries) {
        Checks.notNull("host", host);
        Checks.nonNegative("port", port);
        Checks.notNull("secondaries", secondaries);
        Checks.noNullElements("secondaries", secondaries);
        this.host = host;
        this.port = port;
        this.secondaries = secondaries == null ? new Address[0] : secondaries;
    }

    public Address(Address... addrs) {
        if (addrs.length == 0) {
            throw new IllegalArgumentException("No addresses");
        }
        this.host = addrs[0].host;
        this.port = addrs[0].port;
        secondaries = new Address[addrs.length - 1];
        System.arraycopy(addrs, 1, secondaries, 0, secondaries.length);
    }

    public Address(Collection<Address> addrs) {
        this(addrs.toArray(new Address[addrs.size()]));
    }

    public Address(InetSocketAddress a, Address... secondaries) {
        this(a.getAddress().getHostAddress(), a.getPort(), secondaries);
    }

    public Address(InetSocketAddress a) {
        this(a.getAddress().getHostAddress(), a.getPort());
    }

    public List<Address> toList() {
        List<Address> result = new ArrayList<>(secondaries.length + 1);
        result.add(new Address(host, port));
        result.addAll(Arrays.asList(secondaries));
        return result;
    }

    public InetSocketAddress resolve() {
        return new InetSocketAddress(host, port);
    }

    public InetSocketAddress toSocketAddress() {
        return InetSocketAddress.createUnresolved(host, port);
    }

    public Address(String hostColonPort) {
        Checks.notNull("hostColonPort", hostColonPort);
        String[] parts = hostColonPort.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("SHould be in format 'host:port'");
        }
        host = parts[0];
        port = Integer.parseInt(parts[1]);
        if (port <= 0) {
            throw new IllegalArgumentException("Port < 0: " + port);
        }
        if (port > 65535) {
            throw new IllegalArgumentException("Port > 65535");
        }
        this.secondaries = new Address[0];
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(host).append(':').append(port);
        if (secondaries.length > 0) {
            sb.append('{');
            for (Iterator<Address> it = CollectionUtils.toIterator(secondaries); it.hasNext();) {
                sb.append(it.next());
                if (it.hasNext()) {
                    sb.append(',');
                }
            }
            sb.append('}');
        }
        return sb.toString();
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.host);
        hash = 37 * hash + this.port;
        hash = 37 * hash + Arrays.hashCode(secondaries);
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
        final Address other = (Address) obj;
        if (!Objects.equals(this.host, other.host)) {
            return false;
        }
        if (!Arrays.equals(secondaries, other.secondaries)) {
            return false;
        }
        return this.port == other.port;
    }

    @Override
    public Iterator<Address> iterator() {
        return CollectionUtils.toIterator(secondaries);
    }
}
