package com.hypertino.facade.utils

import java.io.{StringReader, StringWriter}

import com.hypertino.binders.value.Text
import com.hypertino.facade.model.FacadeHeaders
import com.hypertino.hyperbus.model.headers.PlainHeadersConverter
import com.hypertino.hyperbus.model.{DynamicBody, DynamicMessage, DynamicRequest, DynamicResponse, EmptyBody, HRL, Header, Headers, NoContent}
import com.hypertino.hyperbus.serialization.{JsonContentTypeConverter, MessageReader}
import com.hypertino.hyperbus.util.{IdGenerator, SeqGenerator}
import spray.http.{HttpEntity, HttpRequest, HttpResponse, StatusCode}
import spray.can.websocket.frame.{Frame, TextFrame}
import spray.http.HttpCharsets._
import spray.http.HttpHeaders.RawHeader
import spray.http.MediaTypes._
import spray.http._

object MessageTransformer {
  def frameToRequest(frame: Frame, remoteAddress: String, httpRequest: HttpRequest): DynamicRequest = {
    val dynamicRequest = MessageReader.fromString(frame.payload.utf8String, DynamicRequest.apply)

    val uri = spray.http.Uri(dynamicRequest.headers.hrl.location)
    if (uri.scheme.nonEmpty || uri.authority.nonEmpty) {
      throw new IllegalArgumentException(s"Uri $uri has invalid format. Only path and query is allowed.")
    }

    // if location contains query, then parse it plus query if specified
    val hrl = HRL.fromURL(dynamicRequest.headers.hrl.location)
    val hrlWithQuery = hrl.copy(
      query = dynamicRequest.headers.hrl.query + hrl.query
    )

    dynamicRequest.copy(
      headers = Headers
        .builder
        .++=(dynamicRequest.headers)
        .++=(httpRequest.headers.map(kv ⇒ kv.name → Text(kv.value)))
        .+=(FacadeHeaders.REMOTE_ADDRESS → remoteAddress)
        .withHRL(hrlWithQuery)
        .requestHeaders()
    )
  }

  def httpToRequest(request: HttpRequest, remoteAddress: String): DynamicRequest = {
    val hrl = HRL.fromURL(request.uri.toString)
    val body = if (request.entity.isEmpty)
      EmptyBody
    else {
      DynamicBody(new StringReader(request.entity.asString), None) // todo: content type from headers? !!!!
    }

    val headers = Headers
      .builder
      .++=(request.headers.map(kv ⇒ kv.name → Text(kv.value)))
      .+=(FacadeHeaders.REMOTE_ADDRESS → remoteAddress)
      .withHRL(hrl)
      .withMethod(request.method.value.toLowerCase)
      .requestHeaders()

    val headersWithContext = headers
      .get(Header.CORRELATION_ID)
      .orElse(headers.get(Header.MESSAGE_ID))
      .map { _ ⇒
        headers
      }
      .getOrElse {
        val messageId = SeqGenerator.create()
        Headers
          .builder
          .++=(headers)
          .withMessageId(messageId)
          .requestHeaders()
      }


    DynamicRequest(body, headersWithContext)
  }


  def messageToFrame(message: DynamicMessage): Frame = {
    TextFrame(message.serializeToString)
  }

  def messageToHttpResponse(response: DynamicResponse): HttpResponse = {
    val responseData = response match {
      case NoContent(_) ⇒ HttpData.Empty
      case _ ⇒
        val bodyWriter = new StringWriter()
        try {
          response.body.serialize(bodyWriter)
          HttpData(bodyWriter.toString)
        }
        finally {
          bodyWriter.close()
        }
    }


    val headers = PlainHeadersConverter.toHttp(response.headers.underlying)
    HttpResponse(StatusCode.int2StatusCode(response.headers.statusCode),
      HttpEntity(contentTypeToSpray(response.headers.contentType), responseData), headers.map { case (name, value) ⇒
        RawHeader(name, value)
      }.toList
    )
  }

  private def contentTypeToSpray(contentType: Option[String]): spray.http.ContentType = {
    contentType match {
      case None ⇒
        spray.http.ContentType(`application/json`, `UTF-8`)

      case Some(localContentType) ⇒
        val dynamicContentType = JsonContentTypeConverter.localContentTypeToUniversalJson(localContentType).toString
        val indexOfSlash = dynamicContentType.indexOf('/')
        val (mainType, subType) = indexOfSlash match {
          case -1 ⇒
            (dynamicContentType, "")
          case index ⇒
            val mainType = dynamicContentType.substring(0, indexOfSlash)
            val subType = dynamicContentType.substring(indexOfSlash + 1)
            (mainType, subType)
        }
        // todo: why we need to register??? replace with header?
        val mediaType = MediaTypes.register(MediaType.custom(mainType, subType, compressible = true, binary = false))
        spray.http.ContentType(mediaType, `UTF-8`)
    }
  }
}
