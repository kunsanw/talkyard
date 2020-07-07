/// <reference path="../test-types.ts"/>

// SHOULD_CODE_REVIEW this whole file, later.

import * as _ from 'lodash';
import assert = require('../utils/ty-assert');
// import fs = require('fs');  EMBCMTS
import server = require('../utils/server');
import utils = require('../utils/utils');
import { buildSite } from '../utils/site-builder';
import { TyE2eTestBrowser, TyAllE2eTestBrowsers } from '../utils/pages-for';
import settings = require('../utils/settings');
import lad = require('../utils/log-and-die');
import c = require('../test-constants');


let everyonesBrowsers: TyAllE2eTestBrowsers;
let richBrowserA: TyE2eTestBrowser;
let richBrowserB: TyE2eTestBrowser;
let owen: Member;
let owensBrowser: TyE2eTestBrowser;
let memah: Member;
let memahsBrowser: TyE2eTestBrowser;
let maria: Member;
let mariasBrowser: TyE2eTestBrowser;
let strangersBrowser: TyE2eTestBrowser;

let site: IdAddress;

let forum: TwoPagesTestForum;  // or: LargeTestForum

let discussionPageUrl: string;

const apiSecret: TestApiSecret = {
  nr: 1,
  userId: c.SysbotUserId,
  createdAt: c.MinUnixMillis,
  deletedAt: undefined,
  isDeleted: false,
  secretKey: 'publicE2eTestSecretKeyAbc123',
};



describe("some-e2e-test  TyT1234ABC", () => {

  it("import a site", () => {
    const builder = buildSite();
    forum = builder.addTwoPagesForum({  // or: builder.addLargeForum
      title: "Some E2E Test",
      members: ['maria', 'memah', 'michael'],
    });

    const newPage: PageJustAdded = builder.addPage({
      id: 'extraPageId',
      folder: '/',
      showId: false,
      slug: 'extra-page',
      role: c.TestPageRole.Discussion,
      title: "In the middle",
      body: "In the middle of difficulty lies opportunity",
      categoryId: forum.categories.categoryA.id,
      authorId: forum.members.maria.id,
    });

    builder.addPost({
      page: newPage,  // or e.g.: forum.topics.byMichaelCategoryA,
      nr: c.FirstReplyNr,
      parentNr: c.BodyNr,
      authorId: forum.members.maria.id,
      approvedSource: "The secret of getting ahead is getting started",
    });

    // Disable notifications, or notf email counts will be off (since Owen would get emails).
    builder.settings({ numFirstPostsToReview: 0, numFirstPostsToApprove: 0 });
    builder.getSite().pageNotfPrefs = [{
      memberId: forum.members.owen.id,
      notfLevel: c.TestPageNotfLevel.Muted,
      wholeSite: true,
    }];

    // Enable API.
    builder.settings({ enableApi: true });
    builder.getSite().apiSecrets = [apiSecret];

    // Add an ext id to a category.
    // forum.categories.specificCategory.extId = 'specific cat ext id';

    assert.refEq(builder.getSite(), forum.siteData);
    site = server.importSiteData(forum.siteData);
    server.skipRateLimits(site.id);
    discussionPageUrl = site.origin + '/' + forum.topics.byMichaelCategoryA.slug;
  });

  it("initialize people", () => {
    everyonesBrowsers = new TyE2eTestBrowser(allWdioBrowsers);
    richBrowserA = new TyE2eTestBrowser(wdioBrowserA);
    richBrowserB = new TyE2eTestBrowser(wdioBrowserB);

    owen = forum.members.owen;
    owensBrowser = richBrowserA;
    memah = forum.members.memah;
    memahsBrowser = richBrowserA;

    maria = forum.members.maria;
    mariasBrowser = richBrowserB;
    strangersBrowser = richBrowserB;
  });

  /*
  it("Owen logs in to admin area, ... ", () => {
    owensBrowser.adminArea.goToUsersEnabled(siteIdAddress.origin);
    owensBrowser.loginDialog.loginWithPassword(owen);
  }); */

  it("Memah logs in", () => {
    memahsBrowser.go2(site.origin);
    memahsBrowser.complex.loginWithPasswordViaTopbar(memah);
  });

  const TopicATitle = 'TopicATitle';
  const TopicABodyOrig = () =>
          `TopicABodyOrig ${site.origin}/${forum.topics.byMichaelCategoryA.slug}`;


  let topicAUrl: string;

  it("Memah posts a topic that links to Michael's page", () => {
    memahsBrowser.complex.createAndSaveTopic({
            title: TopicATitle, body: TopicABodyOrig() });
    topicAUrl = memahsBrowser.getUrl();
  });

  it("Maria goes to the new topic", () => {
    mariasBrowser.go2(topicAUrl);
  });

  it("... follows the link to Michael's page", () => {
    mariasBrowser.waitAndClick('#post-1 a');
  });

});

