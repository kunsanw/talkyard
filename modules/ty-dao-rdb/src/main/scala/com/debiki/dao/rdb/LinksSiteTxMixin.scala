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

package com.debiki.dao.rdb

import com.debiki.core._
import com.debiki.core.Prelude._
import java.{sql => js}
import Rdb._
import RdbUtil.makeInListFor
import play.api.libs.json.JsNull
import scala.collection.mutable.ArrayBuffer



trait LinksSiteTxMixin extends SiteTransaction {
  self: RdbSiteTransaction =>


  override def upsertLinkPreview(linkPreview: LinkPreview): Unit = {
    val upsertStatement = s"""
          insert into link_previews_t (
              site_id_c,
              link_url_c,
              downloaded_from_url_c,
              downloaded_at_c,
              status_code_c,
              preview_type_c,
              first_linked_by_id_c,
              content_json_c)
          values (?, ?, ?, ?, ?, ?, ?, ?)
          on conflict (site_id_c, link_url_c, downloaded_from_url_c)
          do update set
              downloaded_at_c = excluded.downloaded_at_c,
              status_code_c = excluded.status_code_c,
              preview_type_c = excluded.preview_type_c,
              content_json_c = excluded.content_json_c """

    val values = List(
          siteId.asAnyRef,
          linkPreview.link_url_c,
          linkPreview.downloaded_from_url_c,
          linkPreview.downloaded_at_c.asTimestamp,
          linkPreview.status_code_c.asAnyRef,
          linkPreview.preview_type_c.asAnyRef,
          linkPreview.first_linked_by_id_c.asAnyRef,
          linkPreview.content_json_c)

    runUpdateSingleRow(upsertStatement, values)
  }


  override def loadLinkPreviewByUrl(linkUrl: String, downloadUrl: String)
  : Option[LinkPreview] = {
    val query = s"""
          select * from link_previews_t
          where site_id_c = ?
            and link_url_c = ?
            and downloaded_from_url_c = ?  """
    val values = List(siteId.asAnyRef, linkUrl, downloadUrl)
    runQueryFindOneOrNone(query, values, rs => {
      parseLinkPreview(rs)
    })
  }


  override def deleteLinkPreviews(linkUrl: String): Boolean = {
    val deleteStatement = s"""
          delete from link_previews_t
          where site_id_c = ?
            and link_url_c = ?  """
    val values = List(siteId.asAnyRef, linkUrl)
    runUpdateSingleRow(deleteStatement, values)
  }


  override def upsertLink(link: Link): Boolean = {
    val upsertStatement = s"""
          insert into link_t (
              site_id_c,
              from_post_id_c,
              link_url_c,
              added_at_c,
              added_by_id_c,
              is_external,
              to_post_id_c,
              to_pp_id_c,
              to_tag_id_c,
              to_categoy_id_c)
          values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
          on conflict (site_id_c, from_post_id_c, link_url_c)
             do nothing """

    val values = List(
      siteId.asAnyRef,
      link.from_post_id_c.asAnyRef,
      link.link_url_c,
      link.added_at_c.asTimestamp,
      link.added_by_id_c.asAnyRef,
      link.is_external.asTrueOrNull,
      link.to_post_id_c.asAnyRef,
      link.to_pp_id_c.asAnyRef,
      link.to_tag_id_c.asAnyRef,
      link.to_categoy_id_c.asAnyRef)

    runUpdateSingleRow(upsertStatement, values)
  }


  override def deleteLinkFromPost(postId: PostId, url: String): Boolean = {
    val deleteStatement = s"""
          delete from links_t
          where site_id_c = ?
            and from_post_id_c = ?
            and link_url_c = ?
          """
    val values = List(siteId.asAnyRef, postId.asAnyRef, url)
    runUpdateSingleRow(deleteStatement, values)
  }


  override def deleteAllLinksFromPost(postId: PostId): Boolean = {
    val deleteStatement = s"""
          delete from links_t
          where site_id_c = ?
            and from_post_id_c = ?
          """
    val values = List(siteId.asAnyRef, postId.asAnyRef)
    runUpdateSingleRow(deleteStatement, values)
  }


  override def loadLinksToPage(pageId: PageId): Seq[Link] = {
    val query = s"""
          select * from posts3 ps inner join links_t ls
            on ps.site_id = ls.site_id_c
            and ps.unique_post_id = ls.to_post_id_c
          where site_id_c = ?
            and ps.page_id = ?
          """

    val values = List(siteId.asAnyRef, pageId)

    runQueryFindMany(query, values, rs => {
      parseLink(rs)
    })
  }


  private def parseLinkPreview(rs: js.ResultSet): LinkPreview = {
    LinkPreview(
          link_url_c = getString(rs, "link_url_c"),
          downloaded_from_url_c = getString(rs, "downloaded_from_url_c"),
          downloaded_at_c = getWhen(rs, "downloaded_at_c"),
          status_code_c = getInt(rs, "status_code_c"),
          preview_type_c = getInt(rs, "preview_type_c"),
          first_linked_by_id_c = getInt(rs, "first_linked_by_id_c"),
          content_json_c = getOptJsObject(rs, "content_json_c").getOrElse(JsNull))
  }


  private def parseLink(rs: js.ResultSet): Link = {
    Link(
          from_post_id_c = getInt(rs, "from_post_id_c"),
          link_url_c = getString(rs, "link_url_c"),
          added_at_c = getWhen(rs, "added_at_c"),
          added_by_id_c = getInt(rs, "added_by_id_c"),
          is_external = getOptBool(rs, "is_external") is true,
          to_post_id_c = getOptInt(rs, "to_post_id_c"),
          to_pp_id_c = getOptInt(rs, "to_pp_id_c"),
          to_tag_id_c = getOptInt(rs, "to_tag_id_c"),
          to_categoy_id_c = getOptInt(rs, "to_categoy_id_c"))
  }

}
