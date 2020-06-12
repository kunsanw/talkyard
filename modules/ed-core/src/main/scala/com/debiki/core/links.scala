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
import play.api.libs.json.JsObject


object LinkPreviewTypes {
  // Later:
  val MetaAndOpenGarphTags = 1 // <title> and <description>, and og: ... tags.

  // For now:
  val OEmbed = 3
}

/** Both link_url_c and downloaded_from_url_c are part of the database primary key
  * — otherwise an attacker's website A could hijack a widget from a normal
  * @param downloaded_at_c
  * @param preview_type_c
  * @param first_linked_by_id_c
  * @param content_json_c — as of now: the oEmbed response.
  *   Later: could also be OpenGraph stuff or { title: ... descr: ... }
  *   from < title> and < descr> tags.
  */
case class LinkPreview(
  link_url_c: String,
  downloaded_from_url_c: String,
  downloaded_at_c: When,
  preview_type_c: Int, // always oEmbed, for now
  first_linked_by_id_c: UserId,
  content_json_c: JsObject) {

  require(preview_type_c == LinkPreviewTypes.OEmbed, "TyE50RKSDJJ4")
}


case class Link(
  from_post_id_c: PostId,
  link_url_c: String,
  added_at_c: When,
  added_by_id_c: UserId,
  is_external: Boolean,
  to_post_id_c: Option[PostId],
  to_pp_id_c: Option[UserId],
  to_tag_id_c: Option[TagDefId],
  to_categoy_id_c: Option[CategoryId]) {

  // Not impl
  dieIf(to_tag_id_c ne null, "tag links not impl [TyE5928SK]")

  dieIf(is_external.toZeroOne + to_post_id_c.oneIfDefined + to_pp_id_c.oneIfDefined +
        to_tag_id_c.oneIfDefined + to_categoy_id_c.oneIfDefined != 1, "TyE063KSUHD5")

}
