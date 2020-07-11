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

// SHOULD_CODE_REVIEW  this whole file later !

package talkyard.server.links

import com.debiki.core._
import debiki.TitleSourceAndHtml
import debiki.dao.{CreateForumResult, CreatePageResult, DaoAppSuite, SiteDao}
import play.api.libs.json.{JsObject, JsString, Json}


class LinksAppSpec extends DaoAppSuite {

  val when: When = When.fromMillis(3100010001000L)
  val createdAt: When = when.minusMillis(10001000)

  lazy val daoSite1: SiteDao = {
    globals.systemDao.getOrCreateFirstSite()
    globals.siteDao(Site.FirstSiteId)
  }


  lazy val createForumResult: CreateForumResult = daoSite1.createForum(
        title = "Forum to delete", folder = "/", isForEmbCmts = false,
        Who(SystemUserId, browserIdData)).get

  lazy val forumId: PageId = createForumResult.pagePath.pageId

  lazy val categoryId: CategoryId = createForumResult.defaultCategoryId

  /*
  lazy val (site2, daoSite2) = createSite("site2")

  lazy val forumSite2Id: PageId = daoSite2.createForum(
        title = "Forum site 2", folder = "/", isForEmbCmts = false,
        Who(SystemUserId, browserIdData)).get.pagePath.pageId
  */


  lazy val userMmm: User = createPasswordUser("u_mmm234", daoSite1)
  lazy val userOoo: User = createPasswordOwner("u_ooo567", daoSite1)



  // ----- External links, oEmbed

  // (We don't need to create any pages or categories for these tests
  // to work.)

  val extLinkOneUrl = "https://ex.co/ext-widget"
  val extLinkOneOEmbUrl = s"https://ex.co/oembed?url=$extLinkOneUrl"
  val extLinkOneOEmbJsonOrig: JsObject = Json.obj("html" -> JsString("<b>Contents</b>"))
  val extLinkOneOEmbJsonEdited: JsObject = Json.obj("html" -> JsString("<i>Edited</i>"))

  val extLinkTwoUrl = "https://ex.two.co/ext-two-widget"
  val extLinkTwoOEmbUrl = s"https://ex.two.co/wow-an-oembed?url=$extLinkTwoUrl"
  val extLinkTwoOEmbJson: JsObject = Json.obj("html" -> JsString("<b>Two Two</b>"))

  lazy val linkPreviewOneOrig: LinkPreview = LinkPreview(
        link_url_c = extLinkOneUrl,
        downloaded_from_url_c = extLinkOneOEmbUrl,
        downloaded_at_c = when,
        // cache_max_secs_c = ... — later
        status_code_c = 200,
        preview_type_c = LinkPreviewTypes.OEmbed,
        first_linked_by_id_c = userMmm.id,
        content_json_c = extLinkOneOEmbJsonOrig)

  lazy val linkPreviewOneEdited: LinkPreview =
    linkPreviewOneOrig.copy(
          content_json_c = extLinkOneOEmbJsonEdited)

  lazy val linkPreviewTwo: LinkPreview = LinkPreview(
        link_url_c = extLinkTwoUrl,
        downloaded_from_url_c = extLinkTwoOEmbUrl,
        downloaded_at_c = when.plusMillis(10),
        // cache_max_secs_c = ... — later
        status_code_c = 200,
        preview_type_c = LinkPreviewTypes.OEmbed,
        first_linked_by_id_c = userOoo.id,
        content_json_c = extLinkTwoOEmbJson)


  "prepare: create site 1 and 2, and owners, forums".in {
    // Lazy create things:
    daoSite1 // creates site 1
    //daoSite2 // creates site 2, incl owner
    createForumResult
    userOoo
    userMmm
  }


  "Link previews: insert, update, find, delete" - {

    "insert".inWriteTx(daoSite1) { (tx, _) =>
      tx.upsertLinkPreview(linkPreviewOneOrig)
      tx.upsertLinkPreview(linkPreviewTwo)
    }

    "read back".inReadTx(daoSite1) { tx =>
      tx.loadLinkPreviewByUrl(extLinkOneUrl, extLinkOneOEmbUrl + "-wrong") mustBe None
      tx.loadLinkPreviewByUrl(extLinkOneUrl + "-wrong", extLinkOneOEmbUrl) mustBe None
      val pv1 = tx.loadLinkPreviewByUrl(extLinkOneUrl, extLinkOneOEmbUrl).get
      pv1 mustBe linkPreviewOneOrig

      val pv2 = tx.loadLinkPreviewByUrl(extLinkTwoUrl, extLinkTwoOEmbUrl).get
      pv2 mustBe linkPreviewTwo

      // Mixing link one and two — no no.
      tx.loadLinkPreviewByUrl(extLinkOneUrl, extLinkTwoOEmbUrl) mustBe None
      tx.loadLinkPreviewByUrl(extLinkTwoUrl, extLinkOneOEmbUrl) mustBe None
    }

    "update".inWriteTx(daoSite1) { (tx, _) =>
      linkPreviewOneOrig.first_linked_by_id_c mustBe userMmm.id // not userOoo, ttt
      tx.upsertLinkPreview(
            linkPreviewOneEdited.copy(
                  // This change should get ignored — only the *first*
                  // user who linked the ext thing, is remembered.
                  first_linked_by_id_c = userOoo.id))
    }

    "read back after update".inReadTx(daoSite1) { tx =>
      val editedPrevw = tx.loadLinkPreviewByUrl(extLinkOneUrl, extLinkOneOEmbUrl).get
      editedPrevw mustBe linkPreviewOneEdited
      editedPrevw.first_linked_by_id_c mustBe userMmm.id  // not userOoo

      info("the 2nd link preview didn't change")
      val pv2 = tx.loadLinkPreviewByUrl(extLinkTwoUrl, extLinkTwoOEmbUrl).get
      pv2 mustBe linkPreviewTwo
    }

    "delete link preview one".inWriteTx(daoSite1) { (tx, _) =>
      tx.deleteLinkPreviews(extLinkOneUrl)
    }

    "... thereafter it's gone".inReadTx(daoSite1) { tx =>
      tx.loadLinkPreviewByUrl(extLinkOneUrl, extLinkOneOEmbUrl) mustBe None

      info("but not the other 2nd ext link preview")
      val pv2 = tx.loadLinkPreviewByUrl(extLinkTwoUrl, extLinkTwoOEmbUrl).get
      pv2 mustBe linkPreviewTwo
    }
  }



  // ----- Internal links

  // The text on these posts and pages doesn't actually link to anything.
  // We'll insert links anyway — we're testing the SQL code, not the CommonMark
  // source parsing & find-links code.

  def createLetterPage(letter: Char, anyCategoryId: Option[CategoryId] = None)
        : CreatePageResult =
    createPage2(
          PageType.Discussion, TitleSourceAndHtml(s"Title $letter"),
          textAndHtmlMaker.testBody(s"Body $letter."), authorId = SystemUserId,
          browserIdData, daoSite1, anyCategoryId = anyCategoryId)

  lazy val pageA: CreatePageResult = createLetterPage('A')
  lazy val pageB: CreatePageResult = createLetterPage('B')
  lazy val pageC: CreatePageResult = createLetterPage('C', Some(categoryId))
  lazy val pageD: CreatePageResult = createLetterPage('D')
  lazy val pageE: CreatePageResult = createLetterPage('E')
  lazy val pageF: CreatePageResult = createLetterPage('F')
  // All links lead to Z, or Q.
  lazy val pageZ: CreatePageResult = createLetterPage('Z')
  lazy val pageQ: CreatePageResult = createLetterPage('Q')

  lazy val pageEReplyOne: Post = reply(
        SystemUserId, pageE.id, text = "pg E re One", parentNr = Some(BodyNr))(daoSite1)

  lazy val pageEReplyTwo: Post = reply(
        SystemUserId, pageE.id, text = "pg E re Two", parentNr = Some(BodyNr))(daoSite1)

  lazy val pageFReplyToHide: Post = reply(
        SystemUserId, pageF.id, text = "pg F re hide", parentNr = Some(BodyNr))(daoSite1)


  // We'll start with links A —> {B, Z} only:
  lazy val linkAToB: Link = Link(
        from_post_id_c = pageA.bodyPost.id,
        link_url_c = s"/-${pageB.id}",
        added_at_c = when,
        added_by_id_c = SystemUserId,
        is_external_c = false,
        to_page_id_c = Some(pageB.id),
        to_post_id_c = None,
        to_pp_id_c = None,
        to_tag_id_c = None,
        to_category_id_c = None)

  lazy val linkAToZ: Link = linkAToB.copy(
        link_url_c = s"/-${pageZ.id}",
        to_page_id_c = Some(pageZ.id))

  // These inserted a bit later:
  lazy val linkBToZ: Link = linkAToZ.copy(from_post_id_c = pageB.bodyPost.id)
  lazy val linkCToZ: Link = linkAToZ.copy(from_post_id_c = pageC.bodyPost.id)
  lazy val linkDToZ: Link = linkAToZ.copy(from_post_id_c = pageD.bodyPost.id)

  // Even later, replies linking to Z:
  lazy val linkEReOneToQ: Link = linkAToZ.copy(
        from_post_id_c = pageEReplyOne.id,
        link_url_c = s"/-${pageQ.id}",
        to_page_id_c = Some(pageQ.id))
  lazy val linkEReTwoToQ: Link = linkEReOneToQ.copy(from_post_id_c = pageEReplyTwo.id)
  lazy val linkFReToQ: Link = linkEReOneToQ.copy(from_post_id_c = pageFReplyToHide.id)


  "Internal links: Insert, update, find, delete" - {

    "prepare: create pages".in {
      // Need to create the pages before the links, because if the pages got
      // created via the `lazy val` links, the page tx:s would start *after* the
      // link tx:es, and foreign keys would fail.
      pageA; pageB; pageC; pageD; pageE; pageF; pageZ; pageQ
    }

    "insert".inWriteTx(daoSite1) { (tx, _) =>
      tx.upsertLink(linkAToB) mustBe true
      tx.upsertLink(linkAToZ) mustBe true
    }

    "find links from a post".inReadTx(daoSite1) { tx =>
      tx.loadLinksFromPost(345678) mustBe Seq.empty
      tx.loadLinksFromPost(pageA.bodyPost.id) mustBe Seq(linkAToB, linkAToZ)
      tx.loadLinksFromPost(pageB.bodyPost.id) mustBe Seq.empty
      tx.loadLinksFromPost(pageZ.bodyPost.id) mustBe Seq.empty
    }

    "find page ids linked from a page".inReadTx(daoSite1) { tx =>
      tx.loadPageIdsLinkedFromPage("23456789") mustBe Set.empty
      tx.loadPageIdsLinkedFromPage(pageA.id) mustBe Set(pageB.id, pageZ.id)
      tx.loadPageIdsLinkedFromPage(pageB.id) mustBe Set.empty
      tx.loadPageIdsLinkedFromPage(pageZ.id) mustBe Set.empty
    }

    "find links to a page".inReadTx(daoSite1) { tx =>
      tx.loadLinksToPage(pageA.id) mustBe Seq.empty
      tx.loadLinksToPage(pageB.id) mustBe Seq(linkAToB)
      tx.loadLinksToPage(pageZ.id) mustBe Seq(linkAToZ)
    }

    "find page ids linking to a page".inReadTx(daoSite1) { tx =>
      tx.loadPageIdsLinkingTo(pageA.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageB.id, inclDeletedHidden = false) mustBe Set(pageA.id)
      tx.loadPageIdsLinkingTo(pageZ.id, inclDeletedHidden = false) mustBe Set(pageA.id)
    }

    "delete link A —> B".inWriteTx(daoSite1) { (tx, _) =>
      tx.deleteLinksFromPost(pageA.bodyPost.id, Set.empty) mustBe 0
      tx.deleteLinksFromPost(pageA.bodyPost.id, Set("/wrong-link")) mustBe 0
      tx.deleteLinksFromPost(pageA.bodyPost.id, Set(linkAToB.link_url_c + "x")) mustBe 0
      tx.deleteLinksFromPost(pageA.bodyPost.id, Set(linkAToB.link_url_c)) mustBe 1
      tx.deleteLinksFromPost(
            pageA.bodyPost.id, Set(linkAToB.link_url_c)) mustBe 0  // already gone
    }

    "link A —> B gone".inReadTx(daoSite1) { tx =>
      info("find links from post")
      tx.loadLinksFromPost(pageA.bodyPost.id) mustBe Seq(linkAToZ)

      info("find page ids linked from page A — now only Z, not B")
      tx.loadPageIdsLinkedFromPage(pageA.id) mustBe Set(pageZ.id)
      tx.loadPageIdsLinkedFromPage(pageB.id) mustBe Set.empty
      tx.loadPageIdsLinkedFromPage(pageZ.id) mustBe Set.empty

      info("find links to page Z:  A —> Z only")
      tx.loadLinksToPage(pageA.id) mustBe Seq.empty
      tx.loadLinksToPage(pageB.id) mustBe Seq.empty
      tx.loadLinksToPage(pageZ.id) mustBe Seq(linkAToZ)

      info("find page ids, A, linking to Z")
      tx.loadPageIdsLinkingTo(pageA.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageB.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageZ.id, inclDeletedHidden = false) mustBe Set(pageA.id)
    }

    "add link: {B,C,D} —> Z".inWriteTx(daoSite1) { (tx, _) =>
      tx.upsertLink(linkBToZ)
      tx.upsertLink(linkCToZ)
      tx.upsertLink(linkDToZ)
    }

    "find link {B,C,D} —> Z".inReadTx(daoSite1) { tx =>
      tx.loadLinksFromPost(pageB.bodyPost.id) mustBe Seq(linkBToZ)
      tx.loadLinksFromPost(pageC.bodyPost.id) mustBe Seq(linkCToZ)
      tx.loadLinksFromPost(pageD.bodyPost.id) mustBe Seq(linkDToZ)
      tx.loadPageIdsLinkedFromPage(pageB.id) mustBe Set(pageZ.id)
      tx.loadPageIdsLinkedFromPage(pageC.id) mustBe Set(pageZ.id)
      tx.loadPageIdsLinkedFromPage(pageD.id) mustBe Set(pageZ.id)

      info("and old link A —> Z too")
      tx.loadLinksFromPost(pageA.bodyPost.id) mustBe Seq(linkAToZ)
      tx.loadPageIdsLinkedFromPage(pageA.id) mustBe Set(pageZ.id)

      info("now Z is linked from many pages: A, B, C, D: Exact links")
      tx.loadLinksToPage(pageA.id) mustBe Seq.empty
      tx.loadLinksToPage(pageB.id) mustBe Seq.empty
      tx.loadLinksToPage(pageC.id) mustBe Seq.empty
      tx.loadLinksToPage(pageD.id) mustBe Seq.empty
      tx.loadLinksToPage(pageZ.id) mustBe Seq(linkAToZ, linkBToZ, linkCToZ, linkDToZ)

      info("... and page ids")
      tx.loadPageIdsLinkingTo(pageA.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageB.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageC.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageD.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageZ.id, inclDeletedHidden = false) mustBe Set(
            pageA.id, pageB.id, pageC.id, pageD.id)
    }

    "delete page A  TyT7RD3LM5".inWriteTx(daoSite1) { (tx, staleStuff) =>
      daoSite1.deletePagesImpl(Seq(pageA.id), SystemUserId, browserIdData,
            doingReviewTask = None)(tx, staleStuff)
    }

    "now only pages B, C and D links to Z  TyT7RD3LM5".inReadTx(daoSite1) { tx =>
      // This loads also links on deleted pages.
      tx.loadLinksToPage(pageZ.id) mustBe Seq(linkAToZ, linkBToZ, linkCToZ, linkDToZ)
      // This skips deleted pages and categories.
      tx.loadPageIdsLinkingTo(pageZ.id, inclDeletedHidden = false) mustBe Set(
            pageB.id, pageC.id, pageD.id)
    }

    "delete page C's category  TyT042RKD36".inWriteTx(daoSite1) { (tx, staleStuff) =>
      daoSite1.deleteUndelCategoryImpl(categoryId, delete = true,
            Who(SystemUserId, browserIdData))(tx)
    }

    "now only page B and D links to Z  TyT042RKD36".inReadTx(daoSite1) { tx =>
      // This loads also links on deleted pages.
      tx.loadLinksToPage(pageZ.id) mustBe Seq(linkAToZ, linkBToZ, linkCToZ, linkDToZ)
      // This skips deleted pages and categories.
      tx.loadPageIdsLinkingTo(pageZ.id, inclDeletedHidden = false) mustBe Set(
            pageB.id, pageD.id)
    }

    "undelete page A —> link back".inWriteTx(daoSite1) { (tx, staleStuff) =>
      daoSite1.deletePagesImpl(Seq(pageA.id), SystemUserId, browserIdData,
            doingReviewTask = None, undelete = true)(tx, staleStuff)
    }

    "undelete category —> link back".inWriteTx(daoSite1) { (tx, staleStuff) =>
      daoSite1.deleteUndelCategoryImpl(categoryId, delete = false,
            Who(SystemUserId, browserIdData))(tx)
    }

    "now page A, B, C, D links to Z".inReadTx(daoSite1) { tx =>
      // This loads also links on deleted pages.
      tx.loadLinksToPage(pageZ.id) mustBe Seq(linkAToZ, linkBToZ, linkCToZ, linkDToZ)

      // This skips deleted pages and categories.
      tx.loadPageIdsLinkingTo(pageZ.id, inclDeletedHidden = false) mustBe Set(
            pageA.id, pageB.id, pageC.id, pageD.id)

      info("No other links")
      tx.loadLinksToPage(pageA.id) mustBe Nil
      tx.loadLinksToPage(pageB.id) mustBe Nil
      tx.loadLinksToPage(pageC.id) mustBe Nil
      tx.loadLinksToPage(pageD.id) mustBe Nil
      tx.loadLinksToPage(pageE.id) mustBe Nil
      tx.loadLinksToPage(pageF.id) mustBe Nil
      tx.loadLinksToPage(pageQ.id) mustBe Nil
      tx.loadPageIdsLinkingTo(pageA.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageB.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageC.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageD.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageE.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageF.id, inclDeletedHidden = false) mustBe Set.empty
      tx.loadPageIdsLinkingTo(pageQ.id, inclDeletedHidden = false) mustBe Set.empty
    }

    "delete page Z".inWriteTx(daoSite1) { (tx, staleStuff) =>
      daoSite1.deletePagesImpl(Seq(pageZ.id), SystemUserId, browserIdData,
            doingReviewTask = None, undelete = true)(tx, staleStuff)

      // now links from ... is Set.empty,  if excl deld linked pages.
    }

    "can find links to deleted page Z".inReadTx(daoSite1) { tx =>
      tx.loadLinksToPage(pageZ.id) mustBe Seq(linkAToZ, linkBToZ, linkCToZ, linkDToZ)
      // This only excludes deleted pages that link to Z, ignores that Z itself
      // is deleted:
      tx.loadPageIdsLinkingTo(pageZ.id, inclDeletedHidden = false) mustBe Set(
            pageA.id, pageB.id, pageC.id, pageD.id)
    }



    "Replies can link too" - {
      "add posts".in {
        pageEReplyOne
        pageEReplyTwo
        pageFReplyToHide
      }

      "Link reply One to Q".inWriteTx(daoSite1) { (tx, _) =>
        tx.upsertLink(linkEReOneToQ) mustBe true
      }

      "now the reply links to Q".inReadTx(daoSite1) { tx =>
        tx.loadLinksToPage(pageQ.id) mustBe Seq(linkEReOneToQ)

        info("and thereby page E too")
        tx.loadPageIdsLinkingTo(pageQ.id, inclDeletedHidden = false) mustBe Set(pageE.id)
      }

      "fake edit the reply: delete the link".inWriteTx(daoSite1) { (tx, _) =>
        tx.deleteLinksFromPost(
              pageEReplyOne.id, Set(linkEReOneToQ.link_url_c))
      }

      "then the link from E to Q is gone".inReadTx(daoSite1) { tx =>
        tx.loadLinksToPage(pageQ.id) mustBe Seq.empty

        info("and page E no longer links to Q")
        tx.loadPageIdsLinkingTo(pageQ.id, inclDeletedHidden = false) mustBe Set.empty
      }

      "Link both reply One and Two to Q".inWriteTx(daoSite1) { (tx, _) =>
        tx.upsertLink(linkEReOneToQ) mustBe true
        tx.upsertLink(linkEReTwoToQ) mustBe true
      }

      "now both posts link to Q".inReadTx(daoSite1) { tx =>
        tx.loadLinksToPage(pageQ.id) mustBe Seq(linkEReOneToQ, linkEReTwoToQ)

        info("and page E too")
        tx.loadPageIdsLinkingTo(pageQ.id, inclDeletedHidden = false) mustBe Set(pageE.id)
      }
    }


    "Links from deleted posts are ignored  TyT602AMDUN" - {

      "Delete reply One".inWriteTx(daoSite1) { (tx, staleStuff) =>
        daoSite1.deletePostImpl(
          pageE.id, pageEReplyOne.nr, deletedById = SystemUserId,
          doingReviewTask = None, browserIdData, tx, staleStuff)
      }

      "Reply Two links to Q, and Re One too although post deleted".inReadTx(daoSite1) { tx =>
        tx.loadLinksToPage(pageQ.id) mustBe Seq(linkEReOneToQ, linkEReTwoToQ)

        info("page E still links to Q, because of reply Two (not deleted)")
        tx.loadPageIdsLinkingTo(pageQ.id, inclDeletedHidden = false) mustBe Set(pageE.id)
      }

      "Delete reply Two too".inWriteTx(daoSite1) { (tx, staleStuff) =>
        daoSite1.deletePostImpl(
          pageE.id, pageEReplyTwo.nr, deletedById = SystemUserId,
          doingReviewTask = None, browserIdData, tx, staleStuff)
      }

      "now the posts still link to Q".inReadTx(daoSite1) { tx =>
        tx.loadLinksToPage(pageQ.id) mustBe Seq(linkEReOneToQ, linkEReTwoToQ)

        info("but page E doesn't, when deleted posts skipped")
        tx.loadPageIdsLinkingTo(pageQ.id, inclDeletedHidden = false) mustBe Set.empty
      }

      "Delete the links".inWriteTx(daoSite1) { (tx, staleStuff) =>
        tx.deleteLinksFromPost(pageEReplyOne.id, Set(linkEReOneToQ.link_url_c))
        tx.deleteLinksFromPost(pageEReplyTwo.id, Set(linkEReTwoToQ.link_url_c))
      }

      "now the posts don't link to Q".inReadTx(daoSite1) { tx =>
        tx.loadLinksToPage(pageQ.id) mustBe Nil
        tx.loadPageIdsLinkingTo(pageQ.id, inclDeletedHidden = false) mustBe Set.empty
      }
    }


    "Links from hidden posts are ignored  TyT5KD20G7" - {

      "add Reply One on page F linking to Q".inWriteTx(daoSite1) { (tx, _) =>
        tx.upsertLink(linkFReToQ) mustBe true
      }

      "Oh so many links to Q".inReadTx(daoSite1) { tx =>
        info("exact links")
        tx.loadLinksToPage(pageQ.id) mustBe Seq(linkFReToQ)

        info("page ids")
        tx.loadPageIdsLinkingTo(pageQ.id, inclDeletedHidden = false) mustBe Set(pageF.id)
      }

      "hide page F's reply that links to Q".inWriteTx(daoSite1) { (tx, staleStuff) =>
        daoSite1.hidePostsOnPage(Seq(pageFReplyToHide), pageId = pageF.id,
          reason = "Test test")(tx, staleStuff)
      }

      "the link is still there".inReadTx(daoSite1) { tx =>
        tx.loadLinksToPage(pageQ.id) mustBe Seq(linkFReToQ)

        info("but page F doesn't link to Q, when hidden posts skipped")
        tx.loadPageIdsLinkingTo(pageQ.id, inclDeletedHidden = false) mustBe Set.empty
      }
    }

  }

}

