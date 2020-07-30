/// <reference path="../test-types.ts"/>

// SHOULD_CODE_REVIEW this whole file, later.  .

import * as _ from 'lodash';
import assert = require('../utils/ty-assert');
import server = require('../utils/server');
import utils = require('../utils/utils');
import { TyE2eTestBrowser } from '../utils/pages-for';
import settings = require('../utils/settings');
import make = require('../utils/make');
import { dieIf } from '../utils/log-and-die';
import c = require('../test-constants');

let browser: TyE2eTestBrowser;

let everyone;
let owen;
let owensBrowser: TyE2eTestBrowser;
let maria;
let mariasBrowser: TyE2eTestBrowser;

let idAddress: IdAddress;
let forumTitle = "Misc Link Preview Forum";

interface LinkPreviewProvider {
  name: string;
  sandboxedIframe: boolean;  // default true
  lnPvClassSuffix?: string;
  linkInTopic: string;
  linkInTopicExpectedPreviewText: string;
  linkTwoInTopicButBroken: string;
  linkTwoExpectedPrevwText?: string;
  linkTwoNoticesIsBroken?: boolean;  // defaut true
  linkInReply: string;
  linkInReplyExpectedPreviewText?: string;  // later?
  linkInReplyTwo?: string;
  linkInReplyTwoExpectedPreviewText?: string;  // later?
}

type ProvidersMap = { [name: string]: LinkPreviewProvider };

// NOTE:  To troubleshoot just one, add:  --only3rdParty reddit  to the
// wdio command line, for exampe. Or just:  --o3 reddit

let providersToTest: ProvidersMap = {
  /* Doesn't work without trusting FB's js, skip for now.
  facebookPosts: {
    name: "Facebook",
    sandboxedIframe: false,
    lnPvClassSuffix: 'FbPost',
    linkInTopic: 'https://www.facebook.com/impacthublisbon/posts/2729123110678488?__tn__=-R',
    linkInTopicExpectedPreviewText: "events to enable",
    linkTwoInTopicButBroken: 'https://www.facebook.com/no_one/posts/999999999999?__tn__=-R',
    linkTwoExpectedPrevwText: "Facebook post not found",
    // No ?utm
    linkInReply:
        'https://www.facebook.com/65239508296/photos/a.318899128296/10155689214983297/?type=3',
  },
  */

  // facebookVideos:  TESTS_MISSING

  // giphy:  TESTS_MISSING

  instagram: {
    name: "Instagram",
    sandboxedIframe: true,
    // With ?utm query param
    linkInTopic: 'https://www.instagram.com/p/BJlNX-rju7o/?utm_source=ig_web_button_share_sheet',
    linkInTopicExpectedPreviewText: "",
    linkTwoInTopicButBroken: 'https://www.instagram.com/p/NoN-eXiStNg/',
    linkTwoExpectedPrevwText: "Instagram post not found",
    // No ?utm
    linkInReply: 'https://www.instagram.com/p/CBCJGUZDVT_/',
  },

  reddit: {
    name: "Reddit",
    sandboxedIframe: true,
    // With 'utm' query param.
    linkInTopic: 'https://www.reddit.com/r/aww/comments/h84ti6/' +
          'pics_if_squirrels_landing_on_the_ground/?utm_source=share&utm_medium=web2x',
    // The cool squirrels land on one hand. Or do they really?
    linkInTopicExpectedPreviewText: "squirrels landing",
    linkTwoInTopicButBroken:
          'https://www.reddit.com/r/aww/comments/9999999999999999999999/wont_be_found/',
    linkTwoExpectedPrevwText: "not found",
    // No 'utm' query param.
    linkInReply: 'https://www.reddit.com/r/aww/comments/h85mc9/human_here_i_come/',
    // This is not a Reddit topic/orig-post, but a reply/comment.
    linkInReplyTwo: 'https://www.reddit.com/r/aww/comments/h85mc9/human_here_i_come/fupb4cd/',
    // A kitten attempts an apparently not-too-easy long distance jump.
    linkInReplyTwoExpectedPreviewText: "That little squeak",
  },

  telegram: {
    name: "Telegram",
    sandboxedIframe: true,
    linkInTopic: 'https://t.me/durov/68',
    linkInTopicExpectedPreviewText: "",
    linkTwoInTopicButBroken: 'https://t.me/telegram/99999999999999999',
    linkTwoNoticesIsBroken: false,
    linkInReply: 'https://t.me/telegram/83',
  },

  tiktok: {
    name: "TikTok",
    sandboxedIframe: true,
    linkInTopic: 'https://www.tiktok.com/@scout2015/video/6718335390845095173',
    linkInTopicExpectedPreviewText: "",
    linkTwoInTopicButBroken: 'https://www.tiktok.com/@noone/video/99999999999999999999999',
    linkTwoExpectedPrevwText: "No html in TikTok",
    linkInReply: 'https://www.tiktok.com/@sofia.tingeling/video/6830082580818169093',
  },

  youtube: {
    name: "YouTube",
    sandboxedIframe: true,
    // Honey badger doesn't care.
    linkInTopic: 'https://youtu.be/box0-koAuIY',
    linkInTopicExpectedPreviewText: "",
    linkTwoInTopicButBroken: 'https://youtu.be/9898-9898989898',
    linkTwoNoticesIsBroken: false,
    // Not oEmbed, so we cannot easily know the video is broken, skip:
    // linkTwoExpectedPrevwText: "YouTube video not found",
    // "You shall not pass."
    linkInReply: 'https://www.youtube.com/watch?v=S7znI_Kpzbs',
  }
}


if (settings.only3rdParty) {
  const provider = providersToTest[settings.only3rdParty] ||
        _.find(providersToTest, provider => provider.name.startsWith(settings.only3rdParty));
  dieIf(!provider, `No such 3rd party provider: ${provider.name} [TyE402SKD7]`);
  providersToTest = { x: provider };
}


const brokenPreview = '.s_LnPv-Err';

const makePreviewOkSelector = (provider: LinkPreviewProvider) =>
  `.s_LnPv-${provider.lnPvClassSuffix || provider.name}:not(${brokenPreview})`;

const makePreviewBrokenSelector = (provider: LinkPreviewProvider) =>
  `.s_LnPv-${provider.lnPvClassSuffix || provider.name}${brokenPreview}`;


describe("'All other' link previews  TyT550RMHJ25", () => {

  it("initialize people", () => {
    browser = new TyE2eTestBrowser(wdioBrowser);
    everyone = browser;
    owen = make.memberOwenOwner();
    owensBrowser = browser;
    maria = make.memberMaria();
    mariasBrowser = browser;
  });

  it("import a site", () => {
    let site: SiteData = make.forumOwnedByOwen('editor-onebox', { title: forumTitle });
    site.settings.allowGuestLogin = true;
    site.settings.requireVerifiedEmail = false;
    site.members.push(maria);
    idAddress = server.importSiteData(site);
  });

  it("Owen goes to the homepage and logs in", () => {
    owensBrowser.go2(idAddress.origin);
    owensBrowser.complex.loginWithPasswordViaTopbar(owen);
  });

  _.each(providersToTest, addTestsForOneProvider);


  function addTestsForOneProvider(provider: LinkPreviewProvider) {
    const previewOkSelector = makePreviewOkSelector(provider);
    const previewBrokenSelector = makePreviewBrokenSelector(provider);

    it("Owen goes to the topic list page", () => {
      owensBrowser.go2('/');
    });

    it("Owen starts typing a new topic", () => {
      owensBrowser.forumButtons.clickCreateTopic();
      owensBrowser.editor.editTitle(`Testing ${provider.name} link previews`);
    });


    // ----- Link preview, in new Talkyard topic

    it(`... and a  ${provider.name} comment link`, () => {
      owensBrowser.editor.editText(provider.linkInTopic);
    });

    it("The comment link becomes a preview", () => {
      owensBrowser.preview.waitForExist(previewOkSelector, { where: 'InEditor' });
    });

    if (provider.linkInTopicExpectedPreviewText) {
      it(`... with text: "${provider.linkInTopicExpectedPreviewText}"`, () => {
        owensBrowser.preview.waitUntilPreviewTextMatches(
              provider.linkInTopicExpectedPreviewText,
              { where: 'InEditor', inSandboxedIframe: provider.sandboxedIframe });
      });
    }


    // ----- Broken link preview

    it(`Owen types a broken ${provider.name} comment link`, () => {
      owensBrowser.editor.editText('\n\n' +
            provider.linkTwoInTopicButBroken, { append: true });
    });

    if (provider.linkTwoNoticesIsBroken === false) {
      // Currently no easy way to know if e.g. a direct  .jpg  or  .mp4  link is broken.
      // Later: the server will fetch the remote thing, to see if there're <title>
      // tags etc, and then we'll know if is broken or not. [srvr_fetch_ln_pv]
      it("... it's broken but no way for Talkyard to know — noop.", () => {});
    }
    else {
      it("... it becomes sth like a 'Thing not found' message", () => {
        owensBrowser.preview.waitForExist(previewBrokenSelector, { where: 'InEditor' });
      });

      it(`... with text: "${provider.linkTwoExpectedPrevwText}"`, () => {
        owensBrowser.preview.waitUntilPreviewHtmlMatches(
              provider.linkTwoExpectedPrevwText,
              { where: 'InEditor', whichLinkPreviewSelector: '.s_LnPv-Err' });
      });
    }

    it("The ok thing is still there", () => {
      owensBrowser.preview.waitForExist(previewOkSelector, { where: 'InEditor' });
    });


    // ----- Link previews in already existing topic

    it("Owen saves the page", () => {
      owensBrowser.rememberCurrentUrl();
      owensBrowser.editor.save();
      owensBrowser.waitForNewUrl();
    });

    it("The ok link preview appears in the new topic", () => {
      owensBrowser.topic.waitForExistsInPost(c.BodyNr, previewOkSelector);
    });

    if (provider.linkTwoNoticesIsBroken === false) {
      it("... and the broken link preview too — but we don't know it's broken, " +
            "so instead there're two seemingly ok previews", () => {
        owensBrowser.topic.waitForExistsInPost(c.BodyNr, previewOkSelector, { howMany: 2 });
      });
    }
    else {
      it("... and the broken preview too", () => {
        owensBrowser.topic.waitForExistsInPost(c.BodyNr, previewBrokenSelector);
      });
    }

    it("The previews stay, after reload", () => {
      owensBrowser.refresh2();
    });

    it("... the ok preview", () => {
      owensBrowser.topic.waitForExistsInPost(c.BodyNr, previewOkSelector);
    });

    if (provider.linkInTopicExpectedPreviewText) {
      it(`... with the correct text: "${provider.linkInTopicExpectedPreviewText}"`, () => {
        owensBrowser.linkPreview.waitUntilLinkPreviewMatches({
              postNr: c.BodyNr, inSandboxedIframe: provider.sandboxedIframe,
              // or just the 1st one
              whichLinkPreviewSelector: previewOkSelector,
              regex: provider.linkInTopicExpectedPreviewText });
      });
    }


    if (provider.linkTwoNoticesIsBroken === false) {
      it("... and the broken one, but it looks fine to us", () => {
        owensBrowser.topic.waitForExistsInPost(c.BodyNr, previewOkSelector, { howMany: 2 });
      });
    }
    else {
      it("... and the broken one", () => {
        owensBrowser.topic.waitForExistsInPost(c.BodyNr, previewBrokenSelector);
      });

      if (provider.linkTwoExpectedPrevwText) {
        const noPreviewText = `No preview for ${provider.name}`; // what? why  [0LNPV]
        it(`... with text: "${provider.linkTwoExpectedPrevwText} ..."`, () => {
          owensBrowser.topic.waitUntilPostTextMatches(
                c.BodyNr, provider.linkTwoExpectedPrevwText,
                { thingInPostSelector: '.s_LnPv-Err' });
        });
      }
    }


    // ----- Link preview in a Talkyard reply post

    it(`Owen clicks Reply`, () => {
      owensBrowser.topic.clickReplyToOrigPost();
    });

    it("... starts typing a reply", () => {
      owensBrowser.editor.editText(provider.linkInReply);
    });

    it("An link preview appears in an in-page reply preview", () => {
      owensBrowser.preview.waitForExist(previewOkSelector, { where: 'InPage' });
    });

    it("Owen submits the reply", () => {
      owensBrowser.editor.save();
    });

    it("The reply appears, with a link preview", () => {
      owensBrowser.topic.waitForExistsInPost(c.FirstReplyNr, previewOkSelector);
    });


    // ----- A 2nd preview in a reply

    if (provider.linkInReplyTwo) {
      it(`Owen types a 2nd reply`, () => {
        owensBrowser.topic.clickReplyToOrigPost();
        owensBrowser.editor.editText(provider.linkInReplyTwo);
      });

      it("An link preview appears in an in-page reply preview", () => {
        owensBrowser.preview.waitForExist(previewOkSelector, { where: 'InPage' });
      });

      it("Owen submits the reply", () => {
        owensBrowser.editor.save();
      });

      it("The reply appears, with a link preview", () => {
        owensBrowser.topic.waitForExistsInPost(c.FirstReplyNr + 1, previewOkSelector);
      });
    }
  }

});

