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

package talkyard.server

import com.debiki.core._
import debiki.dao._


package object dao {  CR_DONE // 07-30 .


  /** Remembers things that got out-of-date and should be uncached,
    * e.g. posts html for a page cached in page_html3.
    *
    * Since we 1) pass a mutable StaleStuff to "everywhere" (well, not yet,
    * just getting started with this)  — then, forgetting to pass it along,
    * causes a compile time error. And since 2) [[SiteDao.writeTx]] automatically
    * when the transaction ends, uncaches all stale stuff, it's not so easy
    * to forget to uncache the stale stuff?
    *
    * Mutable. Not thread safe.
    */
  class StaleStuff {
    private var _stalePageIdsMemCacheOnly: Set[PageId] = Set.empty
    private var _stalePageIds: Set[PageId] = Set.empty

    def stalePageIdsMemCacheAndDb: Set[PageId] = _stalePageIds
    def stalePageIdsMemCacheOnly: Set[PageId] = _stalePageIdsMemCacheOnly

    def addPageId(pageId: PageId, memCacheOnly: Boolean): Unit = {
      if (memCacheOnly) _stalePageIdsMemCacheOnly += pageId
      else _stalePageIds += pageId
    }

    def addPageIds(pageIds: Set[PageId]): Unit = {
      _stalePageIds ++= pageIds
    }
  }


}

