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
    require(!inclDeletedHidden, "TyE53KTDP6")  // for now
    // Later: Incl inclDeletedHidden in key, or always load all links,
    // and cache, per link, if the linking page is deleted or hidden?
    RACE // [cache_race_counter] (02526SKB)
    memCache.lookup(
          linksToKey(pageId),
          orCacheAndReturn = Some {
            loadPageIdsLinkingTo(pageId, inclDeletedHidden = false)
          }).get
  }


  def loadPageIdsLinkingTo(pageId: PageId, inclDeletedHidden: Boolean): Set[PageId] = {
    readTx(_.loadPageIdsLinkingToPage(pageId, inclDeletedHidden))
  }


  private def uncacheLinksToPagesLinkedFrom(pageId: PageId): Unit = {
    // Maybe because of a race, the database and cache knows about slightly
    // different links? So uncache based on both?
    val linkedPageIds = loadPageIdsLinkedFrom(pageId)
    val moreIds: Set[PageId] = getPageIdsLinkedFrom(pageId)
    (linkedPageIds ++ moreIds).foreach { id =>
      memCache.remove(linksToKey(id))
      RACE // [cache_race_counter]  might incorrectly get added back here (02526SKB)
    }
  }


  private def linksToKey(pageId: PageId) =
    MemCacheKey(siteId, s"$pageId|PgLns")


}

