/**
 * Copyright (C) 2015 Kaj Magnus Lindberg
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

package debiki.onebox   // RENAME to talkyard.server.links

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki.{Globals, TextAndHtml}
import debiki.onebox.engines._
import debiki.TextAndHtml.safeEncodeForHtml
import org.jsoup.Jsoup
import org.scalactic.{ErrorMessage, Or}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import scala.util.{Failure, Success, Try}
import talkyard.server.TyLogging



sealed abstract class RenderPreviewResult


object RenderPreviewResult {

  /** The URL is not to a trusted site, or the HTTP request failed, or whatever went wrong.
    */
  case object NoPreview extends RenderPreviewResult

  /** If a preview was cached already (in link_previews_t),
    * or no external HTTP request needed.
    */
  case class Done(safeHtml: String, placeholder: String) extends RenderPreviewResult

  /** If we sent a HTTP request to download a preview, e.g. an oEmbed request.
    */
  case class Loading(futureSafeHtml: Future[String], placeholder: String)
    extends RenderPreviewResult
}


class RenderPreviewParams(
  val unsafeUrl: String,
  val requesterId: UserId,
  val mayHttpFetchData: Boolean,
  val loadPreviewFromDb: String => Option[LinkPreview],
  val savePreviewInDb: Option[LinkPreview => Unit])



/**
  * - globals — that's s a bit much — COULD instead, incl only what's needed:
  */
abstract class LinkPreviewRenderEngine(globals: Globals) {

  def regex: Regex

  def cssClassName: String

  def handles(url: String): Boolean = regex matches url

  /** If an engine needs to include an iframe, then it'll have to sanitize everything itself,
    * because Google Caja's JsHtmlSanitizer (which we use) removes iframes.
    */
  protected def alreadySanitized = false

  protected def alreadyWrappedInAside = false

  // (?:...) is a non-capturing group.  (for local dev search: /-/u/ below.)
  val uploadsLinkRegex: Regex =
    """=['"](?:(?:(?:https?:)?//[^/]+)?/-/(?:u|uploads/public)/)([a-zA-Z0-9/\._-]+)['"]""".r


  private def pointUrlsToCdn(safeHtml: String): String = {
    val prefix = globals.config.cdn.uploadsUrlPrefix getOrElse {
      return safeHtml
    }
    uploadsLinkRegex.replaceAllIn(safeHtml, s"""="$prefix$$1"""")
  }


  protected def safeBoringLinkTag(unsafeUrl: String, extraClass: String = ""): String = {
    dieIf(safeEncodeForHtml(extraClass) != extraClass, "TyE602RKDJ4")
    val safeUrl = safeEncodeForHtml(unsafeUrl)
    s"""<a href="$safeUrl" rel="nofollow" class="$extraClass">$safeUrl</a>"""
  }

  final def loadRenderSanitize(urlAndFns: RenderPreviewParams): Future[String] = {

    def sanitizeAndWrap(htmlOrError: String Or ErrorMessage): String = {
      val html = htmlOrError getOrIfBad { unsafeError =>
        return i"""
              |<!-- Link preview error: ${safeEncodeForHtml(unsafeError)} -->
              |${safeBoringLinkTag(urlAndFns.unsafeUrl, extraClass = "s_LnPvErr")}"""
      }
      var safeHtml =
        if (alreadySanitized) html
        else {
          // COULD pass info to here so can follow links sometimes? [WHENFOLLOW]
          Jsoup.clean(html, TextAndHtml.relaxedHtmlTagWhitelist)
        }
      // Don't link to any HTTP resources from safe HTTPS pages, e.g. don't link  [1BXHTTPS]
      // to <img src="http://...">, change to https instead even if the image then breaks.
      // COULD leave <a href=...> HTTP links as is so they won't break. And also leave
      // plain text as is. But for now, this is safe and simple and stupid: (?)
      if (globals.secure) {
        safeHtml = safeHtml.replaceAllLiterally("http:", "https:")
      }
      safeHtml = pointUrlsToCdn(safeHtml)
      dieIf(safeEncodeForHtml(cssClassName) != cssClassName, "TyE06RKTDH2")
      if (!alreadyWrappedInAside) {
        safeHtml = s"""<aside class="onebox $cssClassName clearfix">$safeHtml</aside>"""
      }
      safeHtml
    }

    val futureHtml = loadAndRender(urlAndFns)

    // Use if-isCompleted to get an instant result, if possible — Future.map()
    // apparently isn't executed directly, even if the future is completed.
    if (futureHtml.isCompleted) {
      Future.fromTry(futureHtml.value.get.map(sanitizeAndWrap))
    }
    else {
      futureHtml.map(sanitizeAndWrap)(globals.executionContext)
    }
  }


  protected def loadAndRender(urlAndFns: RenderPreviewParams): Future[String Or ErrorMessage]

}



abstract class ExternalRequestLinkPreviewEngine(globals: Globals, siteId: SiteId,
        mayHttpFetchData: Boolean)
  extends LinkPreviewRenderEngine(globals) {
}



abstract class InstantLinkPreviewEngine(globals: Globals)
  extends LinkPreviewRenderEngine(globals) {

  protected def loadAndRender(urlAndFns: RenderPreviewParams)
        : Future[String Or ErrorMessage] = {
    Future.successful(renderInstantly(urlAndFns.unsafeUrl))
  }

  protected def renderInstantly(unsafeUrl: String): String Or ErrorMessage
}


/** What is a link preview? If you type a link to a Twitter tweet or Wikipedia page,
  * Talkyard might download some html from that page, e.g. title, image, description.
  * Or oEmbed html.
  *
  * This usually requires the server to download the linked page from the target website,
  * and extract the relevant parts. When rendering client side, the client sends a request
  * to the Talkyard server and asks it to create a preview. This needs to be done
  * server side, e.g. for security reasons (cannot trust the client to provide
  * the correct html preview).
  *
  * If !mayHttpFetchData, only creates previews if link preview data
  * has been downloaded and saved already in link_previews_t.
  */
class LinkPreviewRenderer(val globals: Globals, val siteId: SiteId,
    mayHttpFetchData: Boolean, requesterId: UserId) extends TyLogging {

  private val pendingRequestsByUrl = mutable.HashMap[String, Future[String]]()
  private val oneboxHtmlByUrl = mutable.HashMap[String, String]()
  private val failedUrls = mutable.HashSet[String]()
  private val PlaceholderPrefix = "onebox-"
  private val NoEngineException = new DebikiException("DwE3KEF7", "No matching preview engine")

  private val executionContext: ExecutionContext = globals.executionContext

  private val engines = Seq[LinkPreviewRenderEngine](
    // COULD_OPTIMIZE These are, or can be made thread safe — no need to recreate all the time.
    new ImagePrevwRendrEng(globals),
    new VideoPrevwRendrEng(globals),
    new GiphyPrevwRendrEng(globals),
    new YouTubePrevwRendrEng(globals),
    new TwitterPrevwRendrEng(globals, siteId, mayHttpFetchData))

  def loadRenderSanitize(url: String): Future[String] = {
    for (engine <- engines) {
      if (engine.handles(url)) {
        val args = new RenderPreviewParams(
              unsafeUrl = url,
              requesterId = requesterId,
              mayHttpFetchData = mayHttpFetchData,
              loadPreviewFromDb = loadPreiewInfoFromDatabase,
              savePreviewInDb =
                    if (!mayHttpFetchData) None
                    else Some(savePreiewInfoToDatabase))
        return engine.loadRenderSanitize(args)
      }
    }

    Future.failed(NoEngineException)
  }

  private def loadPreiewInfoFromDatabase(url: String): Option[LinkPreview] = {
    // Don't create a write tx — could cause deadlocks, because unfortunately
    // we might be inside a tx already: [nashorn_in_tx] (will fix later)
    val siteDao = globals.siteDao(siteId)
    siteDao.readOnlyTransaction { tx =>
      tx.loadLinkPreview(url)
    }
  }

  private def savePreiewInfoToDatabase(linkPreview: LinkPreview): Unit = {
    val siteDao = globals.siteDao(siteId)
    siteDao.readWriteTransaction { tx =>
      tx.upsertLinkPreview(linkPreview)
    }
  }


  def loadRenderSanitizeInstantly(url: String): RenderPreviewResult = {
    def placeholder = PlaceholderPrefix + nextRandomString()

    val futureSafeHtml = loadRenderSanitize(url)
    if (futureSafeHtml.isCompleted)
      return futureSafeHtml.value.get match {
        case Success(safeHtml) => RenderPreviewResult.Done(safeHtml, placeholder)
        case Failure(throwable) => RenderPreviewResult.NoPreview
      }

    futureSafeHtml.onComplete({
      case Success(safeHtml) =>
      case Failure(throwable) =>
    })(executionContext)

    RenderPreviewResult.Loading(futureSafeHtml, placeholder)
  }

}



/** Used when rendering link previwes from inside Javascript code run by Nashorn.
  */
// CHANGE to  LinkPreviewCache(siteTx: ReadOnlySiteTransaction) ?
// But sometimes Nashorn is used inside a tx — would mean we'd open a *read-only*
// tx inside a posibly write tx. Should be fine, right.
// Or construct the LinkPreviewCache outside Nashorn, with any tx already in use,
// and give to Nashorn?
class LinkPreviewRendererForNashorn(val linkPreviewRenderer: LinkPreviewRenderer)
  extends TyLogging {

  private val donePreviews: ArrayBuffer[RenderPreviewResult.Done] = ArrayBuffer()
  private def globals = linkPreviewRenderer.globals

  /** Called from javascript running server side in Nashorn.  [js_scala_interop]
    */
  def renderAndSanitizeOnebox(unsafeUrl: String): String = {
    lazy val safeUrl = org.owasp.encoder.Encode.forHtml(unsafeUrl)

    if (!globals.isInitialized) {
      // Also see the comment for Nashorn.startCreatingRenderEngines()
      return o"""<p style="color: red; outline: 2px solid orange; padding: 1px 5px;">
           Broken onebox for: <a>$safeUrl</a>. Nashorn called out to Scala code
           that uses old stale class files and apparently the wrong classloader (?)
           so singletons are created a second time when inside Nashorn and everything
           is broken. To fix this, restart the server (CTRL+D + run), and edit and save
           this comment again. This problem happens only when Play Framework
           soft-restarts the server in development mode. [DwE4KEPF72]</p>"""
    }

    linkPreviewRenderer.loadRenderSanitizeInstantly(unsafeUrl) match {
      case RenderPreviewResult.NoPreview =>
        UX; COULD // target="_blank" — maybe site conf val? [site_conf_vals]
        s"""<a href="$safeUrl" rel="nofollow">$safeUrl</a>"""
      case donePreview: RenderPreviewResult.Done =>
        donePreviews.append(donePreview)
        // Return a placeholder because `doneOnebox.html` might be an iframe which would
        // be removed by the sanitizer. So replace the placeholder with the html later, when
        // the sanitizer has been run.
        donePreview.placeholder
      case pendingPreview: RenderPreviewResult.Loading =>
        // We cannot call out to external servers from here. That should have been
        // done already, and the results saved in link_previews_t.
        logger.warn(s"No cached preview for: '$unsafeUrl' [TyE306KUT5]")
        i"""
          |<!-- No cached preview -->
          |<a href="$safeUrl" rel="nofollow" class="s_LnPvErr">$safeUrl</a>"""
    }
  }


  def replacePlaceholders(html: String): String = {
    var htmlWithBoxes = html
    for (donePreview <- donePreviews) {
      htmlWithBoxes = htmlWithBoxes.replace(donePreview.placeholder, donePreview.safeHtml)
    }
    htmlWithBoxes
  }

}


