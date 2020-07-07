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

package debiki.dao

import com.debiki.core._
import com.debiki.core.Prelude._
import org.scalactic.{ErrorMessage, Or}
import scala.collection.immutable
import scala.collection.mutable



/** Cached things that got out-of-date, should be re-rendered.
  * Since we 1) pass a mutable StaleStuff to "everywhere" (well, not yet,
  * just getting started with this)  — then, forgetting to pass it along,
  * causes a compile time error. And since 2) [[SiteDao.writeTx]] automatically
  * just before the transaction ends, uncaches all stale stuff,
  * it's not so easy to forget to uncache the right things?
  *
  * Mutable. Not thread safe.
  */
class StaleStuff {
  private var _stalePageIdsMemCacheOnly: Set[PageId] = Set.empty
  private var _stalePageIdsInDb: Set[PageId] = Set.empty

  def stalePageIdsInDb: Set[PageId] =
    _stalePageIdsInDb

  def stalePageIdsMemCacheOnly: Set[PageId] =
    _stalePageIdsMemCacheOnly

  def addPageId(pageId: PageId, memCacheOnly: Boolean): Unit = {
    if (memCacheOnly) _stalePageIdsMemCacheOnly += pageId
    else _stalePageIdsInDb += pageId
  }

  def addPageIds(pageIds: Set[PageId]): Unit = {
    _stalePageIdsInDb ++= pageIds
  }
}



trait PageLinksDao {
  self: SiteDao =>

  memCache.onPageCreated { case (_, pagePath: PagePathWithId) =>
    uncacheLinkedPages(pagePath.pageId)
  }

  memCache.onPageSaved { sitePageId =>
    uncacheLinkedPages(sitePageId.pageId)
  }


  def getPageIdsLinkedFrom(pageId: PageId): Set[PageId] = {
    COULD_OPTIMIZE  // cache
    loadPageIdsLinkedFrom(pageId)
  }


  def loadPageIdsLinkedFrom(pageId: PageId): Set[PageId] = {
    readTx(_.loadPageIdsLinkedFromPage(pageId))
  }


  def getPageIdsLinkingTo(pageId: PageId, inclDeletedHidden: Boolean): Set[PageId] = {
    memCache.lookup(
          linksKey(pageId),
          orCacheAndReturn = Some {
            loadPageIdsLinkingTo(pageId, inclDeletedHidden = inclDeletedHidden)
          }).get
  }


  def loadPageIdsLinkingTo(pageId: PageId, inclDeletedHidden: Boolean): Set[PageId] = {
    readTx(_.loadPageIdsLinkingTo(pageId, inclDeletedHidden))
  }


  private def uncacheLinkedPages(pageId: PageId): Unit = {
    val linkedPageIds: Set[PageId] = getPageIdsLinkedFrom(pageId)
    linkedPageIds.foreach { id =>
      memCache.remove(linksKey(id))
    }
  }


  private def linksKey(pageId: PageId) =
    MemCacheKey(siteId, s"$pageId|PgLns")


}

