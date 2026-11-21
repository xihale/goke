/* MintCal landing — Hero 四视图切换 & 同步最新 ARMv8 发行包信息 */
(function () {
  'use strict';

  var tablist = document.getElementById('heroTabs');
  var screen = document.getElementById('heroScreen');
  if (tablist && screen) {
    var tabs = Array.prototype.slice.call(tablist.querySelectorAll('[role="tab"]'));
    var imgs = Array.prototype.slice.call(screen.querySelectorAll('.screen-img'));
    var thumb = tablist.querySelector('.seg-thumb');

    function activate(index, focusTab) {
      var i = (index + tabs.length) % tabs.length;
      var key = tabs[i].getAttribute('data-tab');

      tabs.forEach(function (btn, idx) {
        var on = idx === i;
        btn.classList.toggle('is-active', on);
        btn.setAttribute('aria-selected', on ? 'true' : 'false');
        btn.tabIndex = on ? 0 : -1;
      });

      imgs.forEach(function (img) {
        img.classList.toggle('is-active', img.getAttribute('data-tab') === key);
      });

      if (thumb) thumb.style.transform = 'translateX(' + (i * 100) + '%)';
      if (focusTab) tabs[i].focus();
    }

    tabs.forEach(function (btn, idx) {
      btn.addEventListener('click', function () { activate(idx, false); });
      btn.addEventListener('keydown', function (e) {
        if (e.key === 'ArrowRight') { e.preventDefault(); activate(idx + 1, true); }
        else if (e.key === 'ArrowLeft') { e.preventDefault(); activate(idx - 1, true); }
        else if (e.key === 'Home') { e.preventDefault(); activate(0, true); }
        else if (e.key === 'End') { e.preventDefault(); activate(tabs.length - 1, true); }
      });
    });

    var initial = tabs.findIndex(function (b) { return b.getAttribute('aria-selected') === 'true'; });
    activate(initial >= 0 ? initial : 0, false);
  }

  function formatMiB(bytes) {
    return (bytes / (1024 * 1024)).toFixed(2) + ' MiB';
  }

  function setText(id, text) {
    var el = document.getElementById(id);
    if (el) el.textContent = text;
  }

  if (typeof fetch === 'function') {
    fetch('./downloads/latest.json', { cache: 'no-cache' })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        if (!data || !data.apk_name) return;
        var latest = data.stable || data;
        var ver = latest.tag_name || ('v' + latest.version);
        var sizeStr = latest.apk_size ? formatMiB(latest.apk_size) : '3.83 MiB';
        var href = './downloads/' + latest.apk_name;

        setText('heroVer', ver);
        setText('heroVerSize', sizeStr);
        setText('heroLeadSize', sizeStr);
        setText('heroBtnSize', sizeStr);
        setText('dlApkName', latest.apk_name);
        setText('dlSize', sizeStr);

        ['heroApkBtn', 'dlApkBtn'].forEach(function (id) {
          var a = document.getElementById(id);
          if (a) a.setAttribute('href', href);
        });

        var shaEl = document.getElementById('dlSha');
        if (shaEl && latest.apk_sha256) {
          shaEl.textContent = 'SHA-256: ' + latest.apk_sha256;
        }
      })
      .catch(function () {});
  }
})();
