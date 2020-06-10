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
  val TitleDescrTags = 1 // <title> and <description>
  val OpenGraph = 2

  // For now:
  val OEmbed = 3
}

case class LinkPreview(
  link_url_c: String,
  downloaded_from_url_c: String,
  downloaded_at_c: When,
  preview_type_c: Int, // always oEmbed, for now
  first_linked_by_c: UserId,
  content_json_c: JsObject) {

  require(preview_type_c == LinkPreviewTypes.OEmbed, "TyE50RKSDJJ4")
}


case class Link(
  from_post_c: PostId,
  link_url_c: String,
  added_at_c: When,
  added_by_c: UserId,
  is_external: Boolean,
  to_post_c: Option[PostId],
  to_pp_c: Option[UserId],
  to_tag_c: Option[TagDefId],
  to_categoy_c: Option[CategoryId]) {

  // Not impl
  dieIf(to_tag_c ne null, "tag links not impl [TyE5928SK]")

  dieIf(is_external.toZeroOne + to_post_c.oneIfDefined + to_pp_c.oneIfDefined +
        to_tag_c.oneIfDefined + to_categoy_c.oneIfDefined != 1, "TyE063KSUHD5")

}
