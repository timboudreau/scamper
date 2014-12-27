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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Wrapper for an address, serializable as JSON.
 *
 * @author Tim Boudreau
 */
public class Address {
    public final String host;
    public final int port;

    public Address(@JsonProperty(value = "origin") String origin, @JsonProperty(value = "port") int port) {
        this.host = origin;
        this.port = port;
    }
    
    public Address(InetSocketAddress a) {
        this(a.getAddress().getHostAddress(), a.getPort());
    }
    
    public InetSocketAddress toSocketAddress() {
        return InetSocketAddress.createUnresolved(host, port);
    }

    public String toString() {
        return host + ":" + port;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.host);
        hash = 37 * hash + this.port;
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
        return this.port == other.port;
    }
}
