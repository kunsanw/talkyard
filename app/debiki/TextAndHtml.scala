/**
 * Copyright (c) 2015 Kaj Magnus Lindberg (born 1979)
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
import org.scalactic.{ErrorMessage, Or}
import play.api.libs.json.JsArray
import scala.collection.{immutable, mutable}
import scala.util.matching.Regex
import TextAndHtmlMaker._
import debiki.dao.UploadsDao
import org.jsoup.Jsoup
import org.jsoup.safety.Whitelist


/** Immutable.
  */
sealed abstract class SourceAndHtml {
  def source: String
  def text: String  // deprecated
  def safeHtml: String

  REMOVE // from here later, only here for now so [52TKTSJ5] compiles.
  def uploadRefs: Set[UploadRef] = Set.empty
  def internalLinks: Set[String] = Set.empty

  // sourceMarkupLang: MarkupLang  // maybe later?
}


case class TitleSourceAndHtml(source: String, safeHtml: String) extends SourceAndHtml {
  def text: String = source // backw compat
}


object TitleSourceAndHtml {
  def apply(source: String): TitleSourceAndHtml = {
    // REMOVE
    // //  =  dao.textAndHtmlMaker.forTitle(title)
    // // this.textAndHtmlMaker.forTitle(s"Description of the $name category")
    // //                            where this = CategoriesDao
    // // def createForum(options, ...)
    // // val titleHtmlSanitized = TextAndHtml.sanitizeTitleText(options.title)

    val safeHtml = TextAndHtml.sanitizeTitleText(source)
    TitleSourceAndHtml(source, safeHtml = safeHtml)
  }
}



/** Immutable. Use linkDomains to check all links against a spam/evil-things domain block list
  * like Spamhaus DBL, https://www.spamhaus.org/faq/section/Spamhaus%20DBL#271.
  */
sealed abstract class TextAndHtml extends SourceAndHtml {

  def text: String  ; RENAME // to source
  def source: String = text

  def safeHtml: String

  def usernameMentions: Set[String]

  def uploadRefs: Set[UploadRef]

  def externalLinks: immutable.Seq[String]

  override def internalLinks: Set[String]

  /** Domain names used in links. Check against a domain block list.
    */
  def linkDomains: Set[String]

  /** Raw ip addresses (ipv4 or 6) of any links that use raw ip addresses rather than
    * domain names. If there is any, the post should probably be blocked as spam?
    */
  def linkIpAddresses: immutable.Seq[String]

  def isTitle: Boolean

  def htmlLinksOnePerLine: String = {
    TESTS_MISSING
    externalLinks map { href =>
      val hrefAttrEscaped = org.owasp.encoder.Encode.forHtmlAttribute(href)
      val hrefContentEscaped = org.owasp.encoder.Encode.forHtmlContent(href)
      s"""<a href="$hrefAttrEscaped">$hrefContentEscaped</a>"""
    } mkString "\n"
  }

  def append(moreTextAndHtml: TextAndHtml): TextAndHtml
  def append(text: String): TextAndHtml
}


object TextAndHtml {

  /** The result can be incl in html anywhere: As html tags contents,
    * or in a html attribute.
    */
  def safeEncodeForHtml(unsafe: String): String = {
    org.owasp.encoder.Encode.forHtml(unsafe)
  }

  /** Can *only* be incl in html attributes — not as tags contents.
    */
  def safeEncodeForHtmlAttrOnly(unsafe: String): String = {
    org.owasp.encoder.Encode.forHtmlAttribute(unsafe)
  }

  /** Can *only* be incl as html tags contents — *not* in an attribute.
    */
  def safeEncodeForHtmlContentOnly(unsafe: String): String = {
    org.owasp.encoder.Encode.forHtmlContent(unsafe)
  }

  /** Removes bad tags and attributes from a html string.
    * The result is html tags content — and can *not* be incl in an attribute.
    */
  def sanitizeTitleText(unsafe: String): String = {
    // Tested here: TyT6RKKDJ563
    Jsoup.clean(unsafe, titleHtmlTagsWhitelist)
  }

  /** More restrictive than Jsoup's basic() whitelist.
    */
  def titleHtmlTagsWhitelist: org.jsoup.safety.Whitelist = {
    new Whitelist().addTags(
          "b", "code", "em",
          "i", "q", "small", "span", "strike", "strong", "sub",
          "sup", "u")
  }

  /** Links will have rel=nofollow. Images, pre, div allowed.
    */
  def sanitizeAllowLinksAndBlocks(unsafeTags: String,
        amendWhitelistFn: Whitelist => Whitelist = x => x): String = {
    var whitelist = org.jsoup.safety.Whitelist.basic()
    whitelist = amendWhitelistFn(whitelist)
    Jsoup.clean(unsafeTags, whitelist)
  }

  def sanitizeRelaxed(unsafeTags: String,
        amendWhitelistFn: Whitelist => Whitelist = x => x): String = {
    var whitelist = org.jsoup.safety.Whitelist.relaxed()
    whitelist = amendWhitelistFn(whitelist)
    Jsoup.clean(unsafeTags, whitelist)
  }

  // Or could instead use  Nashorn.sanitizeHtml(text: String, followLinks: Boolean) ?
  // But it's slow, if importing a whole site. How deal with this?
  // Maybe just let admins-that-import-a-site set a flag that everything has been
  // sanitized already?_ COULD move server side js to external Nodejs or V8
  // processes? So as not to block a thread here, running Nashorn? [external-server-js]
  def relaxedHtmlTagWhitelist: org.jsoup.safety.Whitelist = {
    // Tested here: TyT03386KTDGR

    // rel=nofollow not included by default, in the relaxed() whitelist,
    // see: https://jsoup.org/apidocs/org/jsoup/safety/Whitelist.html#relaxed()
    BUG // this removes  noopener
    org.jsoup.safety.Whitelist.relaxed().addEnforcedAttribute("a", "rel", "nofollow")
  }
}


object TextAndHtmlMaker {

  val Ipv4AddressRegex: Regex = """[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+""".r


  def findLinks(html: String): immutable.Seq[String] = {   // and add  noopener ?
    // Tested here:  tests/app/debiki/TextAndHtmlTest.scala

    val result = mutable.ArrayBuffer[String]()

    val document = org.jsoup.Jsoup.parse(html)

    // There're  <a hre=...>, and also <area href=...> (that's a clickable map, its
    // contents defined by href links.)
    val hrefAttrElems: org.jsoup.select.Elements = document.select("[href]")

    // There're  <img> <video> <iframe> etc elems with src=...  links.
    val srcAttrElems: org.jsoup.select.Elements = document.select("[src]")

    import scala.collection.JavaConversions._

    for (elem: org.jsoup.nodes.Element <- hrefAttrElems) {
      addUrl(elem.attr("href"))
    }

    for (elem: org.jsoup.nodes.Element <- srcAttrElems) {
      addUrl(elem.attr("src"))
    }

    def addUrl(url: String): Unit = {
      if (url eq null) return
      val trimmed = url.trim
      if (trimmed.isEmpty) return
      result.append(trimmed)
    }

    result.toVector
  }

}



/** Thread safe.
  */
class TextAndHtmlMaker(val site: SiteIdHostnames, nashorn: Nashorn) {

  private class TextAndHtmlImpl(
    val text: String,
    val safeHtml: String,
    val usernameMentions: Set[String],
    override val uploadRefs: Set[UploadRef],
    val externalLinks: immutable.Seq[String],
    override val internalLinks: Set[String],
    val linkDomains: immutable.Set[String],
    val linkIpAddresses: immutable.Seq[String],
    val embeddedOriginOrEmpty: String,
    val isTitle: Boolean,
    val followLinks: Boolean,
    val allowClassIdDataAttrs: Boolean) extends TextAndHtml {

    def append(text: String): TextAndHtml = {
      append(new TextAndHtmlMaker(site = site, nashorn).apply(
        text, embeddedOriginOrEmpty = embeddedOriginOrEmpty,
        isTitle = isTitle, followLinks = followLinks,
        allowClassIdDataAttrs = allowClassIdDataAttrs))
    }

    def append(moreTextAndHtml: TextAndHtml): TextAndHtml = {
      val more = moreTextAndHtml.asInstanceOf[TextAndHtmlImpl]
      if (!nashorn.globals.isProd) {
        dieIf(followLinks != more.followLinks, "TyE306MKSLN2")
        dieIf(embeddedOriginOrEmpty != more.embeddedOriginOrEmpty, "TyE306MKSLN3")
      }

      new TextAndHtmlImpl(
        text + "\n" + more.text,
        safeHtml + "\n" + more.safeHtml,
        usernameMentions = usernameMentions ++ more.usernameMentions,
        uploadRefs = uploadRefs ++ more.uploadRefs,
        externalLinks = (externalLinks.toSet ++ more.externalLinks.toSet).to[immutable.Seq],
        internalLinks = internalLinks ++ more.internalLinks,
        linkDomains ++ more.linkDomains,
        linkIpAddresses = (linkIpAddresses.toSet ++ more.linkIpAddresses.toSet).to[immutable.Seq],
        embeddedOriginOrEmpty = embeddedOriginOrEmpty,
        isTitle = isTitle && more.isTitle,
        followLinks = followLinks,
        allowClassIdDataAttrs = allowClassIdDataAttrs)
    }
  }


  def withCompletedFormData(formInputs: String): TextAndHtml Or ErrorMessage = {
    CompletedFormRenderer.renderJsonToSafeHtml(formInputs) map { htmlString =>
      new TextAndHtmlImpl(text = formInputs.toString, safeHtml = htmlString,
          // Don't let people @mention anyone when submitting forms?  (5LKATS0)
          // @mentions are only for members who post comments & topics to each other, right.
          // Hmm but probably should analyze links! Well this not in use
          // now anyway, except for via UTX.
          usernameMentions = Set.empty, uploadRefs = Set.empty,
          externalLinks = Nil, internalLinks = Set.empty, linkDomains = Set.empty,
          linkIpAddresses = Nil, embeddedOriginOrEmpty = "",
          isTitle = false, followLinks = false, allowClassIdDataAttrs = false)
    }
  }


  def withCompletedFormData(formInputs: JsArray): TextAndHtml Or ErrorMessage = {
    CompletedFormRenderer.renderJsonToSafeHtml(formInputs) map { htmlString =>
      new TextAndHtmlImpl(text = formInputs.toString, safeHtml = htmlString,
            usernameMentions = Set.empty, // (5LKATS0)
            uploadRefs = Set.empty,
            externalLinks = Nil, internalLinks = Set.empty,
            linkDomains = Set.empty,
            linkIpAddresses = Nil, embeddedOriginOrEmpty = "", false, false, false)
    }
  }


  CLEAN_UP; REMOVE
  def forTitle(title: String): TitleSourceAndHtml =
    TitleSourceAndHtml(title) /*
    apply(title, embeddedOriginOrEmpty = "",
      isTitle = true, followLinks = false, allowClassIdDataAttrs = false) */

  def forBodyOrComment(text: String, embeddedOriginOrEmpty: String = "",
        followLinks: Boolean = false, allowClassIdDataAttrs: Boolean = false): TextAndHtml =
    apply(text, embeddedOriginOrEmpty = embeddedOriginOrEmpty,
      isTitle = false, followLinks = followLinks,
      allowClassIdDataAttrs = allowClassIdDataAttrs)

  // COULD escape all CommonMark so becomes real plain text
  def forBodyOrCommentAsPlainTextWithLinks(text: String): TextAndHtml =
    apply(text, embeddedOriginOrEmpty = "",
      isTitle = false, followLinks = false, allowClassIdDataAttrs = false)

  def forHtmlAlready(html: String): TextAndHtml = {
    findLinksEtc(html, RenderCommonmarkResult(html, Set.empty),
        embeddedOriginOrEmpty = "",
        followLinks = false, allowClassIdDataAttrs = false)
  }

  private def apply(
    text: String,
    isTitle: Boolean,
    embeddedOriginOrEmpty: String,
    followLinks: Boolean,
    allowClassIdDataAttrs: Boolean): TextAndHtml = {

    dieIf(isTitle, "TyE593SKD")
    if (isTitle) {  // REMOVE
      val safeHtml = TextAndHtml.sanitizeTitleText(text)
      new TextAndHtmlImpl(text = text, safeHtml = safeHtml,
            uploadRefs = Set.empty,
            externalLinks = Nil, internalLinks = Set.empty,
            usernameMentions = Set.empty,
            linkDomains = Set.empty,
            linkIpAddresses = Nil,
            embeddedOriginOrEmpty = embeddedOriginOrEmpty,
            isTitle = true, followLinks = followLinks,
            allowClassIdDataAttrs = allowClassIdDataAttrs)
    }
    else {
      // Rel links whiting same site: https://moz.com/blog/relative-vs-absolute-urls-whiteboard-friday
      val renderResult = nashorn.renderAndSanitizeCommonMark_new(
            text, site, embeddedOriginOrEmpty = embeddedOriginOrEmpty,
            allowClassIdDataAttrs = allowClassIdDataAttrs, followLinks = followLinks)
      findLinksEtc(text, renderResult, embeddedOriginOrEmpty = embeddedOriginOrEmpty,
            followLinks = followLinks, allowClassIdDataAttrs = allowClassIdDataAttrs)
    }
  }

  private def findLinksEtc(text: String, renderResult: RenderCommonmarkResult,
        embeddedOriginOrEmpty: String,
        followLinks: Boolean, allowClassIdDataAttrs: Boolean): TextAndHtmlImpl = {

    val allLinks = findLinks(renderResult.safeHtml)

    val uploadRefs: Set[UploadRef] =
          UploadsDao.findUploadRefsInLinks(allLinks.toSet, site.pubId)

    var externalLinks = Vector[String]()
    var internalLinks = Set[String]()
    var linkDomains = Set[String]()
    var linkAddresses = Vector[String]()

    allLinks foreach { link =>
        try {
          val uri = new java.net.URI(link)
          val domainOrAddress = uri.getHost

          val (isUrlPath, isSameHostname) =
                if (domainOrAddress eq null) (true, false)
                else {
                  // Would it be good if hosts3 included any non-standard port number,
                  // and protocol? In case an old origin was, say, http://ex.com:8080,
                  // and there was a different site at  http://ex.com  (port 80) ?
                  // So we won't mistake links to origin = ex.com  for pointing
                  // to http://ex.com:8080?  [remember_port]
                  // Doesn't matter in real life, with just http = 80 and https = 443.
                  val isSameHostname = site.allHostnames.contains(domainOrAddress)
                  (false, isSameHostname)
                }

          if (isUrlPath || isSameHostname) {
            internalLinks += link
          }
          else {
            externalLinks :+= link
          }

          if (domainOrAddress.startsWith("[")) {
            if (domainOrAddress.endsWith("]")) {
              // IPv6.
              linkAddresses :+= domainOrAddress
            }
            else {
              // Weird.
              die("TyE305WKUDW2", s"Weird url, starts with '[' but no ']': $domainOrAddress")
            }
          }
          else if (domainOrAddress contains ":") {
            // Cannot happen? Java's getHost() returns the hostname, no port. Instead,
            // getAuthority() includess any port (but not http(s)://).
            die("TyE603KUPRSDJ3", s"Weird url, includes ':': $domainOrAddress")
          }
          else if (Ipv4AddressRegex matches domainOrAddress) {
            linkAddresses :+= domainOrAddress
          }
          else {
            linkDomains += domainOrAddress
          }
        }
        catch {
          case _: Exception =>
            // ignore, the href isn't a valid link, it seems
        }
    }

    new TextAndHtmlImpl(text, renderResult.safeHtml, usernameMentions = renderResult.mentions,
          uploadRefs = uploadRefs,
          externalLinks = externalLinks, internalLinks = internalLinks,
          linkDomains = linkDomains,
          linkIpAddresses = linkAddresses,
          embeddedOriginOrEmpty = embeddedOriginOrEmpty,
          isTitle = false, followLinks = followLinks,
          allowClassIdDataAttrs = allowClassIdDataAttrs)
  }


  /** Creates an instance with both the source and the rendered html set to `text`.
    * This is useful in test suites, because they'll run a lot faster when they won't
    * have to wait for the commonmark renderer to be created.
    */
  def test(text: String, isTitle: Boolean): TextAndHtml = {
    dieIf(Globals.isProd, "EsE7GPM2")
    new TextAndHtmlImpl(text, text, uploadRefs = Set.empty,
          externalLinks = Nil, internalLinks = Set.empty,
          usernameMentions = Set.empty,
          linkDomains = Set.empty, linkIpAddresses = Nil,
          embeddedOriginOrEmpty = "", isTitle = isTitle, followLinks = false,
          allowClassIdDataAttrs = false)
  }

  CLEAN_UP; REMOVE // later, just don't want the diff too large now
  def testTitle(text: String): TitleSourceAndHtml = TitleSourceAndHtml(text)

  def testBody(text: String): TextAndHtml = test(text, isTitle = false)

  def wrapInParagraphNoMentionsOrLinks(text: String, isTitle: Boolean): TextAndHtml = {
    // REMOVE all  isTitle
    new TextAndHtmlImpl(text, s"<p>$text</p>", usernameMentions = Set.empty,
          uploadRefs = Set.empty,
          externalLinks = Nil, internalLinks = Set.empty, linkDomains = Set.empty,
          linkIpAddresses = Nil, embeddedOriginOrEmpty = "",
          isTitle = isTitle, followLinks = false,
          allowClassIdDataAttrs = false)
  }

}



case class StaticSourceAndHtml(
  override val source: String,
  override val safeHtml: String) extends TextAndHtml {

  override def text: String = source
  override def usernameMentions: Set[String] = Set.empty
  override def uploadRefs: Set[UploadRef] = Set.empty
  override def externalLinks: immutable.Seq[String] = Nil
  override def internalLinks: Set[String] = Set.empty
  override def linkDomains: Set[String] = Set.empty
  override def linkIpAddresses: immutable.Seq[String] = Nil
  override def isTitle: Boolean = false
  override def append(moreTextAndHtml: TextAndHtml): TextAndHtml = die("TyE50396SK")
  override def append(text: String): TextAndHtml = die("TyE703RSKTDH")
}
