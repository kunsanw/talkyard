/**
 * Copyright (c) 2015 Kaj Magnus Lindberg
 * Parts Copyright (c) 2013 jzeta (Joanna Zeta)
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
 *
 * The parts copyrighted by jzeta are available under the MIT license:
 * - https://github.com/discourse/onebox/blob/master/lib/onebox/engine/image_onebox.rb
 * - https://github.com/discourse/onebox/blob/master/LICENSE.txt
 */

package debiki.onebox.engines

import debiki.Globals
import debiki.onebox._
import debiki.TextAndHtml.safeEncodeForHtml
import org.scalactic.Good
import scala.util.matching.Regex



class ImagePrevwRendrEng(globals: Globals)
  extends InstantLinkPreviewEngine(globals) {

  val regex: Regex = """^(https?:)?\/\/.+\.(png|jpg|jpeg|gif|bmp|tif|tiff)(\?.*)?$""".r

  val cssClassName = "dw-ob-image"

  override val alreadySanitized = true

  def renderInstantly(url: String): Good[String] = {
    var betterUrl = url
    // Fix Dropbox image links.
    if (url startsWith "https://www.dropbox.com/") {
      betterUrl = url.replaceAllLiterally(
        "https://www.dropbox.com", "https://dl.dropboxusercontent.com")
    }

    val safeUrl = safeEncodeForHtml(url)

    Good(s"<a href='$safeUrl' rel='nofollow' target='_blank'><img src='$safeUrl'></a>")
  }

}


