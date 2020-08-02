/**
 * Copyright (c) 2020 Kaj Magnus Lindberg and Debiki AB
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

package com.debiki.core

import com.debiki.core.Prelude._
import play.api.libs.json.JsValue

// SHOULD rename all column names to camelCase.  .  .

object LinkPreviewTypes {
  // Later:
  val MetaAndOpenGarphTags = 1 // <title> and <description>, and og: ... tags.

  // For now:
  val OEmbed = 3
}

/** Both link_url_c and fetched_from_url_c are part of the database primary key
  * — otherwise an attacker's website A could slightly mess upp the
  * LinkPreiew data for another website, see: [lnpv_t_pk].
  *
  * @param link_url_c: The thing to embed.
  * @param fetched_from_url_c:
  *  Previews for the same link url can get fetched with different oEmbed
  *  maxwidth=... etc params, for different device sizes / resolutions,
  *  and each params combination is then a different fetched_from_url_c.
  *  For OpenGraph, fetched_from_url_c is the same as link_url_c,
  *  but for oEmbed, it instead the oEmbed API endpoint.
  *
  * @param fetched_at_c
  * @param cache_max_secs_c — thereafter, try to re-fetch the preview
  * @param status_code_c: 0 if the request failed, e.g. couldn't connect to server.
  * @param preview_type_c
  * @param first_linked_by_id_c
  * @param content_json_c — as of now: the oEmbed response.
  *   Later: could also be OpenGraph stuff or { title: ... descr: ... }
  *   from < title> and < descr> tags.  JsNull if the request failed.
  */
case class LinkPreview(
  link_url_c: String,
  fetched_from_url_c: String,
  fetched_at_c: When,
  // cache_max_secs_c: Option[Int] — later
  status_code_c: Int,
  preview_type_c: Int, // always oEmbed, for now
  first_linked_by_id_c: UserId,
  content_json_c: JsValue) {

  // For now:
  require(preview_type_c == LinkPreviewTypes.OEmbed, "TyE50RKSDJJ4")

  // Later:
  if (preview_type_c != LinkPreviewTypes.OEmbed) {
    require(link_url_c == fetched_from_url_c, "TyE603MSKU74")
  }
}


case class Link(
  from_post_id_c: PostId,
  link_url_c: String,
  added_at_c: When,
  added_by_id_c: UserId,
  is_external_c: Boolean,
  to_staff_space: Boolean = false,
  to_page_id_c: Option[PageId] = None,
  to_post_id_c: Option[PostId] = None,
  to_pp_id_c: Option[UserId] = None,
  to_tag_id_c: Option[TagDefId] = None,
  to_category_id_c: Option[CategoryId] = None) {

  // Not impl (only to-page links impl)
  dieIf(to_staff_space, "Staff/admin area links not impl [TyE5SKDJ02]")
  dieIf(to_post_id_c.isDefined, "Post links not impl [TyE5SKDJ00]")
  dieIf(to_pp_id_c.isDefined, "Participant links not impl [TyE703WKTDL5]")
  dieIf(to_tag_id_c.isDefined, "Tag links not impl [TyE5928SK]")
  dieIf(to_category_id_c.isDefined, "Category links not impl [TyE5603RDH6]")

  dieIf(is_external_c.toZeroOne + to_staff_space.toZeroOne + to_page_id_c.oneIfDefined +
        to_post_id_c.oneIfDefined + to_pp_id_c.oneIfDefined +
        to_tag_id_c.oneIfDefined + to_category_id_c.oneIfDefined != 1, "TyE063KSUHD5")
}

