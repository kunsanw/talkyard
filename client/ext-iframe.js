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


  /** Talkyard injects this Javascript into external srcdoc=... iframes,
    * to resize the iframe properly, and adjust margins.
    *
    * The embedding parent window doesn't know how tall this iframe with oEmbed
    * stuff inside wants to be — so we postMessage() and tell it.
    *
    * We set body.margin = 0, otherwise e.g. Chrome has an 8px default margin.
    * We set the top-bottom-margin of elems directly in <body> to '0 auto'
    * to avoid scrollbars. E.g. Twitter otherwise include 10px top & bottom margin.
    * 'auto' is to place in the middle.
    *
    * We don't really know when the oEmbed contents is done loading.
    * So, we send a few messages — and if after a while, the oEmbed still
    * doesn't have its final size, then, that's a weird oEmbed and someone
    * else's problem (don't try to fix that).
    */

  // TESTS_MISSING  TyT037MKAH24  // create a LinkPrevwRendrEng that creates a 432 px
  // tall div, with 20 px body margin, 20 px child div padding & margin, should
  // become 432 px tall? (margin & padding removed)

(function(doc) { // Talkyard [OEMBHGHT]
  function removePaddingAndMargin() {
    try {
      doc.body.style.margin = '0';
      var chidren = doc.querySelectorAll('body > *');
      for (var i = 0; i < chidren.length; ++i) {
        var c = chidren[i];
        c.style.margin = '0 auto';
        c.style.padding = '0';
      }
    }
    catch (ex) {
      console.warn("Error removing padding or margin [TyEOEMBMARG]", ex);
    }
  }

  var numSent = 0;

  function sendHeightToParent() {
    var height = 0;
    // There's no body if an embedded-thing-<script> broke, no content appeared.
    if (doc.body) {
      removePaddingAndMargin();
      height = doc.body.clientHeight;
    }
    // Need to post to '*', because the domain of this srcdoc=... iframe is "null",
    // i.e. different from the parent frame domain.
    console.debug("Sending oEmbHeight: " + height + " [TyMOEMBHGHT]");
    window.parent.postMessage(['oEmbHeight', height], '*');
    numSent += 1;
    if (numSent < 4) {
      setTimeout(sendHeightToParent, numSent * 500);
    }
  }

  // This sends a message after 500, 1000, 2000, 3500 ms, right.
  setTimeout(sendHeightToParent, 500);
})(document);
