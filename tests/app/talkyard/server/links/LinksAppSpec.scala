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


  lazy val pageA: CreatePageResult = createPage2(
        PageType.Discussion, TitleSourceAndHtml("Title A"),
        textAndHtmlMaker.testBody("Body A."), authorId = SystemUserId,
        browserIdData, daoSite1)

  lazy val pageB: CreatePageResult = createPage2(
        PageType.Discussion, TitleSourceAndHtml("Title B"),
        textAndHtmlMaker.testBody("Body B."), authorId = SystemUserId,
        browserIdData, daoSite1)

  lazy val pageC: CreatePageResult = createPage2(
        PageType.Discussion, TitleSourceAndHtml("Title C"),
        textAndHtmlMaker.testBody("Body C."), authorId = SystemUserId,
        browserIdData, daoSite1)


  /*
  lazy val (site2, daoSite2) = createSite("site2")

  lazy val forumSite2Id: PageId = daoSite2.createForum(
        title = "Forum site 2", folder = "/", isForEmbCmts = false,
        Who(SystemUserId, browserIdData)).get.pagePath.pageId
  */


  lazy val userOoo: User = createPasswordOwner("u_ooo234", daoSite1)
  lazy val userMmm: User = createPasswordUser("u_mmm567", daoSite1)

  val extWidgetUrl = "https://ex.co/ext-widget"
  val oembedRequestUrl = s"https://ex.co/oembed?url=$extWidgetUrl"
  val extWidgetOEmbedJsonOrig: JsObject = Json.obj("html" -> JsString("<b>Contents</b>"))
  val extWidgetOEmbedJsonEdited: JsObject = Json.obj("html" -> JsString("<b>Contents</b>"))

  lazy val linkPreviewOrig = LinkPreview(
        link_url_c = extWidgetUrl,
        downloaded_from_url_c = oembedRequestUrl,
        downloaded_at_c = when,
        status_code_c = 200,
        preview_type_c = LinkPreviewTypes.OEmbed,
        first_linked_by_id_c = userMmm.id,
        content_json_c = extWidgetOEmbedJsonOrig)

  lazy val linkPreviewEdited: LinkPreview =
    linkPreviewOrig.copy(
          content_json_c = extWidgetOEmbedJsonEdited)


  lazy val linkAToB: Link = Link(
        from_post_id_c = pageA.bodyPost.id,
        link_url_c = s"/-${pageB.id}",
        added_at_c = globals.now(),
        added_by_id_c = SystemUserId,
        is_external_c = false,
        to_page_id_c = Some(pageB.id),
        to_post_id_c = None,
        to_pp_id_c = None,
        to_tag_id_c = None,
        to_category_id_c = None)

  lazy val linkAToC: Link = linkAToB.copy(
        link_url_c = s"/-${pageC.id}",
        to_page_id_c = Some(pageC.id))



  "prepare: create site 1 and 2, and owners, forums" in {
    // Lazy create things:
    daoSite1 // creates site 1
    //daoSite2 // creates site 2, incl owner
    createForumResult
    userOoo
    userMmm

    // Need to create the pages before the links, because if the pages got
    // created via the `lazy val` links, the page tx:s would start *after* the
    // link tx:es, and foreign keys would fail.
    pageA; pageB; pageC
  }

  "insert, update, find LinkPreview" - {
    "insert" in {
      daoSite1.readWriteTransaction { tx =>
        tx.upsertLinkPreview(linkPreviewOrig)
      }
    }

    "read back" in {
      daoSite1.readTx { tx =>
        tx.loadLinkPreviewByUrl(extWidgetUrl, oembedRequestUrl + "-wrong") mustBe None
        tx.loadLinkPreviewByUrl(extWidgetUrl + "-wrong", oembedRequestUrl) mustBe None
        val prevw = tx.loadLinkPreviewByUrl(extWidgetUrl, oembedRequestUrl).get
        prevw mustBe linkPreviewOrig
      }
    }

    "update" in {
      daoSite1.readWriteTransaction { tx =>
        tx.upsertLinkPreview(
              linkPreviewEdited.copy(
                    // This change should get ignored — only the *first*
                    // user who linked the ext thing, is remembered.
                    first_linked_by_id_c = userOoo.id))
      }
    }

    "read back after update" in {
      daoSite1.readWriteTransaction { tx =>
        val editedPrevw = tx.loadLinkPreviewByUrl(extWidgetUrl, oembedRequestUrl).get
        editedPrevw mustBe linkPreviewEdited
        editedPrevw.first_linked_by_id_c mustBe userMmm.id  // not userOoo
      }
    }
  }


  "insert, update, find Link:s" - {
    "insert" in {
      daoSite1.writeTx(retry = false) { tx =>
        tx.upsertLink(linkAToB)
        tx.upsertLink(linkAToC)
      }
    }

    "find links from a post" in {
      daoSite1.readTx { tx =>
        tx.loadLinksFromPost(345678) mustBe Seq.empty
        tx.loadLinksFromPost(pageA.bodyPost.id) mustBe Seq(linkAToB, linkAToC)
      }
    }

    "find page ids linked from a page" in {
      daoSite1.readTx { tx =>
        tx.loadPageIdsLinkedFrom("23456789") mustBe Seq.empty
        tx.loadPageIdsLinkedFrom(pageA.id) mustBe Seq(linkAToB, linkAToC)
      }
    }

    "find links to a page" in {
      daoSite1.readTx { tx =>
        tx.loadLinksToPage(pageA.id) mustBe Seq.empty
        tx.loadLinksToPage(pageB.id) mustBe Seq(linkAToB)
        tx.loadLinksToPage(pageC.id) mustBe Seq(linkAToC)
      }
    }

    "delete links" in {
      daoSite1.writeTx(retry = false) { tx =>
        tx.deleteLinksFromPost(pageA.bodyPost.id, Set.empty)
        tx.deleteLinksFromPost(pageA.bodyPost.id, Set("/wrong-link"))
        tx.deleteLinksFromPost(pageA.bodyPost.id, Set(linkAToB.link_url_c))
      }
    }

    "link gone" - {
      "find links from post" in {
        daoSite1.readTx { tx =>
          tx.loadLinksFromPost(pageA.bodyPost.id) mustBe Seq(linkAToC)
        }
      }

      "find page ids linked from a page" in {
        daoSite1.readTx { tx =>
          tx.loadPageIdsLinkedFrom(pageA.id) mustBe Seq(linkAToB, linkAToC)
        }
      }

      "find links to a page" in {
        daoSite1.readTx { tx =>
          tx.loadLinksToPage(pageA.id) mustBe Seq.empty
          tx.loadLinksToPage(pageB.id) mustBe Seq.empty
          tx.loadLinksToPage(pageC.id) mustBe Seq(linkAToC)
        }
      }
    }

  }

}

