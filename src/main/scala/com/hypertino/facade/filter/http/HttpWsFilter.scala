package com.hypertino.facade.filter.http

import com.hypertino.binders.value._
import com.hypertino.facade.FacadeConfigPaths
import com.hypertino.facade.filter.model.{EventFilter, ResponseFilter}
import com.hypertino.facade.filter.parser.ExpressionEvaluator
import com.hypertino.facade.model._
import com.hypertino.facade.raml.RamlConfiguration
import com.hypertino.facade.utils.HrlTransformer
import com.hypertino.hyperbus.model.{DynamicBody, DynamicMessage, DynamicRequest, DynamicResponse, HRL, Header, HeaderHRL, Headers, HeadersMap, RequestHeaders, ResponseHeaders, StandardResponse}
import com.typesafe.config.Config

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.concurrent.{ExecutionContext, Future}

class HttpWsResponseFilter(config: Config,
                           protected val expressionEvaluator: ExpressionEvaluator) extends ResponseFilter {
  protected val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)

  override def apply(contextWithRequest: RequestContext, response: DynamicResponse)
                    (implicit ec: ExecutionContext): Future[DynamicResponse] = {
    Future {
      //todo: implement rewriting back
      val (headersObj1, body1) = HttpWsFilter.wrapCollection(contextWithRequest, response, hrl ⇒ hrl)
      val (headersObj2, body2) = HttpWsFilter.filterMessage(headersObj1, body1,
        hrl ⇒ hrl
      )
      StandardResponse(body2, ResponseHeaders(headersObj2)).asInstanceOf[DynamicResponse]
    }
  }
}

class WsEventFilter(config: Config, ramlConfig: RamlConfiguration,
                    protected val expressionEvaluator: ExpressionEvaluator) extends EventFilter {
  protected val rewriteCountLimit = config.getInt(FacadeConfigPaths.REWRITE_COUNT_LIMIT)
  override def apply(contextWithRequest: RequestContext, request: DynamicRequest)
                    (implicit ec: ExecutionContext): Future[DynamicRequest] = {
    Future {


      val (newHeaders, newBody) = HttpWsFilter.filterMessage(request.headers.underlying, request.body, hrl ⇒ hrl) // todo: root/baseUri
      val n = Headers
        .builder
        .++=(newHeaders)
        .withHRL(request.headers.hrl)
        .result()
      DynamicRequest(newBody, RequestHeaders(n))
    }
  }
}

object HttpWsFilter {
  private final val MAX_REWRITES=10

  def httpLink(link: Value): String = {
    link match {
      case o @ Obj(links) ⇒
        links.map {
          case(k, v: Obj) ⇒
            "<" + rewriteHrlToHttpUrl(v.to[HRL]).replace(",", "%2C") + ">; rel=" + k
          case(k, other) ⇒
            "<" + other.toString.replace(",", "%2C") + ">; rel=" + k
        } mkString ", "

      case other ⇒ other.toString
    }
  }

  def filterMessage(headers: HeadersMap, body: DynamicBody, uriTransformer: (HRL ⇒ HRL)): (HeadersMap, DynamicBody) = {
    val headersBuilder = Headers.builder

    headers.foreach {
      // todo: transform?
      case (Header.LOCATION, v) ⇒
        headersBuilder += FacadeHeaders.LOCATION → rewriteHrlToHttpUrl(v.to[HRL])

      case (Header.HRL, v) ⇒       // todo: events, remove scheme!!!
        headersBuilder += FacadeHeaders.LOCATION → rewriteHrlToHttpUrl(v.to[HRL])

      case (Header.LINK, v) ⇒
        headersBuilder += FacadeHeaders.LINK → httpLink(v)

      case (Header.COUNT, v) ⇒
        headersBuilder += FacadeHeaders.COUNT → v

      case (k, v) ⇒
        if (FacadeHeaders.directHeaderMapping.contains(k)) {
          headersBuilder += k → v
        }
    }

    val bodyContent = filterBodyContent(body.content)
    val newBody = DynamicBody(bodyContent, body.contentType)
    (headersBuilder.result(), newBody)
  }

  def filterBodyContent(c: Value): Value = {
    c match {
      case o: Obj ⇒
        Obj(
          o.v.map {
            case (k, v) if k.endsWith("_url") ⇒
              k → rewriteHrlToHttpUrl(v)

            case (k, v) ⇒
              k -> filterBodyContent(v)
          }
        )

      case l: Lst ⇒
        Lst(
          l.v.map(filterBodyContent)
        )

      case _ ⇒ c
    }
  }

  def rewriteHrlToHttpUrl(v: Value): Value = v match {
    case v: Obj if v.contains(HeaderHRL.LOCATION) ⇒
      Text(rewriteHrlToHttpUrl(v.to[HRL]))

    case v: Text ⇒
      Text(rewriteHrlToHttpUrl(HRL.fromURL(v.v)))

    case _ ⇒ v
  }

  def rewriteHrlToHttpUrl(hrl: HRL): String = {
    HrlTransformer.rewriteLinkToOriginal(hrl, MAX_REWRITES).toURL()
  }

  // todo: move wrap_collection to separate filter & make configurable
  def wrapCollection(contextWithRequest: RequestContext,
                    message: DynamicMessage, uriTransformer: (HRL ⇒ HRL)): (HeadersMap, DynamicBody) = {

    val wq = contextWithRequest.request.headers.hrl.query.wrap_collection.toBoolean
    if (wq || contextWithRequest.request.headers.get("X-Wrap-Collection").exists(_.toBoolean)) {

      val vx: Map[String, Value] = message.headers.flatMap {
        // todo: transform?
        case (Header.LINK, v: Value) ⇒
          val l: Value = v match {
            case o@Obj(links) ⇒
              Obj(links.map {
                case (k, v: Obj) ⇒
                  k → Text(rewriteHrlToHttpUrl(v.to[HRL]))
                case (k, other) ⇒
                  k → other
              })

            case other ⇒ other
          }
          Some("link" → l)

        case (Header.COUNT, v) ⇒
          Some("count" → v)

        case _ ⇒
          None
      } ++ Map("items" → message.body.content)


      (HeadersMap(message.headers.toSeq: _*), DynamicBody(Obj(vx), message.body.contentType))
    }
    else {
      (HeadersMap(message.headers.toSeq: _*), message.body)
    }
  }
}