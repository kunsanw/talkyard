/**
 * Copyright (c) 2016 Kaj Magnus Lindberg
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

package debiki.onebox.engines

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki.{Globals, Nashorn}
import debiki.onebox._
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}


class GiphyPrevwRendrEng(globals: Globals)
  extends InstantLinkPreviewEngine(globals) {

  val regex: Regex =
    """^(https?:)?\/\/giphy\.com\/(gifs|embed)/[a-zA-Z0-9-]*-?[a-zA-Z0-9]+(/html5)?$""".r

  // (?:...) is a non capturing group.
  // *? is like * but non greedy.
  val findIdRegex: Regex =
    """(?:https?:)\/\/[^/]+\/[a-z]+\/[a-zA-Z0-9-]*?-?([a-zA-Z0-9]+)""".r

  val cssClassName = "esOb-Giphy"

  override val alreadySanitized = true

  def renderInstantly(unsafeUrl: String): Try[String] = {
    val unsafeId = findIdRegex.findGroupIn(unsafeUrl) getOrElse {
      return Failure(new QuickMessageException("Cannot find Giphy video id in URL"))
    }

    // The id is [a-zA-Z0-9] so need not be sanitized, but do anyway.
    val safeId = sanitizeUrl(unsafeId)

    COULD // find out if this still works? Or use oEmbed?

    // The hardcoded width & height below are probably incorrect. They can be retrieved
    // via Giphys API: https://github.com/Giphy/GiphyAPI#get-gif-by-id-endpoint
    Success(o"""
     <iframe src="https://giphy.com/embed/$safeId"
       width="480" height="400" frameBorder="0" class="giphy-embed" allowFullScreen>
     </iframe>
      """)
  }

}


