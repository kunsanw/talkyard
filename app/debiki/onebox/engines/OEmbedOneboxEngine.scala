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
 *
 * The parts copyrighted by jzeta are available under the MIT license:
 * - https://github.com/discourse/onebox/blob/master/lib/onebox/engine/video_onebox.rb
 * - https://github.com/discourse/onebox/blob/master/LICENSE.txt
 */

package debiki.onebox.engines

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki.{Globals, Nashorn}
import debiki.onebox._
import play.api.libs.ws.WSRequest

import scala.concurrent.Future
import scala.util.Success
import scala.util.matching.Regex


/** For now: Twitter only.
  *
  * Later: oEmbed works for FB, Insta, Mediumm, Reddit, "everything" — just change
  * the regex, + map urls to the correct oEmbed provider endpoints.
  */
class OEmbedOneboxEngine(globals: Globals, nashorn: Nashorn)
  extends OneboxEngine(globals, nashorn) {

  val regex: Regex = """^(https?:).*twitter\.com.*$""".r

  // ""^(https?:)?\/\/.*\.(mov|mp4|m4v|webm|ogv)(\?.*)?$""".r

  // https://github.com/michael-simons/java-oembed

  // ! wow !
  //  https://github.com/michael-simons/java-oembed
  // could be a blog post: Safely oEmbed via srcdoc?

  // https://www.html5rocks.com/en/tutorials/security/sandboxed-iframes/

  val cssClassName = "dw-ob-oembed"


  /** The oEmbed stuff might include arbitrary html tags and even scripts,
    * so we render it in a sandboxed iframe.
    */
  override val alreadySanitized = true


  def loadAndRender(url: String): Future[String] = {
    val providerEndpoint: String =
          OEmbedOneboxEngine.getOEmbedProviderEndpoint(url) getOrElse {
            return Future.successful("Ooops unimpl [43987626576]")
          }

    val request: WSRequest = globals.wsClient.url(providerEndpoint + "?url=" + url)
    request.get().map({ r: request.Response =>
      val json = r.json
      /* Example response json, this from Twitter:
      {
        "url": "https://twitter.com/i/moments/650667182356082688",
        "title": "The Obamas' wedding anniversary",
        "html": "<a class=\"twitter-moment\" href=\"https://twitter.com/i/moments/650667182356082688\">The Obamas&#39; wedding anniversary</a>\n<script async src=\"//platform.twitter.com/widgets.js\" charset=\"utf-8\"></script>",
        "width": 550,
        "height": null,
        "type": "rich"
        "cache_age": "3153600000",
        "provider_name": "Twitter",
        "provider_url": "https://twitter.com",
        "version": "1.0"
      */
      val html: String = (json \ "html").asOpt[String] match {
        case None =>
          // Escape, in case the url is "evil".
          org.owasp.encoder.Encode.forHtmlContent(
                s"Couldn't embed this oEmbed link: $url")
        case Some(html: String) =>
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

          (<iframe sandbox="allow-scripts xxallow-same-origin" seamless=""
                   srcdoc={html}></iframe>
                ).toString
          // + width: calc(100% - 30px);  max-width: 700px;
          // border: none;  ?
          // background: hsl(0, 0%, 91%);
      }
      html
    })(globals.executionContext)
  }
}


object OEmbedOneboxEngine {

  def getOEmbedProviderEndpoint(stuffUrl: String): Option[String] = {
    // for now
    Some("https://publish.twitter.com/oembed")
  }
}
