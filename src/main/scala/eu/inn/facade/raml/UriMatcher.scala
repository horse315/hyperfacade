package eu.inn.facade.raml

import eu.inn.hyperbus.transport.api.uri.{PathMatchType, RegularMatchType, Token, _}

import scala.annotation.tailrec

object UriMatcher {

  /**
    * Matches URI pattern from RAML configuration with request URI
    * @param pattern - URI pattern from RAML configuration
    * @param uri - request URI
    * @return if request URI matches pattern then Some of constructed URI with parameters will be returned, None otherwise
    */
  def matchUri(pattern: String, uri: Uri): Option[Uri] = {
    val requestUriTokens = UriParser.tokens(uri.pattern.specific)
    var args = Map[String, String]()
    val patternTokens = UriParser.tokens(pattern)
    val patternTokenIter = patternTokens.iterator
    val reqUriTokenIter = requestUriTokens.iterator
    var matchesCorrectly = true
    var previousReqUriToken: Option[Token] = None
    while(patternTokenIter.hasNext && reqUriTokenIter.hasNext && matchesCorrectly) {
      val reqUriToken = getRequestUriToken(reqUriTokenIter, previousReqUriToken)
      patternTokenIter.next() match {
        case patternToken @ (TextToken(_) | SlashToken) ⇒
          matchesCorrectly = (patternToken == reqUriToken) &&
                 (patternTokenIter.hasNext == reqUriTokenIter.hasNext)

        case ParameterToken(paramName, RegularMatchType) ⇒ reqUriToken match {
          case requestUriToken @ TextToken(value) ⇒
            args += paramName → value
            matchesCorrectly = patternTokenIter.hasNext == reqUriTokenIter.hasNext
        }

        case patternToken @ ParameterToken(paramName, PathMatchType) ⇒
          reqUriToken match {
            case requestUriToken @ TextToken(value) ⇒ args += paramName → foldUriTail(value, reqUriTokenIter)
          }
      }
      previousReqUriToken = Some(reqUriToken)
    }
    if (!matchesCorrectly) None
    else Some(Uri(pattern, args))
  }

  /**
    * This method removes multiple slashes in request
    */
  def getRequestUriToken(iter: Iterator[Token], previousToken: Option[Token]): Token = {
    iter.next() match {
      case SlashToken ⇒ previousToken match {
        case Some(SlashToken) ⇒
          if (iter.hasNext) getRequestUriToken(iter, previousToken)
          else SlashToken
        case Some(other) ⇒ SlashToken
        case None ⇒ SlashToken
      }

      case other ⇒ other
    }
  }

  /**
    * It's like toString for iterator of URI tokens
    * @param uriTail - string of merged URI tokens
    * @param iter - remaining tokens
    * @return
    */
  @tailrec private def foldUriTail(uriTail: String, iter: Iterator[Token]): String = {
    if (iter.hasNext) {
      val tokenStr = iter.next() match {
        case TextToken(value) ⇒ value
        case SlashToken ⇒ "/"
      }
      foldUriTail(uriTail + tokenStr, iter)
    } else uriTail
  }
}