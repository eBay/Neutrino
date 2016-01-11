package com.ebay.neutrino.handler.pipeline;

import com.ebay.neutrino.NeutrinoRequest;
import io.netty.handler.codec.http.HttpResponse;

/**
 * Very very simplified 'generic' pipeline interface for processing HTTP request pipeline
 * functionality.
 *
 * Note that the conditional-check is removed (ie: shouldExecute(request)). Instead, the
 * pipeline should simply return continue.
 *
 * Created by cbrawn on 9/24/14.
 *
 * TODO - rename something (much) better?
 */
public interface HttpInboundPipeline {

    /**
     * Response status:
     *  SKIPPED: pipeline was not executed (did not meet conditions); continue pipeline execution with request
     *  CONTINUE: pipeline was executed successfully; continue pipeline execution with request
     *  COMPLETE: pipeline was executed successfully; skip remainder of pipeline and return request
     *  REJECT: pipeline stage failed; a failure-response is optionally provided.
     */
    public enum Status { SKIPPED, CONTINUE, COMPLETE, REJECT }


    /**
     * Execution result type from the pipeline execution.
     *  Status will always be populated, as one of Status.enum
     *  Response is optional and will only be provided on REJECT status
     */
    public interface Result {
        public Status status();
        public HttpResponse response();
    }


    /**
     * Execute the pipeline functionality, and return a conditional response.
     *
     * @param connection
     * @param request
     */
    public Result execute(final NeutrinoRequest request);
}