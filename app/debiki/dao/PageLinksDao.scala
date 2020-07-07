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

  memCache.onPageCreated { (_, pagePath: PagePathWithId) =>
    uncacheLinksToPagesLinkedFrom(pagePath.pageId)
  }

  memCache.onPageSaved { sitePageId =>
    uncacheLinksToPagesLinkedFrom(sitePageId.pageId)
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
          linksToKey(pageId),
          orCacheAndReturn = Some {
            loadPageIdsLinkingTo(pageId, inclDeletedHidden = inclDeletedHidden)
          }).get
  }


  def loadPageIdsLinkingTo(pageId: PageId, inclDeletedHidden: Boolean): Set[PageId] = {
    readTx(_.loadPageIdsLinkingToPage(pageId, inclDeletedHidden))
  }


  private def uncacheLinksToPagesLinkedFrom(pageId: PageId): Unit = {
    val linkedPageIds: Set[PageId] = getPageIdsLinkedFrom(pageId)
    linkedPageIds.foreach { id =>
      memCache.remove(linksToKey(id))
    }
  }


  private def linksToKey(pageId: PageId) =
    MemCacheKey(siteId, s"$pageId|PgLns")


}

