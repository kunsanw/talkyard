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
import scala.util.matching.Regex



object FacebookPostsOneboxEngine {

  // Facebook posts and photos URL scheme, from https://oembed.com:
  //
  // API endpoint: https://www.facebook.com/plugins/post/oembed.json
  // for urls like:
  // >  https://www.facebook.com/*/posts/*
  // >  https://www.facebook.com/photos/*
  // >  https://www.facebook.com/*/photos/*
  // >  https://www.facebook.com/photo.php*
  // >  https://www.facebook.com/photo.php
  // >  https://www.facebook.com/*/activity/*
  // >  https://www.facebook.com/permalink.php
  // >  https://www.facebook.com/media/set?set=*
  // >  https://www.facebook.com/questions/*
  // >  https://www.facebook.com/notes/*/*/*
  //
  // From  https://developers.facebook.com/docs/plugins/oembed-endpoints/:
  //   https://www.facebook.com/{page-name}/posts/{post-id}
  //   https://www.facebook.com/{username}/posts/{post-id}
  //   https://www.facebook.com/{username}/activity/{activity-id}
  //   https://www.facebook.com/photo.php?fbid={photo-id}
  //   https://www.facebook.com/photos/{photo-id}
  //   https://www.facebook.com/permalink.php?story_fbid={post-id}
  //   https://www.facebook.com/media/set?set={set-id}
  //   https://www.facebook.com/questions/{question-id}
  //   https://www.facebook.com/notes/{username}/{note-url}/{note-id}

  def handles(url: String): Boolean = {
    if (!url.startsWith("https://www.facebook.com/"))
      return false

    val path = url.replaceAllLiterally("https://www.facebook.com", "")

    if (path.startsWith("/photos") ||
        path.startsWith("/photo.php?") ||      // folowed by ?query=params
        path.startsWith("/permalink.php?") ||  // ?story_fbid=...
        path.startsWith("/media/set?set=") ||
        path.startsWith("/questions/") ||
        path.startsWith("/notes/"))
      return true

    // This is good enough?
    if (path.contains("/posts/") ||
        path.contains("/photos/") ||
        path.contains("/activity/"))
      return true

    false
  }

}


object FacebookVideosOneboxEngine {

  // maxwidth=...
  // omitscript=...

  // Facebook videos URL scheme, from https://oembed.com:
  //
  // API endpoint: https://www.facebook.com/plugins/video/oembed.json
  // for urls like:
  // >  https://www.facebook.com/*/videos/*
  // >  https://www.facebook.com/video.php
  //
  // Videos, from https://developers.facebook.com/docs/plugins/oembed-endpoints/:
  //   https://www.facebook.com/{page-name}/videos/{video-id}/
  //   https://www.facebook.com/{username}/videos/{video-id}/
  //   https://www.facebook.com/video.php?id={video-id}
  //   https://www.facebook.com/video.php?v={video-id}
  //
  // Response looks like:
  //   {
  //     "author_name": "Facebook",
  //     "author_url": "https://www.facebook.com/facebook/",
  //     "provider_url": "https://www.facebook.com",
  //     "provider_name": "Facebook",
  //     "success": true,
  //     "height": null,
  //     "html": "<div id=\"fb-root\"></div>\n<script>(function(d, s, id) {\n  var js, fjs = d.getElementsByTagName(s)[0];\n  if (d.getElementById(id)) return;\n  js = d.createElement(s); js.id = id;\n  js.src = \"https://connect.facebook.net/en_US/sdk.js#xfbml=1&amp;version=v2.9\";\n  fjs.parentNode.insertBefore(js, fjs);\n}(document, 'script', 'facebook-jssdk'));</script><div class=\"fb-video\" data-href=\"https://www.facebook.com/facebook/videos/10153231379946729/\"><div class=\"fb-xfbml-parse-ignore\"><blockquote cite=\"https://www.facebook.com/facebook/videos/10153231379946729/\"><a href=\"https://www.facebook.com/facebook/videos/10153231379946729/\">How to Share With Just Friends</a><p>How to share with just friends.</p>Posted by <a href=\"https://www.facebook.com/facebook/\">Facebook</a> on Friday, December 5, 2014</blockquote></div></div>",
  //     "type": "video",
  //     "version": "1.0",
  //     "url": "https://www.facebook.com/facebook/videos/10153231379946729/",
  //     "width": "100%"
  //   }

  def handles(url: String): Boolean = {
    if (!url.startsWith("https://www.facebook.com/"))
      return false

    val path = url.replaceAllLiterally("https://www.facebook.com", "")

    if (path.startsWith("/video.php?")) // folowed by ?query=params
      return true

    // Good enough?
    if (path.contains("/videos/"))
      return true

    false
  }

}


object InstagramOneboxEngine {

  // Instagram URL scheme, from https://oembed.com:
  //
  //  API:  https://api.instagram.com/oembed  (only json)
  //
  // > http://instagram.com/*/p/*,
  // > http://www.instagram.com/*/p/*,
  // > https://instagram.com/*/p/*,
  // > https://www.instagram.com/*/p/*,
  // > http://instagram.com/p/*
  // > http://instagr.am/p/*
  // > http://www.instagram.com/p/*
  // > http://www.instagr.am/p/*
  // > https://instagram.com/p/*
  // > https://instagr.am/p/*
  // > https://www.instagram.com/p/*
  // > https://www.instagr.am/p/*
  // > http://instagram.com/tv/*
  // > http://instagr.am/tv/*
  // > http://www.instagram.com/tv/*
  // > http://www.instagr.am/tv/*
  // > https://instagram.com/tv/*
  // > https://instagr.am/tv/*
  // > https://www.instagram.com/tv/*
  // > https://www.instagr.am/tv/*

  val regex: Regex =
    """^https?://(www\.)?(instagram\.com|instagr\.am)/(.*/)?(p|tv)/.*$""".r

}


object RedditOneboxEngine {

  // From https://oembed.com:
  //
  // API endpoint: https://www.reddit.com/oembed
  // > https://reddit.com/r/*/comments/*/*
  // > https://www.reddit.com/r/*/comments/*/*

  val regex: Regex =
    """^https://(www\.)?(reddit\.com|instagr\.am)/r/[^/]+/comments/[^/]+/.*$""".r

}



object TwitterOneboxEngine {

  // URL scheme, from https://oembed.com:
  // >  https://twitter.com/*/status/*
  // >  https://*.twitter.com/*/status/*
  val regex: Regex = """^https://(.*\.)?twitter\.com/.*/status/.*$""".r

  // What about Twitter Moments?
  // https://developer.twitter.com/en/docs/twitter-for-websites/moments/guides/oembed-api
  // Links look like:
  //   https://twitter.com/i/moments/650667182356082688

  // And Timelines?
  // https://developer.twitter.com/en/docs/twitter-for-websites/timelines/guides/oembed-api
  // Links look like:
  //   https://twitter.com/TwitterDev

  def getOEmbedProviderEndpoint(stuffUrl: String): Option[String] = {
    // for now
    Some("https://publish.twitter.com/oembed")
  }

}


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
class TwitterOneboxEngine(globals: Globals, nashorn: Nashorn)
  extends OneboxEngine(globals, nashorn) {

  def regex: Regex = TwitterOneboxEngine.regex

  // ! wow !
  //  https://github.com/michael-simons/java-oembed
  // could be a blog post: Safely oEmbed via srcdoc?

  // https://www.html5rocks.com/en/tutorials/security/sandboxed-iframes/

  val cssClassName = "dw-ob-oembed"


  /** The oEmbed stuff might include arbitrary html tags and even scripts,
    * so we render it in a sandboxed iframe.
    */
  override val alreadySanitized = true

  override val alreadyWrappedInAside = true


  def loadAndRender(url: String): Future[String] = {
    val providerEndpoint: String =
          TwitterOneboxEngine.getOEmbedProviderEndpoint(url) getOrElse {
            return Future.successful("Ooops unimpl [43987626576]")
          }

    // omit_script=1  ?
    // theme  = {light, dark}
    // link_color  = #zzz   [ty_themes]
    // lang="en" ... 1st 2 letters in Ty's lang code — except for Chinese:  zh-cn  zh-tw
    // see:
    // https://developer.twitter.com/en/docs/twitter-for-websites/twitter-for-websites-supported-languages/overview
    // dnt  ?

    // Wants:  theme: light / dark.  Primary color / link color.
    // And device:  mobile / tablet / laptop ?  for maxwidth.
    val requestUrl = providerEndpoint +
          "?maxwidth=500" +
          "&align=center" +
          s"&url=$url"

    val request: WSRequest = globals.wsClient.url(requestUrl)

    request.get().map({ r: request.Response =>
      var problem = r.status match {
        case 404 => s"Tweet gone: "
        case 429 => s"Rate limited by Twitter, cannot show: "
        case 200 => "" // continue below
        case x => s"Unexpecte Twitter oEmbed status code: $x, url: "
      }

      val anyHtml = if (problem.isEmpty) (r.json \ "html").asOpt[String] else None

      if (problem.isEmpty && anyHtml.isEmpty) {
        problem = s"No html in Twitter oEmbed, url: "
      }

      val safeHtmlResult = if (problem.nonEmpty) {
        // Escape, in case the url is evil.
        org.owasp.encoder.Encode.forHtmlContent(problem + s"<tt>$url</tt>")
      }
      else {
        val html = anyHtml.getOrDie("TyE6986SK")
        val providerNameOrUrl = (r.json \ "provider_name").asOpt[String] getOrElse url
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

        COULD // incl Ty Javascript that messages the parent with the height
        // & width of the contents?

        // Iframe sandbox permissions.
        val permissions = (
              // Most? oEmbeds execute Javascript to render themselves — ok, in a sandbox.
              "allow-scripts " +

              // This makes:  <a href=... target=_blank>  work — opens a new
              // browser tab. But since we don't  allow-same-origin,  cookies won't work
              // in that new tab.
              "allow-popups " +

              // So we need this too — makes cookies work in the above-mentioned new tab.
              "allow-popups-to-escape-sandbox " +

              // This makes links work, but only if the user actually clicks the links.
              // Javascript in the iframe cannot change the top win location when
              // the user is inactive (that would have made pishing attacks possible,
              // if the iframe could silently replace the whole page with another
              // page on another domain but that looks the same).
              "allow-top-navigation-by-user-activation")

        <aside class="s_Ob s_Ob-Twitter s_Ob-oEmb"
          ><iframe seamless=""
                   sandbox={permissions}
                   srcdoc={html + OneboxIframe.iframeHeightScript}
          ></iframe
          ><div class="s_Ob_Lnk"
          ><a href={url} target="_blank">{ "View at " + providerNameOrUrl /* I18N */
          } <span class="icon-link-ext"></span></a
          ></div>
        ></aside>.toString

        // + width: calc(100% - 30px);  max-width: 700px;
        // border: none;  ?
        // background: hsl(0, 0%, 91%);
      }

      safeHtmlResult

    })(globals.executionContext)
  }
}


object OneboxIframe {

  /** The embedding parent window doesn't know how tall this iframe with oEmbed
    * stuff inside wants to be — so let's tell it.
    */
  val iframeHeightScript: String = """
    |
    |<!-- Talkyard script [OEMBHGHT] -->
    |<script>
    |var numHeightMessagesSent = 0;
    |function sendHeightMessage() {
    |  const height = document.body.clientHeight;
    |  console.debug(`Telling parent the height of this oEmbed iframe: ${height} [TyMOEMBHGHT]`);
    |  window.parent.postMessage(['oEmbHeight', height], '*');
    |  if (numHeightMessagesSent < 3) {
    |    numHeightMessagesSent += 1;
    |    setTimeout(sendHeightMessage, numHeightMessagesSent * 1000 + 500);
    |  }
    |}
    |setTimeout(sendHeightMessage, 500);
    |</script>
    |""".stripMargin

}
