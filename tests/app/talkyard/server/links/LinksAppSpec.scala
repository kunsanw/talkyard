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
import debiki.dao.{CreateForumResult, DaoAppSuite, SiteDao}
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



  "prepare: create site 1 and 2, and owners, forums" in {
    // Lazy create things:
    daoSite1 // creates site 1
    //daoSite2 // creates site 2, incl owner
    createForumResult
    userOoo
    userMmm
  }

  "insert, find, update, find LinkPreview:s and Link:s" - {
    "insert" in {
      daoSite1.readWriteTransaction { tx =>
        tx.upsertLinkPreview(linkPreviewOrig)
      }
    }

    "read back" in {
      daoSite1.readOnlyTransaction { tx =>
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


  /*
  lazy val pageAaaId: PageId = createPage(
        PageType.Discussion,
        textAndHtmlMaker.testTitle("Page Aaa"),
        textAndHtmlMaker.testBody("Page body aaa."), userMmm.id, browserIdData, daoSite1,
        anyCategoryId = Some(categoryId))

  lazy val pageBbbId: PageId = createPage(
        PageType.Discussion,
        textAndHtmlMaker.testTitle("Page Bbb"),
        textAndHtmlMaker.testBody("Page body bbb."), userMmm.id, browserIdData, daoSite1,
        anyCategoryId = Some(categoryId))

  lazy val linkToPage = Link(
        from_post_id_c: PostId,
        link_url_c: String,
        added_at_c: When,
        added_by_id_c: UserId,
        is_external: Boolean,
        to_post_id_c: Option[PostId],
        to_pp_id_c: Option[UserId],
        to_tag_id_c: Option[TagDefId],
        to_categoy_id_c: Option[CategoryId])

  "remember Link:s between pages and posts" - {

    "create pages and posts" in {


      val second = createPage(PageType.Discussion, textAndHtmlMaker.testTitle("Member Page 4ZM2 b"),
        textAndHtmlMaker.testBody("Page body 4ZM2 b."), member.id, browserIdData, dao,
        anyCategoryId = Some(categoryId))
    }
  } */

}

