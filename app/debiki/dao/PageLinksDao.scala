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
    readTx(_.loadPageIdsLinkedFrom(pageId))
  }


  def getPageIdsLinkingTo(pageId: PageId): Set[PageId] = {
    memCache.lookup(
          linksKey(pageId),
          orCacheAndReturn = Some {
            loadPageIdsLinkingTo(pageId)
          }).get
  }


  def loadPageIdsLinkingTo(pageId: PageId): Set[PageId] = {
    readTx(_.loadPageIdsLinkingTo(pageId))
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

