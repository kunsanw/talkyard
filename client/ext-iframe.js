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


  /** The embedding parent window doesn't know how tall this iframe with oEmbed
    * stuff inside wants to be — so let's tell it.
    *
    * We set body.margin = 0, otherwise e.g. Chrome has 8px default margin.
    *
    * We set the top-bottom-margin of elems directly in < body > to '0 auto'
    * to avoid scrollbars. E.g. Twitter otherwise include 10px top & bottom margin.
    * ('auto' to place in the middle.)
    *
    * Need to postMessage(...) to '*', because the domain of this srcdoc=...
    * iframe is "null", i.e. different from the parent frame domain.
    *
    * We don't really know when the oEmbed contents is done loading.
    * So, we send a few messages — and if, after that, if the oEmbed still
    * doesn't have its final size, then, that's a weird oEmbed and someone
    * else's problem, we shouldn't try to fix that.
    */
  // TESTS_MISSING  // create a LinkPrevwRendrEng that creates a 432 px tall div,
  // with 20 px body margin, 20 px child div padding & margin,
  // should become 432 px tall? (margin & padding removed)

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
      console.warn("Error rm padd marg [TyEOEMBMARG]", ex);
    }
  }

  var numSent = 0;
  function sendHeight() {
    var height = 0;
    // There's no body if an embedded-thing-<script> broke, didn't show anything.
    if (doc.body) {
      removePaddingAndMargin();
      height = doc.body.clientHeight;
    }
    console.debug("Sending oEmbHeight: " + height + " [TyMOEMBHGHT]");
    window.parent.postMessage(['oEmbHeight', height], '*');
    numSent += 1;
    if (numSent < 4) {
      setTimeout(sendHeight, numSent * 500);
    }
  }
  setTimeout(sendHeight, 500);
})(document);
