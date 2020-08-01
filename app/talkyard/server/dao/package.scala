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
import com.debiki.core.Prelude._
import debiki.dao._
import scala.collection.mutable


package object dao {  CR_DONE // 07-30 .



  case class StalePage(
    pageId: PageId,
    memCacheOnly: Boolean,
    pageModified: Boolean,
    backlinksStale: Boolean,
    ancestorCategoriesStale: Boolean,
    ppNamesStale: Boolean)


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
    private val _stalePages = mutable.Map[PageId, StalePage]()

    def nonEmpty: Boolean = _stalePages.nonEmpty

    def stalePages: Iterator[StalePage] = _stalePages.valuesIterator

    def stalePageIdsInDb: Set[PageId] =
      // COULD_OPTIMIZE calc toSet just once, remember (forget if new page added)
      stalePages.filter(!_.memCacheOnly).map(_.pageId).toSet

    def stalePageIdsInMem: Set[PageId] =
      // That's all stale pages (there's no stale-only-in-database).
      stalePages.map(_.pageId).toSet

    def stalePageIdsInMemIndirectly: Set[PageId] =
      stalePages.filter(p => !p.pageModified).map(_.pageId).toSet

    def stalePageIdsInMemDirectly: Set[PageId] =
      stalePages.filter(p => p.pageModified).map(_.pageId).toSet

    /**
      * @param pageId
      * @param memCacheOnly If page_meta_t.version_c (pages3.version) got bumped,
      *   that's enough — then it's different from page_html_t.version_c already
      *   and the database "knows" the cached html is out-of-date.
      *   Then, pass memCacheOnly = true here.
      *   COULD_OPTIMIZE mostly forgotten to actually do that.
      */
    def addPageId(pageId: PageId, memCacheOnly: Boolean = false,
            pageModified: Boolean = true, backlinksStale: Boolean = false): Unit = {
      dieIf(!pageModified && !backlinksStale, "TyE305KTDT", "Nothing happened")
      val oldEntry = _stalePages.get(pageId)
      val newEntry = oldEntry.map(o =>
            o.copy(
              backlinksStale = o.backlinksStale && backlinksStale,
              memCacheOnly = o.memCacheOnly && memCacheOnly))
          .getOrElse(StalePage(
              pageId,
              memCacheOnly = memCacheOnly,
              pageModified = pageModified,
              backlinksStale = backlinksStale,
              ancestorCategoriesStale = false,
              ppNamesStale = false))

      if (oldEntry isNot newEntry) {
        _stalePages.update(pageId, newEntry)
      }
    }

    def addPageIds(pageIds: Set[PageId], pageModified: Boolean = true,
            backlinksStale: Boolean = false): Unit = {
      pageIds.foreach(
            addPageId(_, pageModified = pageModified, backlinksStale = backlinksStale))
    }

    def pageModified(pageId: PageId): Boolean = {
      _stalePages.get(pageId).exists(_.pageModified)
    }
  }


}

