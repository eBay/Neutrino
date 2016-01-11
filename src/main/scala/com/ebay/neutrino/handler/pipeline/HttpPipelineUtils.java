package com.ebay.neutrino.handler.pipeline;

import com.ebay.neutrino.handler.pipeline.HttpInboundPipeline.Result;
import com.ebay.neutrino.handler.pipeline.HttpInboundPipeline.Status;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;

/**
 * Helper class for interacting with the HttpInboundPipeline interfaces.
 */
public abstract class HttpPipelineUtils {
    // Prevent instantiation
    private HttpPipelineUtils() {}

    /**
     * Convert the string-content provided to a byte-buffer
     * TODO - should these be hard-coded to prevent heap-allocation issues
     */
    public static final ByteBuf toByteBuf(final String content) {
        // Allocate some memory for this
        // final ByteBuf buffer = Unpooled.copiedBuffer(content, CharsetUtil.UTF_8);
        final ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeBytes(content.getBytes(CharsetUtil.UTF_8));
        return byteBuf;
    }

    /**
     * Singleton implementations of the common results.
     */
    public static final Result RESULT_SKIPPED = new Result() {
        public Status status() { return Status.SKIPPED; }
        public HttpResponse response() { return null; }
    };

    public static final Result RESULT_CONTINUE = new Result() {
        public Status status() { return Status.CONTINUE; }
        public HttpResponse response() { return null; }
    };

    public static final Result RESULT_COMPLETE = new Result() {
        public Status status() { return Status.COMPLETE; }
        public HttpResponse response() { return null; }
    };

    /**
     * Create a failed-result with the provided response details.
     *
     * @param status HTTP status
     * @param contentType one of the legal response header CONTENT_TYPE values.
     * @param content error message to user. It is important not to divulge stack trace or other internal details.
     * @return
     */
    public static Result reject(final HttpResponseStatus status, final String contentType, final String content) {
        return new Result() {
            public Status status() { return Status.REJECT; }

            // I think we should rely on the ops pipelines to 'enforce' the correct default outbound headers
            public HttpResponse response() {
                final HttpResponse resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, toByteBuf(content));
                resp.headers().add(HttpHeaderNames.CONTENT_TYPE, contentType);
                return resp;
            }
        };
    }

    /**
     * Create a failed-result with the provided response details. Courtesy shortcut method.
     *
     * @param contentType one of the legal response header CONTENT_TYPE values.
     * @param content error message to user. It is important not to divulge stack trace or other internal details.
     * @return
     */
    public static Result reject(final String contentType, final String content) {
        return reject( HttpResponseStatus.OK, contentType, content );
    }

    /**
     * Create a failed-result with the provided response details. Courtesy shortcut method.
     *
     * @param status HTTP status
     * @return
     */
    public static Result reject(final HttpResponseStatus status) {
        return reject(status, "text/plain; charset=UTF-8", "Reject: " + status.toString() + "\r\n");
    }
}