package com.mastfrog.scamper.compression;

import com.google.inject.AbstractModule;
import com.mastfrog.scamper.codec.MessageCodec;

/**
 *
 * @author Tim Boudreau
 */
public class CompressionModule extends AbstractModule {
    
    public static final String SETTINGS_KEY_COMPRESSION_THRESHOLD = "sctp.compress.threshold";
    public static final int DEFAULT_COMPRESSION_THRESHOLD = 256;

    @Override
    protected void configure() {
        bind(MessageCodec.class).to(AutoCompressCodec.class);
    }
}
