/**
 * Copyright (c) 2015, 2020 Kaj Magnus Lindberg
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

package debiki


import com.debiki.core._
import com.debiki.core.Prelude._
import org.jsoup.Jsoup
import org.scalatest._


class TextAndHtmlTest extends FreeSpec with matchers.must.Matchers {

  "TextAndHtml can" - {
    import TextAndHtml.{sanitizeTitleText => san}

    def checkRemovesScriptTags(fn: String => String): Unit = {
      san("""<script>alert("123")</script>Title""") mustBe "Title"
      san("""Two <script>alert("123") three</script>four""") mustBe "Two four"
      san("""Unterminated <script>alert("123") scr ip t""") mustBe "Unterminated"
      san("""very </script> terminated""") mustBe "very  terminated  666"
    }

    def checkRemovesScriptAttributes(fn: String => String, keepTag: Boolean): Unit = {
      val (start, end) = if (keepTag) ("<a>", "</a>") else ("", "")
      san("""<a href="javascript:alert(123)">Title</a>""") mustBe s"${start}Title$end"
      san("""Hi <a onclick="alert(123)">Title</a>""") mustBe s"Hi ${start}Title$end"
    }


    "sanitize titles  TyT6RKKDJ563" - {
      "remove <script> and anything inside" in {
        checkRemovesScriptTags(san)
      }

      "remove attribs w javascript, in fact, the whole <a> too" in {
        checkRemovesScriptAttributes(san, keepTag = false)
      }

      "delete unknown tags but keep the text inside" in {
        san("""<unknown>alert("123")</unknown>Title""") mustBe """alert("123")Title"""
        san("""<unknown>single ' double " </unknown>Title"""
              ) mustBe """single ' double " Title"""
      }

      "delete other bad stuff" in {
        san("""<unknown>whatever </unknown>Title""") mustBe "whatever Title"
        san("""<a href="whatever:no-no">Title</a> more""") mustBe "Title more"
      }

      "no links or anchors inside titles — titles link to the topics already" in {
        san("""<a id="whatever">Title</a>""") mustBe "Title"
        san("""<a href="https://ex.com">Title</a>""") mustBe "Title"
        san("""<a target="_blank">Title</a>""") mustBe "Title"
        san("""<a rel="follow">Title</a>""") mustBe "Title"
        san("""<a rel="nofollow">Title</a>""") mustBe "Title"
        san("""<a>Title</a>""") mustBe "Title"
      }

      "allow some tags" in {
        val okTitles = Seq(
              "",
              "() {} [] , ! ?",
              """Nice Title Wow""",
              """<code>nice with the source</code>""",
              """<small>but not tiny</small>""",
              """<b>bold</b> and <i>it</i> is <strong>ok</strong>""")
        for (title <- okTitles) {
          // Don't know why Jsoup adds these newlines — without them, the test fails.
          san(title).replaceAllLiterally("\n", "") mustBe title
        }
      }

      "escape" in {
        san("&") mustBe "&amp;"
        san("less < than") mustBe "less &lt; than"
        san("gr > than") mustBe "gr &gt; than"
        san("all < fun & escapes > here") mustBe "all &lt; fun &amp; escapes &gt; here"
      }

      "not escape already escaped html" in {
        san("&amp;") mustBe "&amp;"
        san("&lt;") mustBe "&lt;"
        san("&gt;") mustBe "&gt;"
      }

      "handle missing tags" in {
        san("""why </b> this""").replaceAllLiterally("\n", "") mustBe "why  this"
        san("""why <b>this""").replaceAllLiterally("\n", "") mustBe "why <b>this</b>"
      }

      "delete block elems" in {
        san("""<p>no paras</p>""") mustBe "no paras"
        san("""<div>no divs</div>""") mustBe "no divs"
        san("""<div>really no divs""") mustBe "really no divs"
      }
    }

    "sanitize posts  TyT03386KTDGR" - {
      import TextAndHtml.relaxedHtmlTagWhitelist

      def san(text: String) = Jsoup.clean(text, relaxedHtmlTagWhitelist)

      "remove <script> and anything inside" in {
        checkRemovesScriptTags(san)
      }

      "remove attribs w javascript, but allow <a>" in {
        checkRemovesScriptAttributes(san, keepTag = false)
      }

      "add rel=nofollow" in {
        san("""<a href="https://x.co">x.co</a>"""
              ) mustBe """<a href="https://x.co" rel="nofollow">x.co</a>"""
      }

      "change rel=follow to nofollow" in {
        san("""<a href="https://x.co" rel="follow">x.co</a>"""
              ) mustBe """<a href="https://x.co" rel="nofollow">x.co</a>"""
      }
    }
  }


  "TextAndHtmlMaker can" - {

    val maker = new TextAndHtmlMaker(
          siteId = NoSiteId, pubSiteId = "123abc", nashorn = null)

    "find links" - {

      "empty text" in {
        val textAndHtml = maker.forHtmlAlready("")
        textAndHtml.links mustBe Seq()
        textAndHtml.linkDomains mustBe Set()
        textAndHtml.linkIpAddresses mustBe Seq()
      }

      "html without links" in {
        val textAndHtml = maker.forHtmlAlready("<h1>Title</h1><p>Hello</p>")
        textAndHtml.links mustBe Seq()
        textAndHtml.linkDomains mustBe Set()
        textAndHtml.linkIpAddresses mustBe Seq()
      }

      "one <a href=...> link" in {
        val textAndHtml = maker.forHtmlAlready("<a href='http://example.com/path'>A link</a>")
        textAndHtml.links mustBe Seq("http://example.com/path")
        textAndHtml.linkDomains mustBe Set("example.com")
        textAndHtml.linkIpAddresses mustBe Seq()
      }

      "blank <a href='  '> link" in {
        val textAndHtml = maker.forHtmlAlready("<a href='  '>A link</a>")
        textAndHtml.links mustBe Seq()
        textAndHtml.linkDomains mustBe Set()
        textAndHtml.linkIpAddresses mustBe Seq()
      }

      "an  <a>  but without href attr is no link" in {
        val textAndHtml = maker.forHtmlAlready("<a>Not a link</a>")
        textAndHtml.links mustBe Seq()
        textAndHtml.linkDomains mustBe Set()
        textAndHtml.linkIpAddresses mustBe Seq()
      }

      "Pre-formatted <pre> blocks can contain links" in {
        val textAndHtml = maker.forHtmlAlready(
          "<pre><a href='http://hello.ex.co/path'>A link</a></pre>")
        textAndHtml.links mustBe Seq("http://hello.ex.co/path")
        textAndHtml.linkDomains mustBe Set("hello.ex.co")
        textAndHtml.linkIpAddresses mustBe Seq()
      }

      "<img src=...> link" in {
        val textAndHtml = maker.forHtmlAlready("<img src='http://example2.com/one.jpg'>")
        textAndHtml.links mustBe Seq("http://example2.com/one.jpg")
        textAndHtml.linkDomains mustBe Set("example2.com")
        textAndHtml.linkIpAddresses mustBe Seq()
      }

      "ip address <a href> link" in {
        val textAndHtml = maker.forHtmlAlready("<a href='http://11.22.33.44/path'>A link</a>")
        textAndHtml.links mustBe Seq("http://11.22.33.44/path")
        textAndHtml.linkDomains mustBe Set()
        textAndHtml.linkIpAddresses mustBe Seq("11.22.33.44")
      }

      "ip address <img src> link" in {
        val textAndHtml = maker.forHtmlAlready("<img src='http://22.22.11.11/img.png'>")
        textAndHtml.links mustBe Seq("http://22.22.11.11/img.png")
        textAndHtml.linkDomains mustBe Set()
        textAndHtml.linkIpAddresses mustBe Seq("22.22.11.11")
      }

      "Many links, incl <video>" in {
        val textAndHtml = maker.forHtmlAlready(o"""
           <img src='http://imgs.com/one.jpg'>
           <video src='http://vids.com/two.mp4'>
           <div><a href='http://hello.ex.co/path'>A link</a></div>
           <area href='http://1.2.3.4/path'>An ip addr</a>
           <b>Hello <a>not a link</a> and <img src="">not an img</img></b>
           """)
        textAndHtml.links.sorted mustBe Seq(
          "http://imgs.com/one.jpg",
          "http://vids.com/two.mp4",
          "http://hello.ex.co/path",
          "http://1.2.3.4/path").sorted
        textAndHtml.linkDomains mustBe Set("imgs.com", "vids.com", "hello.ex.co")
        textAndHtml.linkIpAddresses mustBe Seq("1.2.3.4")
      }

      TESTS_MISSING // ipv6 addr?
    }

  }

}
