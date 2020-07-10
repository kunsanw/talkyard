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

// SHOULD_CODE_REVIEW  this whole file later

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


  "prepare: create site 1 and 2, and owners, forums" in {
    // Lazy create things:
    daoSite1 // creates site 1
    //daoSite2 // creates site 2, incl owner
    createForumResult
    userOoo
    userMmm
  }

  "Link previews: insert, update, find, delete" - {
    "insert" in {
      daoSite1.writeTx { (tx, _) =>
        tx.upsertLinkPreview(linkPreviewOneOrig)
        tx.upsertLinkPreview(linkPreviewTwo)
      }
    }

    "read back" in {
      daoSite1.readTx { tx =>
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
    }

    "update" in {
      daoSite1.writeTx { (tx, _) =>
        linkPreviewOneOrig.first_linked_by_id_c mustBe userMmm.id // not userOoo, ttt
        tx.upsertLinkPreview(
              linkPreviewOneEdited.copy(
                    // This change should get ignored — only the *first*
                    // user who linked the ext thing, is remembered.
                    first_linked_by_id_c = userOoo.id))
      }
    }

    "read back after update" in {
      daoSite1.readTx { tx =>
        val editedPrevw = tx.loadLinkPreviewByUrl(extLinkOneUrl, extLinkOneOEmbUrl).get
        editedPrevw mustBe linkPreviewOneEdited
        editedPrevw.first_linked_by_id_c mustBe userMmm.id  // not userOoo
      }
    }

    "the 2nd link preview didn't change" in {
      daoSite1.readTx { tx =>
        val pv2 = tx.loadLinkPreviewByUrl(extLinkTwoUrl, extLinkTwoOEmbUrl).get
        pv2 mustBe linkPreviewTwo
      }
    }

    "delete link preview one" in {
      daoSite1.writeTx { (tx, _) =>
        tx.deleteLinkPreviews(extLinkOneUrl)
      }
    }

    "... thereafter it's gone" in {
      daoSite1.readTx { tx =>
        tx.loadLinkPreviewByUrl(extLinkOneUrl, extLinkOneOEmbUrl) mustBe None
      }
    }

    "... but not the other 2nd ext link preview" in {
      daoSite1.readTx { tx =>
        val pv2 = tx.loadLinkPreviewByUrl(extLinkTwoUrl, extLinkTwoOEmbUrl).get
        pv2 mustBe linkPreviewTwo
      }
    }
  }



  // ----- Internal links

  // The text on these posts and pages doesn't actually link to anything.
  // We'll insert links anyway — we're testing the SQL code, not the CommonMark
  // source parsing & find-links code.

  lazy val pageA: CreatePageResult = createPage2(
        PageType.Discussion, TitleSourceAndHtml("Title A"),
        textAndHtmlMaker.testBody("Body A."), authorId = SystemUserId,
        browserIdData, daoSite1)

  lazy val pageAReplyOneToDelete: Post = reply(
        userMmm.id, pageA.id, text = "Re One", parentNr = Some(BodyNr))(daoSite1)

  lazy val pageAReplyTwoToHide: Post = reply(
        userOoo.id, pageA.id, text = "Re Two", parentNr = Some(BodyNr))(daoSite1)

  lazy val pageAReplyThreeToKeep: Post = reply(
        userMmm.id, pageA.id, text = "Re Three", parentNr = Some(BodyNr))(daoSite1)

  lazy val pageB: CreatePageResult = createPage2(
        PageType.Discussion, TitleSourceAndHtml("Title B"),
        textAndHtmlMaker.testBody("Body B."), authorId = SystemUserId,
        browserIdData, daoSite1)

  lazy val pageC: CreatePageResult = createPage2(
        PageType.Discussion, TitleSourceAndHtml("Title C"),
        textAndHtmlMaker.testBody("Body C."), authorId = SystemUserId,
        browserIdData, daoSite1)

  lazy val pageD: CreatePageResult = createPage2(
        PageType.Discussion, TitleSourceAndHtml("Title D"),
        textAndHtmlMaker.testBody("Body D."), authorId = SystemUserId,
        browserIdData, daoSite1,
        anyCategoryId = Some(categoryId))

  lazy val pageZ: CreatePageResult = createPage2(
        PageType.Discussion, TitleSourceAndHtml("Title Z"),
        textAndHtmlMaker.testBody("Body Z."), authorId = SystemUserId,
        browserIdData, daoSite1)

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


  "Internal links: Insert, update, find, delete" - {

    "prepare: create pages" in {
      // Need to create the pages before the links, because if the pages got
      // created via the `lazy val` links, the page tx:s would start *after* the
      // link tx:es, and foreign keys would fail.
      pageA; pageB; pageC; pageD; pageZ
    }

    "insert" in {
      daoSite1.writeTx { (tx, _) =>
        tx.upsertLink(linkAToB)
        tx.upsertLink(linkAToZ)
      }
    }

    "find links from a post" in {
      daoSite1.readTx { tx =>
        tx.loadLinksFromPost(345678) mustBe Seq.empty
        tx.loadLinksFromPost(pageA.bodyPost.id) mustBe Seq(linkAToB, linkAToZ)
        tx.loadLinksFromPost(pageB.bodyPost.id) mustBe Seq.empty
        tx.loadLinksFromPost(pageZ.bodyPost.id) mustBe Seq.empty
      }
    }

    "find page ids linked from a page" in {
      daoSite1.readTx { tx =>
        tx.loadPageIdsLinkedFromPage("23456789") mustBe Set.empty
        tx.loadPageIdsLinkedFromPage(pageA.id) mustBe Set(pageB.id, pageZ.id)
        tx.loadPageIdsLinkedFromPage(pageB.id) mustBe Set.empty
        tx.loadPageIdsLinkedFromPage(pageZ.id) mustBe Set.empty
      }
    }

    "find links to a page" in {
      daoSite1.readTx { tx =>
        tx.loadLinksToPage(pageA.id) mustBe Seq.empty
        tx.loadLinksToPage(pageB.id) mustBe Seq(linkAToB)
        tx.loadLinksToPage(pageZ.id) mustBe Seq(linkAToZ)
      }
    }

    "find page ids linking to a page" in {
      daoSite1.readTx { tx =>
        tx.loadPageIdsLinkingTo(pageA.id, inclDeletedHidden = false) mustBe Set.empty
        tx.loadPageIdsLinkingTo(pageB.id, inclDeletedHidden = false) mustBe Set(pageA.id)
        tx.loadPageIdsLinkingTo(pageZ.id, inclDeletedHidden = false) mustBe Set(pageA.id)
      }
    }

    "delete link A —> B" in {
      daoSite1.writeTx { (tx, _) =>
        tx.deleteLinksFromPost(pageA.bodyPost.id, Set.empty) mustBe 0
        tx.deleteLinksFromPost(pageA.bodyPost.id, Set("/wrong-link")) mustBe 0
        tx.deleteLinksFromPost(pageA.bodyPost.id, Set(linkAToB.link_url_c)) mustBe 1
      }
    }

    "link A —> B gone" - {
      "find links from post" in {
        daoSite1.readTx { tx =>
          tx.loadLinksFromPost(pageA.bodyPost.id) mustBe Seq(linkAToZ)
        }
      }

      "find page ids linked from a page — now only Z, not B" in {
        daoSite1.readTx { tx =>
          tx.loadPageIdsLinkedFromPage(pageA.id) mustBe Set(pageZ.id)
          tx.loadPageIdsLinkedFromPage(pageB.id) mustBe Set.empty
          tx.loadPageIdsLinkedFromPage(pageZ.id) mustBe Set.empty
        }
      }

      "find links to a page" in {
        daoSite1.readTx { tx =>
          tx.loadLinksToPage(pageA.id) mustBe Seq.empty
          tx.loadLinksToPage(pageB.id) mustBe Seq.empty
          tx.loadLinksToPage(pageZ.id) mustBe Seq(linkAToZ)
        }
      }

      "find page ids linking to a page" in {
        daoSite1.readTx { tx =>
          tx.loadPageIdsLinkingTo(pageA.id, inclDeletedHidden = false) mustBe Set.empty
          tx.loadPageIdsLinkingTo(pageB.id, inclDeletedHidden = false) mustBe Set.empty
          tx.loadPageIdsLinkingTo(pageZ.id, inclDeletedHidden = false) mustBe Set(pageA.id)
        }
      }
    }

    "add link: {B,C,D} —> Z" in {
      daoSite1.writeTx { (tx, _) =>
        tx.upsertLink(linkBToZ)
        tx.upsertLink(linkCToZ)
        tx.upsertLink(linkDToZ)
      }
    }

    "find link B —> Z" in {
      daoSite1.readTx { tx =>
        tx.loadLinksFromPost(pageB.bodyPost.id) mustBe Seq(linkBToZ)
        tx.loadLinksFromPost(pageC.bodyPost.id) mustBe Seq(linkCToZ)
        tx.loadLinksFromPost(pageD.bodyPost.id) mustBe Seq(linkDToZ)
        tx.loadPageIdsLinkedFromPage(pageB.id) mustBe Set(pageZ.id)
        tx.loadPageIdsLinkedFromPage(pageC.id) mustBe Set(pageZ.id)
        tx.loadPageIdsLinkedFromPage(pageD.id) mustBe Set(pageZ.id)

        info("and old link A —> Z too")
        tx.loadLinksFromPost(pageA.bodyPost.id) mustBe Seq(linkAToZ)
        tx.loadPageIdsLinkedFromPage(pageA.id) mustBe Set(pageZ.id)
      }
    }

    "now Z is linked from many pages: A, B, C, D" in {
      daoSite1.readTx { tx =>
        info("exact links")
        tx.loadLinksToPage(pageA.id) mustBe Seq.empty
        tx.loadLinksToPage(pageB.id) mustBe Seq.empty
        tx.loadLinksToPage(pageC.id) mustBe Seq.empty
        tx.loadLinksToPage(pageD.id) mustBe Seq.empty
        tx.loadLinksToPage(pageZ.id) mustBe Seq(linkAToZ, linkBToZ, linkCToZ, linkDToZ)

        info("page ids")
        tx.loadPageIdsLinkingTo(pageA.id, inclDeletedHidden = false) mustBe Set.empty
        tx.loadPageIdsLinkingTo(pageB.id, inclDeletedHidden = false) mustBe Set.empty
        tx.loadPageIdsLinkingTo(pageZ.id, inclDeletedHidden = false) mustBe Set(
              pageA.id, pageB.id, pageC.id, pageD.id)
      }
    }

    "delete page A's Reply One that links to Z" - {
      TESTS_MISSING  //  [q_deld_lns]  wip
      "delete Reply One" in {
        daoSite1.writeTx { (tx, staleStuff) =>
          daoSite1.deletePostImpl(pageA.id, pageAReplyOneToDelete.nr,
                deletedById = SystemUserId, doingReviewTask = None, browserIdData,
                tx, staleStuff)
        }
      }
      // Thereafter,  tx.loadPageIdsLinkingTo() skips link  A —> Z
    }

    "hide page A's Reply Two that links to Z" - {
      "hide Reply Two" in {
        daoSite1.writeTx { (tx, staleStuff) =>
          daoSite1.hidePostsOnPage(Seq(pageAReplyTwoToHide), pageId = pageA.id,
                reason = "Test test")(tx, staleStuff)
        }
      }
    }

    "delete page B" - {
      "delete page B" in {
        daoSite1.writeTx { (tx, staleStuff) =>
          daoSite1.deletePagesImpl(Seq(pageB.id), SystemUserId, browserIdData,
                doingReviewTask = None)(tx, staleStuff)
        }
      }
    }

    "delete page C's category" - {
      "delete category" in {
        daoSite1.writeTx { (tx, staleStuff) =>
          daoSite1.deleteUndelCategoryImpl(categoryId, delete = true,
                Who(SystemUserId, browserIdData))(tx)
        }
      }
    }

    "now only page D links to Z" - {
    }

    "undelete page B —> link back" - {
      "undelete page B" in {
        daoSite1.writeTx { (tx, staleStuff) =>
          daoSite1.deletePagesImpl(Seq(pageB.id), SystemUserId, browserIdData,
                doingReviewTask = None, undelete = true)(tx, staleStuff)
        }
      }
    }

    "undelete category —> link back" - {
      "undelete category" in {
        daoSite1.writeTx { (tx, staleStuff) =>
          daoSite1.deleteUndelCategoryImpl(categoryId, delete = false,
                Who(SystemUserId, browserIdData))(tx)
        }
      }
    }

    "delete page Z" - {
      "delete page Z" in {
        daoSite1.writeTx { (tx, _) =>
        }
      }

      // now links from ... is Set.empty,  if excl deld linked pages.
    }

  }

}

