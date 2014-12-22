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
