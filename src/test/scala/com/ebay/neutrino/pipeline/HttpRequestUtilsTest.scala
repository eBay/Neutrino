package com.ebay.neutrino.config

import com.ebay.neutrino.util.HttpRequestUtils
import io.netty.handler.codec.http._
import org.scalatest.FlatSpec


class HttpRequestSupportTest extends FlatSpec {

  val ver = HttpVersion.HTTP_1_1
  val get = HttpMethod.GET


  it should "URI() should parse a valid request" in {
    import HttpRequestUtils.HttpRequestSupport

    // Check relative URI only
    { val uri = new DefaultHttpRequest(ver, get, "/").URI()
      assert(!uri.isAbsolute)
      assert(uri.getHost == null)
      assert(uri.getPort == -1)
    }

    // Check absolute URI only
    { val uri = new DefaultHttpRequest(ver, get, "http://localhost").URI()
      assert(uri.isAbsolute)
      assert(uri.getHost == "localhost")
      assert(uri.getPort == -1)
    }

    // Check absolute URI and port only
    { val uri = new DefaultHttpRequest(ver, get, "http://localhost:2344").URI()
      assert(uri.isAbsolute)
      assert(uri.getHost == "localhost")
      assert(uri.getPort == 2344)
    }

    // Check absolute URI with port
    { val uri = new DefaultHttpRequest(ver, get, "http://localhost:2344/some/uri").URI()
      assert(uri.isAbsolute)
      assert(uri.getHost == "localhost")
      assert(uri.getPort == 2344)
      assert(uri.getPath == "/some/uri")
    }

    // Check relative URI
    { val uri = new DefaultHttpRequest(ver, get, "/some/uri").URI()
      assert(!uri.isAbsolute)
      assert(uri.getHost == null)
      assert(uri.getPort == -1)
      assert(uri.getPath == "/some/uri")
    }

    // Check fmt errors
  }
}