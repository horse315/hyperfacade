package eu.inn.facade.filter.raml

import eu.inn.facade.model._
import eu.inn.facade.raml.UriMatcher
import eu.inn.facade.raml.annotationtypes.rewrite
import eu.inn.facade.utils.UriTransformer
import eu.inn.hyperbus.transport.api.uri.Uri

import scala.concurrent.{ExecutionContext, Future}

class RewriteRequestFilter(val args: rewrite) extends RequestFilter {
  override def apply(context: FacadeRequestContext, request: FacadeRequest)(implicit ec: ExecutionContext): Future[FacadeRequest] = {
    val rewrittenUri = UriTransformer.rewrite(request.uri, Uri(args.getUri))
    val rewrittenRequest = request.copy(
      uri = rewrittenUri
    )
    Future.successful(rewrittenRequest)
  }
}

class RewriteEventFilter(val args: rewrite, rewriteCountLimit: Int) extends EventFilter {
  override def apply(context: FacadeRequestContext, event: FacadeRequest)
                    (implicit ec: ExecutionContext): Future[FacadeRequest] = {

    if (UriMatcher.matchUri(args.getUri, event.uri).isDefined) {
      context.prepared match {
        case Some(r) ⇒
          Future.successful(event.copy(uri = r.requestUri))
        case None ⇒
          Future.successful(event)
      }
    }
    else {
      val newUri = UriTransformer.rewriteLinkToOriginal(event.uri, rewriteCountLimit)
      Future.successful(event.copy(uri = newUri))
    }
  }
}

/*
"uri":{"pattern":"/revault/content/{path:*}","args":{"path":"services/status-monitor/app-statuses/9"}}
*/