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
package com.mastfrog.scamper.protocol;

import com.mastfrog.giulius.Dependencies;

/**
 * Object returned by SctpServerAndClientBuilder which can be used to cleanly
 * shut down the server or client, shutting down all related event loops and
 * (ideally) allowing the entire thing to be shut down and if unreferenced, be
 * garbage collected.
 *
 * @author Tim Boudreau
 */
public interface Control<T> {

    /**
     * Shut down the server/client/sender
     */
    void shutdown();

    /**
     * Get the object that was created
     *
     * @return The object
     */
    T get();

    /**
     * Get the Guice injector if objects are needed from it
     *
     * @return A wrapper for the injector
     */
    Dependencies getInjector();

}
