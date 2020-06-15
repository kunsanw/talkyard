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
import debiki.{Globals, Nashorn, TextAndHtml}
import debiki.onebox._
import debiki.TextAndHtml.sanitizeAllowLinksAndBlocks
import org.scalactic.{Bad, ErrorMessage, Good, Or}
import play.api.libs.json.JsObject
import play.api.libs.ws.WSRequest
import scala.concurrent.Future
import scala.util.matching.Regex
import talkyard.server.TyLogging



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
  extends ExternalRequestLinkPreviewEngine(
        globals, siteId = siteId, mayHttpFetch = mayHttpFetch) {

  // ! wow !
  //  https://github.com/michael-simons/java-oembed
  // could be a blog post: Safely oEmbed via srcdoc?

  // https://www.html5rocks.com/en/tutorials/security/sandboxed-iframes/

  val extraLnPvCssClasses: String = "s_LnPv-oEmb " + providerLnPvCssClassName

  def providerName: Option[String]

  def widgetName: String

  def providerLnPvCssClassName: String

  def providerEndpoint: String

  //def sanitizeInsteadOfSandbox = false

  def queryParamsEndAmp = "max_width=600&"

  def moreQueryParamsEndAmp = ""


  /** The oEmbed stuff might include arbitrary html tags and even scripts,
    * so we render it in a sandboxed iframe.
    */
  //override val alreadySanitized = true

  override def sandboxInIframe = true


  //override val alreadyWrappedInAside = true


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

    // omit_script=1  ?
    // theme  = {light, dark}
    // link_color  = #zzz   [ty_themes]
    // lang="en" ... 1st 2 letters in Ty's lang code — except for Chinese:  zh-cn  zh-tw
    // see:
    // https://developer.twitter.com/en/docs/twitter-for-websites/twitter-for-websites-supported-languages/overview
    // dnt  ?

    // Wants:  theme: light / dark.  Primary color / link color.
    // And device:  mobile / tablet / laptop ?  for maxwidth.
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

      // Try get from Redis or mem cache


      /*
      val unsafeProviderName = (cachedPreview.content_json_c \ "provider_name").asOpt[String]

      val safeHtml = {
        if (sanitizeInsteadOfSandbox) {
          TextAndHtml.sanitizeRelaxed(unsafeHtml)
        }
        else {
          SandboxedAutoSizeIframe.makeSafePreviewHtml(
                unsafeUrl = unsafeUrl, unsafeHtml = unsafeHtml,
                unsafeProviderName = unsafeProviderName,
                extraLnPvCssClasses = extraLnPvCssClasses)
        }
      }

      val safeHtmlInAside = LinkPreviewHtml.wrapSafeHtmlInAside(
            safeHtml = safeHtml, extraLnPvCssClasses = extraLnPvCssClasses,
            unsafeUrl = unsafeUrl, unsafeProviderName = unsafeProviderName)
      */

      return FutGood(unsafeHtml) //safeHtmlInAside)
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
          // CACHE in Redis
          Bad(LinkPreviewProblem(
                problem, unsafeUrl = unsafeUrl, errorCode = "TyELNPVRSP"))
        }
        else {
          SECURITY // incl in quota? num preview links * X
          params.savePreviewInDb foreach { fn =>
            val preview = LinkPreview(  // mabye Ty SCRIPT tag instead?
                  link_url_c = unsafeUrl,
                  downloaded_from_url_c = downloadUrl,
                  downloaded_at_c = globals.now(),
                  status_code_c = r.status,
                  preview_type_c = LinkPreviewTypes.OEmbed,
                  first_linked_by_id_c = params.requesterId,
                  content_json_c = unsafeJsObj)
            fn(preview)
          }

          val unsafeHtml = anyUnsafeHtml.getOrDie("TyE6986SK")
          /*
          val unsafeProviderName = (unsafeJsObj \ "provider_name").asOpt[String]

          val safeHtml = {
            if (sanitizeInsteadOfSandbox) {
              TextAndHtml.sanitizeRelaxed(unsafeHtml)
            }
            else {
              SandboxedAutoSizeIframe.makeSafePreviewHtml(
                    unsafeUrl = unsafeUrl, unsafeHtml = unsafeHtml,
                    unsafeProviderName = unsafeProviderName,
                    extraLnPvCssClasses = extraLnPvCssClasses)
            }
          }

          val safeHtmlInAside = LinkPreviewHtml.wrapSafeHtmlInAside(
                safeHtml = safeHtml, extraLnPvCssClasses = extraLnPvCssClasses,
                unsafeUrl = unsafeUrl, unsafeProviderName = unsafeProviderName)
          safeHtmlInAside */
          Good(unsafeHtml)
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


object SandboxedAutoSizeIframe {  // move to where? Own file?

  def makeSafePreviewHtml(unsafeUrl: String, unsafeHtml: String,
        unsafeProviderName: Option[String], extraLnPvCssClasses: String): String = {
          /* Example response json, this from Twitter:
            {
              "url": "https://twitter.com/Interior/status/507185938620219395",
              "author_name": "US Dept of Interior",
              "author_url": "https://twitter.com/Interior",
              "html": "<blockquote class="twitter-tweet"><p lang="en" dir="ltr">Happy 50th anniversary to the Wilderness Act! Here&#39;s a great wilderness photo from <a href="https://twitter.com/YosemiteNPS">@YosemiteNPS</a>. <a href="https://twitter.com/hashtag/Wilderness50?src=hash">#Wilderness50</a> <a href="http://t.co/HMhbyTg18X">pic.twitter.com/HMhbyTg18X</a></p>&mdash; US Dept of Interior (@Interior) <a href="https://twitter.com/Interior/status/507185938620219395">September 3, 2014</a></blockquote>n<script async src="//platform.twitter.com/widgets.js" charset="utf-8"></script>",
              "width": 550,
              "height": null,
              "type": "rich",
              "cache_age": "3153600000",    <—— how handle this?
              "provider_name": "Twitter",
              "provider_url": "https://twitter.com",
              "version": "1.0"
            } */

        // Nice:
        // https://www.html5rocks.com/en/tutorials/security/sandboxed-iframes/

        // Hmm:
        // https://stackoverflow.com/questions/31184505/sandboxing-iframe-and-allow-same-origin

        // org.owasp.encoder.Encode.forHtmlContent(html); — currently using Scala's
        // html/xml support instead, see below (it html-escapes).
        // See: https://html.spec.whatwg.org/multipage/iframe-embed-object.html#attr-iframe-srcdoc
        // Some oEmbed:s want to run scripts (e.g. FB, Twitter); let them do that,
        // but in a sandboxed iframe. But do *not* allow-same-origin  — that'd
        // let those scripts break out from the sandbox and steal session id cookies etc.

        // EDIT: Hmm srcdoc, what domain do they "have"?
        // Both allow-scripts and allow-same-origin should be fine? The iframes
        // don't show contents from the same Talkyard site, and in any case
        // Talkyard doesn't use any unsafe scripts that attacks the same site itself?
        // The Whatwg docs:
        // > Setting both the allow-scripts and allow-same-origin keywords together
        // > when the embedded page has the same origin as the page containing
        // > the iframe allows the embedded page to simply remove the sandbox
        // > attribute and then reload itself, effectively breaking out of the
        // > sandbox altogether.
        // https://html.spec.whatwg.org/multipage/iframe-embed-object.html#the-iframe-element

        // Don't allow window.top redirects, except for maybe from trusted
        // providers. Otherwise, someone could show some type of fake login and
        // then redirect to a pishing site.

        // Hack ex:
        //   https://medium.com/@jonathanbouman/stored-xss-unvalidated-embed-at-medium-com-528b0d6d4982
        // Medium bounty prog:
        //   https://policy.medium.com/mediums-bug-bounty-disclosure-program-34b1c80764c2

        // https://github.com/beefproject/beef

        // Iframe sandbox permissions. [IFRMSNDBX]
        val permissions = (
              // Most? oEmbeds execute Javascript to render themselves — ok, in a sandbox.
              "allow-scripts " +

              // This makes:  <a href=.. target=_blank>  work — opens in new browser tab.
              "allow-popups " +

              // Makes a popup / new-tab opened from the iframe work properly;
              // it won't inherit the iframe sandbox restrictions.
              "allow-popups-to-escape-sandbox " +

              // Lets the iframe access cookies and stuff *from its own domain*,
              // e.g. to know if one is logged in, so they can show one's avatar
              // or other things relevant to oneself in the iframe.
              //
              // This, plus  allow-scripts, would have been unsafe it was served by
              // Talkyard on the Talkyard site's domain itself — it could then have
              // removed the sandbox attribute.
              // https://developer.mozilla.org/en-US/docs/Web/HTML/Element/iframe#attr-sandbox
              // Also:
              // https://stackoverflow.com/questions/31184505/sandboxing-iframe-and-allow-same-origin
              //
              // However! Together with srcdoc=..., seems the iframe *does* get the same
              // origin as the parent window, i.e. Talkyard itself, letting the
              // iframe escape the sandbox? So skip this for now (let previews
              // that require same-origin be broken).
              //
              // "allow-same-origin " +   // no, see above

              // This makes links work, but only if the user actually clicks the links.
              // Javascript in the iframe cannot change the top win location when
              // the user is inactive — that would have made pishing attacks possible:
              // the iframe could silently have replaced the whole page with [a similar
              // looking page on the attacker's domain].
              "allow-top-navigation-by-user-activation")

    <iframe seamless="" sandbox={permissions} srcdoc={
          unsafeHtml + OneboxIframe.adjustOEmbedIframeHeightScript
          }></iframe>.toString

    /*
        <aside class={s"s_LnPv $extraLnPvCssClasses"}
          ><iframe seamless=""
                   sandbox={permissions}
                   srcdoc={unsafeHtml + OneboxIframe.adjustOEmbedIframeHeightScript}
          ></iframe
          ><div class="s_LnPv_ViewAt"
          ><a href={unsafeUrl} target="_blank" rel="nofollow noopener">{
              "View at " + unsafeProviderName.getOrElse(unsafeUrl) /* I18N */
          } <span class="icon-link-ext"></span></a
          ></div
        ></aside>.toString */
  }
}


object OneboxIframe {

  /** The embedding parent window doesn't know how tall this iframe with oEmbed
    * stuff inside wants to be — so let's tell it.
    *
    * We set body.margin = 0, otherwise e.g. Chrome has 8px default margin.
    *
    * We set the top-bottom-margin of elems directly in < body > to '0 auto'
    * to avoid scrollbars. E.g. Twitter otherwise include 10px top & bottom margin.
    * ('auto' to place in the middle.)
    *
    * Need to postMessage(...) to '*', because the domain of this srcdoc=...
    * iframe is "null", i.e. different from the parent frame domain.
    *
    * We don't really know when the oEmbed contents is done loading.
    * So, we send a few messages — and if, after that, if the oEmbed still
    * doesn't have its final size, then, that's a weird oEmbed and someone
    * else's problem, we shouldn't try to fix that.
    */
  // TESTS_MISSING  // create a LinkPrevwRendrEng that creates a 432 px tall div,
  // with 20 px body margin, 20 px child div padding & margin,
  // should become 432 px tall? (margin & padding removed)
  val adjustOEmbedIframeHeightScript: String = s"""
    |<script src="/-/assets/ext-iframe$min.js"></script>
    """.stripMargin

    /*
    |<script>(function(d) { // Talkyard [OEMBHGHT]
    |d.body.style.margin = '0';
    |var nSent = 0;
    |function sendH() {
    |  var h = d.body.clientHeight;
    |  console.debug("Sending oEmbed height: " + h + " [TyMOEMBHGHT]");
    |  try {
    |    var cs = d.querySelectorAll('body > *');
    |    for (var i = 0; i < cs.length; ++i) {
    |      var c = cs[i];
    |      c.style.margin = '0 auto';
    |      c.style.padding = '0';
    |    }
    |  }
    |  catch (ex) {
    |    console.warn("Error removing margin [TyEOEMBMARG]");
    |  }
    |  window.parent.postMessage(['oEmbHeight', h], '*');
    |  nSent += 1;
    |  if (nSent < 4) {
    |    setTimeout(sendH, nSent * 500);
    |  }
    |}
    |setTimeout(sendH, 500);
    |})(document);
    |</script>
    |""".stripMargin */

  private def min = Globals.isDevOrTest ? "" | ".min"
}
