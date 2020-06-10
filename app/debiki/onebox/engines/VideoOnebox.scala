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
 * - https://github.com/discourse/onebox/blob/master/lib/onebox/engine/video_onebox.rb
 * - https://github.com/discourse/onebox/blob/master/LICENSE.txt
 */

package debiki.onebox.engines

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki.{Globals, Nashorn}
import debiki.onebox._
import scala.util.Success
import scala.util.matching.Regex



class VideoPrevwRendrEng(globals: Globals)
  extends InstantLinkPreviewEngine(globals) {

  val regex: Regex = """^(https?:)?\/\/.*\.(mov|mp4|m4v|webm|ogv)(\?.*)?$""".r

  val cssClassName = "dw-ob-video"

  override def alreadySanitized = true

  def renderInstantly(unsafeUrl: String): Success[String] = {
    val safeUrl = sanitizeUrl(unsafeUrl)
    Success(o"""
     <video width='100%' height='100%' controls src='$safeUrl'>
       <a href='$safeUrl' target='_blank' rel='nofollow'>$safeUrl</a>
     </video>
    """)
  }

}


