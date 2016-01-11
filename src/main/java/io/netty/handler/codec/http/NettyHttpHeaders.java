package io.netty.handler.codec.http;

import io.netty.buffer.ByteBuf;

/**
 * A copy-and-paste reimpleemntation of the Netty HttpHeaders, to decouple
 * it from the HTTP handler.
 *
 * We want to be able to multiplex it on the channel. Note that we would much
 * rather just reuse the existing Netty handler functionality, but it is not
 * easily extensible.
 *
 * @see HttpHeaders
 *
 *
 * Provides the constants for the standard HTTP header names and values and
 * commonly used utility methods that accesses an {@link HttpMessage}.
 */
public abstract class NettyHttpHeaders {
    // Prevent instantiation
    private NettyHttpHeaders() {}


    // Just delegate to the package Netty version (dumb)
    public static void encode(HttpHeaders headers, ByteBuf buf) throws Exception {
        HttpHeaders.encode(headers, buf);
    }


    // Just delegate to the package Netty version (dumb)
    public static void encodeAscii0(CharSequence seq, ByteBuf buf) {
        HttpHeaders.encodeAscii0(seq, buf);
    }
}
