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
 */

package debiki.onebox.engines

import com.debiki.core._
import com.debiki.core.Prelude._
import debiki.{Globals, TextAndHtml}
import debiki.onebox._
import org.scalactic.{Bad, ErrorMessage, Good, Or}
import scala.util.matching.Regex


// Instant link preview engines don't send any external http requests,
// but knows directly how to format a link as html.
//
// Sorted alphabetically:
//
// Image links (move to this file later)
// Telegram
// Video links: mp4  (move to this file later)

// (Giphy? YouTube? But use oEmbed for them instead)



class TelegramPrevwRendrEng(globals: Globals) extends InstantLinkPreviewEngine(globals) {

  override def regex: Regex =
    """^https://t\.me/([a-zA-Z0-9]+/[0-9]+)$""".r

  def providerLnPvCssClassName = "s_LnPv-Telegram"

  override def alreadySanitized = true


  def renderInstantly(unsafeUrl: String): String Or LinkPreviewProblem = {
    val messageId = (regex findGroupIn unsafeUrl) getOrElse {
      return Bad(LinkPreviewProblem(
            "Couldn't find message id in Telegram link [TyETELEGRAMLN]",
            unsafeUrl = unsafeUrl, "TyE0TLGRMID"))
    }

    //"durov/68" "telegram/83"

    val safeMessageId = TextAndHtml.safeEncodeForHtmlAttrOnly(messageId)

    // Look at the regex — messageId should be safe already.
    dieIf(safeMessageId != messageId, "TyE50SKDGJ5")

    // This is what Telegram's docs says we should embed: ...
    /*
    val unsafeScriptWithMessageId =
          """<script async src="https://telegram.org/js/telegram-widget.js?9" """ +
            s"""data-telegram-post="$safeMessageId" data-width="100%"></script>"""

    val safeHtml = SandboxedAutoSizeIframe.makeSafePreviewHtml(
          unsafeUrl = unsafeUrl, unsafeHtml = unsafeScriptWithMessageId,
          unsafeProviderName = Some("Telegram"),
          extraLnPvCssClasses = extraLnPvCssClasses)

    return Good(safeHtml)   */

    // ... HOWEVER then Telegram refuses to show that contents — because
    // Telegram creates an iframe that refuses to appear when nested in
    // Talkyard's sandboxed iframe.
    // There's this error:
    //   68:1 Access to XMLHttpRequest at 'https://t.me/durov/68?embed=1' from
    //   origin 'null' has been blocked by CORS policy: No 'Access-Control-Allow-Origin'
    //   header is present on the requested resource.
    // Happens in Telegram's  'initWidget',
    //   https://telegram.org/js/telegram-widget.js?9   line 199:
    //       widgetEl.parentNode.insertBefore(iframe, widgetEl);
    // apparently Telegram loads its own iframe, but that won't work, because
    // Talkyard's sandboxed iframe is at cross-origin domain "null",
    // and becasue (?) Telegram's iframe request has:
    //    Sec-Fetch-Site: cross-site
    // but Telegram's response lacks any Access-Control-Allow-Origin header.

    // Instead, let's load the Telegram iframe ourselves instead;
    // this seems to work:

    // Iframe sandbox permissions. [IFRMSNDBX]
    val permissions =
          "allow-popups " +
          "allow-popups-to-escape-sandbox " +
          "allow-top-navigation-by-user-activation"

    // So let's copy-paste Telegram's iframe code to here, and sandbox it.
    // This'll be slightly fragile, in that it'll break if Telegram makes "major"
    // change to their iframe and its url & params.
    val safeUrl = TextAndHtml.safeEncodeForHtmlAttrOnly(unsafeUrl)
    val safeTelegramIframeUrl = s"$safeUrl?embed=1"

    val safeSandboxedIframe =
          s"""<iframe sandbox="$permissions" src="$safeTelegramIframeUrl"></iframe>"""
    // Telegarm's script would add: (I suppose the height is via API?)
    //  width="100%" height="" frameborder="0" scrolling="no"
    //  style="border: none; overflow: hidden; min-width: 320px; height: 96px;">

    // Unfortunately, now Telegram's iframe tends to become a bit too tall. [TELEGRIFR]

    Good(safeSandboxedIframe)
  }

}


