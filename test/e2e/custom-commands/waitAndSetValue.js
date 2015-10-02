
exports.command = function(selector, timeout, value) {
  this.waitForElementVisible(selector, timeout)
      .setValue(selector, value);
  return this;
};

// vim: fdm=marker et ts=2 sw=2 tw=0 fo=r list
