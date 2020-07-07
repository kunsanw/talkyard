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

package debiki.onebox   // RENAME to talkyard.server.links

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki.{Globals, TextAndHtml}
import debiki.onebox.engines._
import debiki.TextAndHtml.safeEncodeForHtml
import debiki.dao.RedisCache
import org.scalactic.{Bad, ErrorMessage, Good, Or}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import scala.util.{Failure, Success}
import talkyard.server.TyLogging



object LinkPreviewHtml {

  private def safeBoringLinkTag(unsafeUrl: String, isOk: Boolean): String = {
    val spaceErrClass = if (!isOk) " s_LnPv_L-Err" else ""
    val safeUrl = safeEncodeForHtml(unsafeUrl)
    s"""<a href="$safeUrl" class="s_LnPv_L$spaceErrClass" """ +
          s"""target="_blank" rel="nofollow noopener">$safeUrl</a>"""
  }


  def safeProblem(unsafeProblem: String, unsafeUrl: String,
      extraLnPvCssClasses: String, errorCode: String = ""): String = {

    val safeProblem = TextAndHtml.safeEncodeForHtmlContentOnly(unsafeProblem)
    val safeLinkTag = safeBoringLinkTag(unsafeUrl, isOk = false)
    val errInBrackets = if (errorCode.isEmpty) "" else s" <code>[$errorCode]</code>"
    val safeHtml =
          s"""<aside class="onebox s_LnPv s_LnPv-Err $extraLnPvCssClasses clearfix">${
            safeProblem} $safeLinkTag$errInBrackets</aside>"""

    // safeHtml is safe already — let's double-sanitize just in case:
    TextAndHtml.sanitizeAllowLinksAndBlocks(
      safeHtml, _.addAttributes("aside", "class").addAttributes("a", "class"))
  }


  def safeAside(safeHtml: String, extraLnPvCssClasses: String,
        unsafeUrl: String, unsafeProviderName: Option[String],
        addViewAtLink: Boolean): String = {
    <aside class={s"onebox s_LnPv $extraLnPvCssClasses clearfix"}>{
        // The html should have been sanitized already (that's why the param
        // name is *safe*Html).
        scala.xml.Unparsed(safeHtml)
      }{ if (!addViewAtLink) xml.Null else {
        <div class="s_LnPv_ViewAt"
          ><a href={unsafeUrl} target="_blank" rel={
                // 'noopener' stops [reverse_tabnabbing], prevents the new
                // browser tab from redireting the current browser tab to,
                // say, a pishing site.
                // 'ugc' means User-generated-content. There's also  "sponsored",
                // which must be used for paid links (or "nofollow" is also ok,
                // but not "ugc" — search engines can penalize that).
                // rel=nofollow also added here: [rel_nofollow].
                "nofollow noopener ugc"}>{
            "View at " + unsafeProviderName.getOrElse(unsafeUrl) /* I18N */
            } <span class="icon-link-ext"></span></a
        ></div>
    }}</aside>.toString
  }


  def sandboxedIframe(unsafeUrl: String, unsafeHtml: String,
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

    // ! wow !
    //  https://github.com/michael-simons/java-oembed
    // could be a blog post: Safely oEmbed via srcdoc?

    // https://www.html5rocks.com/en/tutorials/security/sandboxed-iframes/

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
      unsafeHtml + adjustOEmbedIframeHeightScript
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
  private def adjustOEmbedIframeHeightScript: String = i"""
        |<script src="/-/assets/ext-iframe$min.js"></script>
        """

  private def min = Globals.isDevOrTest ? "" | ".min"

}
