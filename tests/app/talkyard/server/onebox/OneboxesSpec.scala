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


package talkyard.server.onebox


import org.scalatest._
import org.scalatest.matchers.must
import com.debiki.core.Prelude._
import debiki.onebox.engines.TwitterPrevwRendrEng


class OneboxesSpec extends FreeSpec with must.Matchers {  // RENAME to LinkPreviewMatchSpec ?

  val http = "http"
  val https = "https"

  "LinkPreviewRenderEngine" - {

    "TwitterPrevwRendrEng can" - {
      import TwitterPrevwRendrEng.{regex => rgx}

      // Sample tweet link from:
      // https://developer.twitter.com/en/docs/tweets/post-and-engage/api-reference/get-statuses-oembed
      val sampleTweetLink = "https://twitter.com/Interior/status/507185938620219395"

      // Sample Moment link from:
      // https://developer.twitter.com/en/docs/twitter-for-websites/moments/guides/oembed-api
      val sampleMomentLink = "https://twitter.com/i/moments/650667182356082688"

      "regex match tweet urls" in {
        val status = "status"
        val twittercom = "twitter.com"
        rgx.matches(s"$https://$twittercom/abc123/$status/def456*") mustBe true
        rgx.matches(s"$https://$twittercom/abc/123/$status/def/456*") mustBe true
        rgx.matches(s"$https://sth.$twittercom/ab12/$status/de45*") mustBe true
        rgx.matches(sampleTweetLink) mustBe true
      }

      "but not the wrong tweet urls" in {
        // Not https:
        rgx.matches(sampleTweetLink.replaceAllLiterally("https:", "http:")) mustBe false
        // Not 'status':
        rgx.matches(sampleTweetLink.replaceAllLiterally("status", "sta_tus")) mustBe false
        // Not 'twitter.com':
        rgx.matches(sampleTweetLink.replaceAllLiterally("twitter.com", "twi_ter.com")
              ) mustBe false
        // 'https://not_twitter.com':
        rgx.matches(sampleTweetLink.replaceAllLiterally("://t", "://not_t")
              ) mustBe false
      }

      "?? Later ?? regex match Twitter Moment urls" in {
        // rgx.matches(sampleMomentLink) mustBe true
      }

    }

    val facebookPostUrl = "https://www.facebook.com/abc123/posts/def456"
    val facebookVideoUrl = "https://www.facebook.com/abc123/videos/def456"

    "FacebookPostPrevwRendrEng can" - {
      import debiki.onebox.engines.{FacebookPostPrevwRendrEng => fb}

      val postUrl = facebookPostUrl
      val photoUrl = "https://www.facebook.com/photos/def456"

      "match FB post urls" in {
        fb.handles(postUrl) mustBe true
        fb.handles(photoUrl) mustBe true
        fb.handles("https://www.facebook.com/abc123/photos/def456") mustBe true
        fb.handles("https://www.facebook.com/photos.php?something=more") mustBe true
        fb.handles("https://www.facebook.com/abc123/activity/def456") mustBe true
        fb.handles("https://www.facebook.com/permalink.php?whatever=abc123") mustBe true
        fb.handles("https://www.facebook.com/media/set?set=abc123") mustBe true
        fb.handles("https://www.facebook.com/questions/abc123") mustBe true
        fb.handles("https://www.facebook.com/notes/abc/123") mustBe true
      }

      "but not the wrong urls" in {
        fb.handles(postUrl.replaceAllLiterally("posts", "pos_ts")) mustBe false
        fb.handles(postUrl.replaceAllLiterally("facebook", "fac_ebook")) mustBe false
        fb.handles(postUrl.replaceAllLiterally("https:", "http:")) mustBe false

        fb.handles(photoUrl.replaceAllLiterally("photos", "pho_tos")) mustBe false
      }

      "not FB video urls" in {
        fb.handles(facebookVideoUrl) mustBe false
      }
    }


    "FacebookVideoPrevwRendrEng can" - {
      import debiki.onebox.engines.{FacebookVideoPrevwRendrEng => fb}

      val videoUrl = facebookVideoUrl
      val videoUrl2 = "https://www.facebook.com/video.php?query=param"

      "match FB video urls" in {
        fb.handles(videoUrl) mustBe true
        fb.handles(videoUrl2) mustBe true
      }

      "but not the wrong urls" in {
        fb.handles(videoUrl.replaceAllLiterally("videos", "vid_eos")) mustBe false

        fb.handles(videoUrl.replaceAllLiterally("facebook", "fac_ebook")) mustBe false
        fb.handles(videoUrl.replaceAllLiterally("https:", "http:")) mustBe false

        fb.handles(videoUrl2.replaceAllLiterally("video", "vid_eo")) mustBe false
      }

      "not FB post urls" in {
        fb.handles(videoUrl.replaceAllLiterally("videos", "posts")) mustBe false
        fb.handles(facebookPostUrl) mustBe false
      }
    }


    "InstagramPrevwRendrEng can" - {
      import debiki.onebox.engines.{InstagramPrevwRendrEng => insta}

      val realUrl1 = "https://www.instagram.com/p/BJlNX-rju7o/?utm_source=ig_web_button_share_sheet"
      val realUrl2 = "https://www.instagram.com/p/CBCJGUZDVT_/"
      val url1 = "https://instagram.com/abc123/p/def456"
      val url2 = "https://instagram.com/abc123/tv/def456"
      val url3 = "https://instagram.com/p/def456"
      val url4 = "https://instagram.com/tv/def456"
      val url5 = "https://instagr.am/abc123/p/def456"

      "match real Instagram url:s" in {
        insta.regex.matches(realUrl1) mustBe true
        insta.regex.matches(realUrl2) mustBe true
      }

      "match even more Instagram urls" in {
        insta.regex.matches(url1) mustBe true
        insta.regex.matches(url2) mustBe true
        insta.regex.matches(url3) mustBe true
        insta.regex.matches(url4) mustBe true
        insta.regex.matches(url5) mustBe true
      }

      "also http" in {
        insta.regex.matches(url1.replaceAllLiterally("https:", "http:")) mustBe true
      }

      "but not the wrong urls" in {
        insta.regex.matches(url1.replaceAllLiterally("instag", "ins_tag")) mustBe false
        insta.regex.matches(url1.replaceAllLiterally("/p/", "/weird/")) mustBe false
        insta.regex.matches(url2.replaceAllLiterally("/tv/", "/weird/")) mustBe false

        insta.regex.matches(url5.replaceAllLiterally("instagr.am", "ins_agr.am")) mustBe false
        insta.regex.matches(url5.replaceAllLiterally("/p/", "/weird/")) mustBe false
      }
    }


    "RedditPrevwRendrEng can" - {
      import debiki.onebox.engines.{RedditPrevwRendrEng => reddit}

      val url1 = "https://reddit.com/r/abc123/comments/de45/fg67"
      val url2 = "https://www.reddit.com/r/abc123/comments/de45/fg67"
      val url3 = "https://www.reddit.com/r/AskReddit/comments/gz52ae/" +
                    "your_username_becomes_a_real_being_with_a_human/ftehaok/"

      "match Reddit urls" in {
        reddit.regex.matches(url1) mustBe true
        reddit.regex.matches(url2) mustBe true
        reddit.regex.matches(url3) mustBe true
      }

      "but not the wrong urls" in {
        // http not https
        reddit.regex.matches(url1.replaceAllLiterally("https:", "http:")) mustBe false
        reddit.regex.matches(url2.replaceAllLiterally("https:", "http:")) mustBe false
        // wrong domain
        reddit.regex.matches(url1.replaceAllLiterally("reddit.com", "red_it.com")) mustBe false
        reddit.regex.matches(url2.replaceAllLiterally("reddit.com", "red_it.com")) mustBe false
        // wrong path
        reddit.regex.matches(url1.replaceAllLiterally("/comments/", "/comets/")) mustBe false
        reddit.regex.matches(url2.replaceAllLiterally("/comments/", "/comets/")) mustBe false
        // no  /r/
        reddit.regex.matches(url1.replaceAllLiterally("/r/", "/x/")) mustBe false
        reddit.regex.matches(url2.replaceAllLiterally("/r/", "/x/")) mustBe false
        // too many /
        reddit.regex.matches(url3.replaceAllLiterally("/AskReddit/", "/AskR/eddit/")) mustBe false
      }
    }
  }


  "TikTokPrevwRendrEng can" - {
    import debiki.onebox.engines.{TikTokPrevwRendrEng => tiktok}

    val url1 = "https://www.tiktok.com/@scout2015/video/6718335390845095173"
    val url2 = "https://www.tiktok.com/@someone.somename/video/1234567890000000002"

    "match TikTok urls" in {
      tiktok.regex.matches(url1) mustBe true
      tiktok.regex.matches(url2) mustBe true
    }

    "but not the wrong urls" in {
      // http not https
      tiktok.regex.matches(url1.replaceAllLiterally("https:", "http:")) mustBe false
      tiktok.regex.matches(url2.replaceAllLiterally("https:", "http:")) mustBe false
      // wrong domain
      tiktok.regex.matches(url1.replaceAllLiterally("tiktok.com", "tik_ok.com")) mustBe false
      tiktok.regex.matches(url2.replaceAllLiterally("tiktok.com", "tiktok.org")) mustBe false
      tiktok.regex.matches(url2.replaceAllLiterally("www.tiktok", "bad.tiktok")) mustBe false
      // wrong path
      tiktok.regex.matches(url1.replaceAllLiterally("/video/", "/villains/")) mustBe false
      tiktok.regex.matches(url2.replaceAllLiterally("/video/", "/vi/deo/")) mustBe false
      tiktok.regex.matches(url2.replaceAllLiterally("/video/", "/")) mustBe false
      // no  @ in username
      tiktok.regex.matches(url1.replaceAllLiterally("/@", "/")) mustBe false
    }
  }


  "YouTubePrevwRendrEngOEmbed can" - {
    import debiki.onebox.engines.{YouTubePrevwRendrEngOEmbed => youtube}

    val url1 = "https://youtu.be/box0-koAuIY"
    val url2 = "https://www.youtube.com/watch?v=S7znI_Kpzbs"
    val url3 = "https://www.youtube.com/watch?v=8h9Mvz1i9hk"

    "match YouTube urls" in {
      youtube.handles(url1) mustBe true
      youtube.handles(url2) mustBe true
      youtube.handles(url3) mustBe true
    }

    "but not the wrong urls" in {
      // http not https
      youtube.handles(url1.replaceAllLiterally("https:", "http:")) mustBe false
      // wrong domain
      youtube.handles(url1.replaceAllLiterally(".be", ".com")) mustBe false
      youtube.handles(url2.replaceAllLiterally(".com", ".be")) mustBe false
      youtube.handles(url3.replaceAllLiterally("youtube", "yo_tube")) mustBe false
      // wrong path
      youtube.handles(url2.replaceAllLiterally("/watch", "/look")) mustBe false
      youtube.handles(url2.replaceAllLiterally("/watch", "/wow/watch")) mustBe false
    }
  }


}


