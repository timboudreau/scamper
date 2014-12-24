package com.mastfrog.scamper;

/**
 * Options for serialization formats for data.
 *
 * @author Tim Boudreau
 */
public enum DataEncoding {
    /**
     * Use BSON encoding - faster to parse than JSON but not necessarily 
     * smaller
     */
    BSON,
    /**
     * Use JSON encoding - useful for debugging
     */
    JSON,
    /**
     * Use Java serialization.  Note that since class information is sent
     * with the data, for small objects this may be larger than BSON or JSON.
     */
    JAVA_SERIALIZATION
}
