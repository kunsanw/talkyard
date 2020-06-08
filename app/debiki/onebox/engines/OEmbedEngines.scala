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
import scala.util.matching.Regex


// These oEmbed engines are sorted alphabetically, index:   (Soon)
// Facebook posts
// Facebook videos
// Instagram
// Reddit
// TikTok
// Twitter
// YouTube


// ===== Facebook posts


object FacebookPostPrevwRendrEng {

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


class FacebookPostPrevwRendrEng(globals: Globals, siteId: SiteId, mayHttpFetch: Boolean)
  extends OEmbedPrevwRendrEng(
    globals, siteId = siteId, mayHttpFetch = mayHttpFetch) {

  def providerName = Some("Facebook")
  def widgetName = "Facebook post"
  def providerLnPvCssClassName = "s_LnPv-FbPost"
  def providerEndpoint = "https://www.facebook.com/plugins/post/oembed.json"
  //override def sanitizeInsteadOfSandbox = true
  override def sandboxInIframe = false
  override def handles(url: String): Boolean = FacebookPostPrevwRendrEng.handles(url)
}



// ===== Facebook videos


object FacebookVideoPrevwRendrEng {

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


class FacebookVideoPrevwRendrEng(globals: Globals, siteId: SiteId, mayHttpFetch: Boolean)
  extends OEmbedPrevwRendrEng(
    globals, siteId = siteId, mayHttpFetch = mayHttpFetch) {

  def providerName = Some("Facebook")
  def widgetName = "Facebook post"
  def providerLnPvCssClassName = "s_LnPv-FbVideo"
  def providerEndpoint = "https://www.facebook.com/plugins/video/oembed.json"
  override def handles(url: String): Boolean = FacebookVideoPrevwRendrEng.handles(url)
}



// ===== Instagram


object InstagramPrevwRendrEng {

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
    """^https?://(www\.)?(instagram\.com|instagr\.am)/([^/]+/)?(p|tv)/.*$""".r
}

class InstagramPrevwRendrEng(globals: Globals, siteId: SiteId, mayHttpFetch: Boolean)
  extends OEmbedPrevwRendrEng(
    globals, siteId = siteId, mayHttpFetch = mayHttpFetch) {

  def providerName = Some("Instagram")
  def widgetName = "Instagram post"
  def providerLnPvCssClassName = "s_LnPv-Instagram"
  def providerEndpoint = "https://api.instagram.com/oembed"
  override def regex: Regex = InstagramPrevwRendrEng.regex
}



// ===== Reddit

// Reddit's embedding script is buggy: it breaks in Talkyard's sandboxed iframe,
// when it cannot access document.cookie. It won't render any link preview
// — *however*, reddit comment replies use another Reddit script,
// which works (not buggy).
//
// Unfortunately,  allow-same-origin  apparently sets an <iframe srcdoc=...>'s
// domain to the same as its parent, so we cannot allow-same-origin
// (then the provider's scripts could look at Talkyard cookies, and other things).

// Reddit's stack trace:
//     platform.js:7 Uncaught SecurityError: Failed to read the 'cookie' property from
//       'Document': The document is sandboxed and lacks the 'allow-same-origin' flag.
//     get @ platform.js:7
//       h.getUID @ platform.js:8
//     ...
//     platform.js:7 Uncaught DOMException: Failed to read the 'cookie' property from
//         'Document': The document is sandboxed and lacks the 'allow-same-origin' flag.
//     at Object.get (https://embed.redditmedia.com/widgets/platform.js:7:8209)
//
// Reddit comments script, which works fine, is instead:
// https://www.redditstatic.com/comment-embed.js   not  /widgets/platform.js.

// From https://oembed.com:
// API endpoint: https://www.reddit.com/oembed
// URLs patterns:
//  - https://reddit.com/r/*/comments/*/*
//  - https://www.reddit.com/r/*/comments/*/*

object RedditPrevwRendrEng {
  val regex: Regex =
    """^https://(www\.)?(reddit\.com|instagr\.am)/r/[^/]+/comments/[^/]+/.*$""".r
}

class RedditPrevwRendrEng(globals: Globals, siteId: SiteId, mayHttpFetch: Boolean)
  extends OEmbedPrevwRendrEng(
    globals, siteId = siteId, mayHttpFetch = mayHttpFetch) {

  def providerName = Some("Reddit")
  def widgetName = "post"
  def providerLnPvCssClassName = "s_LnPv-Reddit"
  def providerEndpoint = "https://www.reddit.com/oembed"
  override def regex: Regex = RedditPrevwRendrEng.regex
}



// ===== TikTok

// TikTok's embed script (they include in the oEmbed html field) is buggy —
// it breaks when it cannot access localStorage in Talkyard's sandboxed iframe:
//
//   > VM170 embed_v0.0.6.js:1 Uncaught DOMException: Failed to read the 'localStorage'
//   >      property from 'Window': The document is sandboxed and lacks the
//   >      'allow-same-origin' flag.
//   >   at Module.3177845424933048caec (https://s16.tiktokcdn.com/tiktok/
//   >                                      falcon/embed/embed_v0.0.6.js:1:20719)
//   >   at r (https://s16.tiktokcdn.com/tiktok/falcon/embed/embed_v0.0.6.js:1:106)
//
// So no video or image loads — only some text and links.

object TikTokPrevwRendrEng {
  val regex: Regex =
    """^https://www.tiktok.com/@[^/]+/video/[0-9]+$""".r
}

class TikTokPrevwRendrEng(globals: Globals, siteId: SiteId, mayHttpFetch: Boolean)
  extends OEmbedPrevwRendrEng(
    globals, siteId = siteId, mayHttpFetch = mayHttpFetch) {

  def providerName = Some("TikTok")
  def widgetName = "video"
  def providerLnPvCssClassName = "s_LnPv-TikTok"

  // Example:
  // https://www.tiktok.com/oembed
  //    ?url=https://www.tiktok.com/@scout2015/video/6718335390845095173
  // Docs: https://developers.tiktok.com/doc/Embed
  //
  def providerEndpoint = "https://www.tiktok.com/oembed"
  override def regex: Regex = TikTokPrevwRendrEng.regex
}



// ===== Twitter

// What about Twitter Moments?
// https://developer.twitter.com/en/docs/twitter-for-websites/moments/guides/oembed-api
// Links look like:
//   https://twitter.com/i/moments/650667182356082688

// And Timelines?
// https://developer.twitter.com/en/docs/twitter-for-websites/timelines/guides/oembed-api
// Links look like:
//   https://twitter.com/TwitterDev

// omit_script=1  ?
// theme  = {light, dark}
// link_color  = #zzz   [ty_themes]
// lang="en" ... 1st 2 letters in Ty's lang code — except for Chinese:  zh-cn  zh-tw
// see:
// https://developer.twitter.com/en/docs/twitter-for-websites/twitter-for-websites-supported-languages/overview
// dnt  ?

// Wants:  theme: light / dark.  Primary color / link color.
// And device:  mobile / tablet / laptop ?  for maxwidth.

object TwitterPrevwRendrEng {
  // URL scheme, from https://oembed.com:
  // >  https://twitter.com/*/status/*
  // >  https://*.twitter.com/*/status/*
  val regex: Regex = """^https://(.*\.)?twitter\.com/.*/status/.*$""".r
}

class TwitterPrevwRendrEng(globals: Globals, siteId: SiteId, mayHttpFetch: Boolean)
  extends OEmbedPrevwRendrEng(
        globals, siteId = siteId, mayHttpFetch = mayHttpFetch) {

  def providerName = Some("Twitter")
  def widgetName = "tweet"
  def providerLnPvCssClassName = "s_LnPv-Twitter"
  def providerEndpoint = "https://publish.twitter.com/oembed"

  override def regex: Regex = TwitterPrevwRendrEng.regex

  // Twitter tweets are 598 px over at Twitter.com
  // Twitter wants 'maxwidth' not 'max_width'.
  // omit_script=1  ?
  // theme  = {light, dark}
  // link_color  = #zzz   [ty_themes]
  // lang="en" ... 1st 2 letters in Ty's lang code — except for Chinese:  zh-cn  zh-tw
  // see:
  // https://developer.twitter.com/en/docs/twitter-for-websites/twitter-for-websites-supported-languages/overview
  // dnt  ?
  // Wants:  theme: light / dark.  Primary color / link color.
  // And device:  mobile / tablet / laptop ?  for maxwidth.
  override def queryParamsEndAmp = "maxwidth=600&align=center&"

}


// From oembed.com:
// URL scheme: https://*.youtube.com/watch*
// URL scheme: https://*.youtube.com/v/*
// URL scheme: https://youtu.be/*
// API endpoint: https://www.youtube.com/oembed
//
object YouTubePrevwRendrEngOEmbed {
  val youtuDotBeStart = "https://youtu.be/"
  val youtubeComRegex: Regex = """^https://[^.]+\.youtube\.com/(watch|v/).+$""".r

  def handles(url: String): Boolean = {
    if (url.startsWith(youtuDotBeStart)) return true
    youtubeComRegex matches url
  }
}

/* Doesn't work, just gets 404 Not Found oEmbed responses. Use instead:
    YouTubePrevwRendrEng extends InstantLinkPreviewEngine

class YouTubePrevwRendrEng(globals: Globals, siteId: SiteId, mayHttpFetch: Boolean)
  extends OEmbedPrevwRendrEng(
    globals, siteId = siteId, mayHttpFetch = mayHttpFetch) {

  def providerName = Some("YouTube")
  def widgetName = "video"
  def providerLnPvCssClassName = "s_LnPv-YouTube"
  def providerEndpoint = "https://www.reddit.com/oembed"
  override def handles(url: String): Boolean = {
    YouTubePrevwRendrEng.handles(url)
  }

}  */
