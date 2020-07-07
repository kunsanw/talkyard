/**
 * Copyright (c) 2020 Kaj Magnus Lindberg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package debiki.onebox.engines

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki.Globals
import debiki.onebox._
import org.scalactic.{Bad, Good, Or}
import play.api.libs.json.JsObject
import play.api.libs.ws.WSRequest
import scala.concurrent.Future

// SHOULD_CODE_REVIEW this whole file.

// Visit linked page, look for oEmbed and OpenGraph tags
// + inline links:  Repl w: "Linked Page Title (domain.name.com)"  ?

// whitelist, to allow more scripts / top nav / etc?
// blacklist

// Ty can support oEmbed itself?
//   https://wordpress.org/plugins/wpsso/
// LinkedIn likes better than FB:
//    https://surniaulula.com/2019/standards/og/linkedin-prefers-oembed-data-instead-of-open-graph/

// + opengraph, ex;
//   https://wordpress.org/support/topic/social-sharing-on-linkedin-description-not-appearing/

// Commercial:  https://embed.ly  +  https://iframely.com
// but iframely = OSS too:
//   https://github.com/itteco/iframely/tree/master/plugins/domains

// Discourse: https://meta.discourse.org/t/rich-link-previews-with-onebox/98088

// Java:
//   https://github.com/michael-simons/java-oembed
//   https://github.com/vnesek/nmote-oembed

// + lang, width?

/** For now: Twitter only.
  *
  * Later: oEmbed works for FB, Insta, Mediumm, Reddit, "everything" — just change
  * the regex, + map urls to the correct oEmbed provider endpoints.
  */
abstract class OEmbedPrevwRendrEng(globals: Globals, siteId: SiteId, mayHttpFetch: Boolean)
  extends ExternalFetchLinkPrevwRendrEng(
        globals, siteId = siteId, mayHttpFetch = mayHttpFetch) {

  val extraLnPvCssClasses: String = "s_LnPv-oEmb " + providerLnPvCssClassName

  def providerName: Option[String]

  def widgetName: String

  def providerLnPvCssClassName: String

  def providerEndpoint: String

  def queryParamsEndAmp = "max_width=600&"

  def moreQueryParamsEndAmp = ""

  override def sandboxInIframe = true


  def loadAndRender(params: RenderPreviewParams): Future[String Or LinkPreviewProblem] = {
    val unsafeUrl: String = params.unsafeUrl

    def provdrOrUnk = providerName getOrElse "oEmbed provider"  // I18N
    def providerWidget = s"$provdrOrUnk $widgetName"

    // This'll look like e.g. "Twitter tweet not found: ...the-url...".  I18N
    def notFoundError = s"$providerWidget not found: "
    def networkError = s"Error getting a $providerWidget preview: Got no response, url: "
    def rateLimitedError = s"Rate limited by $provdrOrUnk, cannot show: "
    def weirdStatusError(x: Int) = s"Unexpected $provdrOrUnk http status code: $x, url: "
    def noHtmlInOEmbed = s"No html in $providerWidget API response, url: "

    val downloadUrl =
          s"$providerEndpoint?$queryParamsEndAmp${moreQueryParamsEndAmp}url=$unsafeUrl"

    params.loadPreviewFromDb(downloadUrl) foreach { cachedPreview =>
      UX; SHOULD // not cache 404 and other errors for too long
      SECURITY; SHOULD // rate limit each user, and each site, + max db table rows per site?
      cachedPreview.status_code_c match {
        case 200 => // ok
        case 404 =>
          return FutBad(LinkPreviewProblem(
                notFoundError, unsafeUrl = unsafeUrl, errorCode = "TyELNPV404"))
        case 429 =>
          return FutBad(LinkPreviewProblem(
                rateLimitedError, unsafeUrl = unsafeUrl, errorCode = "TyELNPV429"))
        case 0 =>
          // Currently won't happen, [ln_pv_netw_err].
          return FutBad(LinkPreviewProblem(
                networkError, unsafeUrl = unsafeUrl, errorCode = "TyELNP0"))
        case x =>
          return FutBad(LinkPreviewProblem(
                weirdStatusError(x), unsafeUrl = unsafeUrl, errorCode = "TyELNPVWUNK"))
      }

      val unsafeHtml = (cachedPreview.content_json_c \ "html").asOpt[String] getOrElse {
        return FutBad(LinkPreviewProblem(
                  noHtmlInOEmbed, unsafeUrl = unsafeUrl, errorCode = "TyELNPV0HTML"))
      }

      return FutGood(unsafeHtml)
    }

    if (!params.mayHttpFetch) {
      // This can happen if one types and saves a new post really fast, before
      // preview data has been downloaded? (so not yet found in cache above)
      return FutBad(LinkPreviewProblem(
            s"No preview for $providerWidget: ", // [0LNPV]
            unsafeUrl = unsafeUrl, errorCode = "TyE0LNPV"))
    }

    val request: WSRequest = globals.wsClient.url(downloadUrl)

    request.get().map({ r: request.Response =>
      // These can be problems with the provider, rather than Talkyard? E.g. if
      // a Twitter tweet is gone, that's interesting to know for the site visitors.
      var problem = r.status match {
        case 404 => notFoundError
        case 429 => rateLimitedError
        case 200 => "" // continue below
        case x => weirdStatusError(x)
      }

      // There has to be a max-json-length restriction. There's ths db constraint:
      val dbJsonMaxLength = 27*1000 // [oEmb_json_len]
      if (problem.isEmpty && r.bodyAsBytes.length > (dbJsonMaxLength - 2000)) {
        problem = s"Too large $provdrOrUnk oEmbed response: ${r.bodyAsBytes.length} bytes json"
      }

      val unsafeJsObj: JsObject = if (problem.nonEmpty) JsObject(Nil) else {
        try {
          // What does r.json do if the response wasn't json?
          r.json match {
            case jo: JsObject => jo
            case _ =>
              problem = s"Got $provdrOrUnk json but it's not a json obj, request url: "
              JsObject(Nil)
          }
        }
        catch {
          case ex: Exception =>
            problem = s"$provdrOrUnk response not json, request url: "
            JsObject(Nil)
        }
      }

      val anyUnsafeHtml =
            if (problem.isEmpty) (unsafeJsObj \ "html").asOpt[String]
            else None

      if (problem.isEmpty && anyUnsafeHtml.isEmpty) {
        problem = noHtmlInOEmbed
      }

      val result: String Or LinkPreviewProblem = {
        if (problem.nonEmpty) {
          Bad(LinkPreviewProblem(
                problem, unsafeUrl = unsafeUrl, errorCode = "TyELNPVRSP"))
        }
        else {
          SECURITY // incl in quota? num preview links * X
          val preview = LinkPreview(  // mabye Ty SCRIPT tag instead?
                link_url_c = unsafeUrl,
                downloaded_from_url_c = downloadUrl,
                downloaded_at_c = globals.now(),
                status_code_c = r.status,
                preview_type_c = LinkPreviewTypes.OEmbed,
                first_linked_by_id_c = params.requesterId,
                content_json_c = unsafeJsObj)
          params.savePreviewInDb(preview)

          Good(anyUnsafeHtml getOrDie "TyE6986SK")
        }
      }

      result

    })(globals.executionContext).recover({
      case ex: Exception =>
        // Maybe save with status code 0? [ln_pv_netw_err]
        logger.warn("Error creating oEmbed link preview [TyEOEMB897235]", ex)
        Bad(LinkPreviewProblem(
              ex.getMessage, unsafeUrl = unsafeUrl, errorCode = "TyE0EMBNETW"))
    })(globals.executionContext)
  }
}

