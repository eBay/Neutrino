package com.ebay.neutrino.handler

import com.ebay.neutrino.NeutrinoRequest
import com.ebay.neutrino.handler.pipeline.{HttpInboundPipeline, HttpPipelineUtils, NeutrinoInboundPipelineHandler}
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.HttpResponseStatus

/**
 * Simulated handler representing a more complex handler execution that (eventually) we'll
 * want to insert at this stage (both inbound and outbound)
 *
 */
@Sharable
class ExamplePipelineHandler extends NeutrinoInboundPipelineHandler with StrictLogging {

  // TODO implement a custom pipeline-event handler
  // (pipeline completed (success || ex))

  // Pipeline:
  // 0) TCP / HTTP port selector/multiplexorec
  //    - should replace pipeline with one or the other
  // 1) HTTP Header extractor
  //    - extract first line (uri details)
  //    - frame around headers with indexes into the byte-buffer
  //    - collect the headers
  //    - once headers are collected, transition into transfer handler
  // 2) Transfer handler
  //    - pass through additional data as-is (w. optional high-order framing)
  // 3) Pipeline handler
  //    - turn off auto-read here if delegating externally
  // 4) Statistics
  // 5) Timeout handler
  // 6) Transit handler
  //
  // ALSO - needs bidirectional (response pipeline, response statistics) support eventually
  //
  /**
   * Execute the pipeline functionality, and return a conditional response.
   *
   * @param request
   */
  override def execute(request: NeutrinoRequest): HttpInboundPipeline.Result = {

    // Log the pipeline call, and remove ourself from the pipeline for future execution
    logger.info("Pipeline inbound processing for HTTP request {}", request.requestUri)



    // Example of setting the downstream pool
    if (request.pool.set("default").isDefined)
      HttpPipelineUtils.RESULT_CONTINUE
    else
      HttpPipelineUtils.reject(HttpResponseStatus.BAD_REQUEST)
  }
}