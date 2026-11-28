/* Goke landing — Motion 编排、光刃斜切预览、小组件聚焦、BoxSel 交互试玩、截图筛选与释放同步 */
(function () {
  'use strict';

  var reduceMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* 0. Scroll-linked section reveal choreography */
  var revealEls = Array.prototype.slice.call(document.querySelectorAll('[data-reveal]'));
  if ('IntersectionObserver' in window && !reduceMotion) {
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-in');
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.14, rootMargin: '0px 0px -24px 0px' });
    revealEls.forEach(function (el) { io.observe(el); });
  } else {
    revealEls.forEach(function (el) { el.classList.add('is-in'); });
  }

  /* Helper: sliding pill thumb positioner */
  function positionSmallThumb(container, btn) {
    if (!container || !btn) return;
    var thumb = container.querySelector('.seg-thumb--small');
    if (!thumb) return;
    thumb.style.width = btn.offsetWidth + 'px';
    thumb.style.transform = 'translateX(' + (btn.offsetLeft - 4) + 'px)';
  }

  /* 1. Hero 5-tab switcher and 18-deg diagonal blade dark wipe */
  var tablist = document.getElementById('heroTabs');
  var screen = document.getElementById('heroScreen');
  if (tablist && screen) {
    var tabs = Array.prototype.slice.call(tablist.querySelectorAll('[role="tab"]'));
    var imgs = Array.prototype.slice.call(screen.querySelectorAll('.screen-img'));
    var hotspots = Array.prototype.slice.call(screen.querySelectorAll('.phone-nav-hotspot button'));
    var thumb = tablist.querySelector('.seg-thumb');
    var currentIdx = -1;
    var slideTimer = null;

    function clearSlideClasses() {
      imgs.forEach(function (img) {
        img.classList.remove('is-animating', 'is-leaving');
        img.style.transform = '';
      });
    }

    function activateTab(index, focusTab) {
      var i = (index + tabs.length) % tabs.length;
      var prevIdx = currentIdx;
      var key = tabs[i].getAttribute('data-tab');

      tabs.forEach(function (btn, idx) {
        var on = idx === i;
        btn.classList.toggle('is-active', on);
        btn.setAttribute('aria-selected', on ? 'true' : 'false');
        btn.tabIndex = on ? 0 : -1;
      });

      if (slideTimer) {
        clearTimeout(slideTimer);
        slideTimer = null;
      }

      if (prevIdx >= 0 && prevIdx !== i && !reduceMotion) {
        var dir = i > prevIdx ? 1 : -1;
        var oldKey = tabs[prevIdx].getAttribute('data-tab');
        clearSlideClasses();
        imgs.forEach(function (img) {
          var tab = img.getAttribute('data-tab');
          if (tab === oldKey) {
            img.classList.remove('is-active');
            img.classList.add('is-leaving');
          } else if (tab === key) {
            img.classList.add('is-active');
            img.style.transform = 'translateX(' + (dir * 100) + '%)';
          } else {
            img.classList.remove('is-active');
          }
        });
        void screen.offsetWidth;
        imgs.forEach(function (img) {
          var tab = img.getAttribute('data-tab');
          if (tab === oldKey) {
            img.classList.add('is-animating');
            img.style.transform = 'translateX(' + (-dir * 26) + '%)';
          } else if (tab === key) {
            img.classList.add('is-animating');
            img.style.transform = 'translateX(0%)';
          }
        });
        slideTimer = setTimeout(function () {
          clearSlideClasses();
          slideTimer = null;
        }, 380);
      } else {
        clearSlideClasses();
        imgs.forEach(function (img) {
          img.classList.toggle('is-active', img.getAttribute('data-tab') === key);
        });
      }

      currentIdx = i;
      if (thumb) thumb.style.transform = 'translateX(' + (i * 100) + '%)';
      if (focusTab) tabs[i].focus();
    }

    tabs.forEach(function (btn, idx) {
      btn.addEventListener('click', function () { activateTab(idx, false); });
      btn.addEventListener('keydown', function (e) {
        if (e.key === 'ArrowRight') { e.preventDefault(); activateTab(idx + 1, true); }
        else if (e.key === 'ArrowLeft') { e.preventDefault(); activateTab(idx - 1, true); }
        else if (e.key === 'Home') { e.preventDefault(); activateTab(0, true); }
        else if (e.key === 'End') { e.preventDefault(); activateTab(tabs.length - 1, true); }
      });
    });

    var initial = tabs.findIndex(function (b) { return b.getAttribute('aria-selected') === 'true'; });
    activateTab(initial >= 0 ? initial : 0, false);

    hotspots.forEach(function (btn, idx) {
      btn.addEventListener('click', function () {
        var key = btn.getAttribute('data-tab');
        var targetIdx = tabs.findIndex(function (t) { return t.getAttribute('data-tab') === key; });
        activateTab(targetIdx >= 0 ? targetIdx : idx, false);
      });
    });

    var heroThemeBtn = document.getElementById('heroThemeToggle');
    var heroThemeLabel = document.getElementById('heroThemeLabel');
    var bladeTimer = null;
    if (heroThemeBtn) {
      heroThemeBtn.addEventListener('click', function () {
        var isDark = heroThemeBtn.getAttribute('aria-pressed') === 'true';
        var nextDark = !isDark;
        heroThemeBtn.setAttribute('aria-pressed', nextDark ? 'true' : 'false');
        if (heroThemeLabel) heroThemeLabel.textContent = nextDark ? '深色预览' : '浅色预览';
        if (bladeTimer) clearTimeout(bladeTimer);
        screen.classList.add('is-blading');
        screen.classList.toggle('is-dark', nextDark);
        bladeTimer = setTimeout(function () {
          screen.classList.remove('is-blading');
        }, 640);
      });
    }
  }

  /* 2. 5 Native Desktop Widgets Matrix spotlight interaction */
  var widgetMatrix = document.getElementById('widgetMatrix');
  if (widgetMatrix) {
    var wCards = Array.prototype.slice.call(widgetMatrix.querySelectorAll('.w-card'));
    function focusWidget(target) {
      wCards.forEach(function (c) { c.classList.toggle('is-focus', c === target); });
    }
    wCards.forEach(function (card) {
      card.addEventListener('mouseenter', function () { focusWidget(card); });
      card.addEventListener('focus', function () { focusWidget(card); });
      card.addEventListener('click', function () { focusWidget(card); });
    });
    widgetMatrix.addEventListener('mouseleave', function () { focusWidget(null); });
  }

  /* 3. Interactive 1:1 BoxSel Playground (auto-demo + real drag over Thu P6-P7) */
  var pgLoupe = document.getElementById('pgLoupe');
  var pgRipple = document.getElementById('pgRipple');
  var pgBoxSel = document.getElementById('pgBoxSel');
  var pgPill = document.getElementById('pgPill');
  var pgReplay = document.getElementById('pgReplayBtn');
  var pgStatus = document.getElementById('pgStatus');

  if (pgLoupe && pgBoxSel && pgPill) {
    var pgTimer = null;
    function resetBoxSel() {
      if (pgTimer) clearTimeout(pgTimer);
      pgBoxSel.classList.remove('is-active', 'is-manual');
      pgBoxSel.style.height = '0%';
      pgPill.classList.remove('is-shown');
      if (pgRipple) pgRipple.classList.remove('is-pop');
    }

    function playBoxSelDemo() {
      resetBoxSel();
      if (pgStatus) pgStatus.textContent = '长按周四第 6 节空白…';
      if (pgRipple) {
        void pgRipple.offsetWidth;
        pgRipple.classList.add('is-pop');
      }
      pgTimer = setTimeout(function () {
        pgBoxSel.classList.add('is-active');
        pgBoxSel.style.height = '11.7%';
        if (pgStatus) pgStatus.textContent = '向下拖拽选定 6–7 节…';
        pgTimer = setTimeout(function () {
          pgPill.classList.add('is-shown');
          if (pgStatus) pgStatus.textContent = '已生成：教3-101 · 14:40–16:15';
        }, 420);
      }, 280);
    }

    if (pgReplay) {
      pgReplay.addEventListener('click', playBoxSelDemo);
    }

    /* Interactive pointer drag inside #pgLoupe */
    var dragging = false;
    var startY = 0;
    pgLoupe.addEventListener('pointerdown', function (e) {
      dragging = true;
      startY = e.clientY;
      pgLoupe.setPointerCapture(e.pointerId);
      resetBoxSel();
      pgBoxSel.classList.add('is-active', 'is-manual');
      pgBoxSel.style.height = '5.8%';
      if (pgRipple) {
        void pgRipple.offsetWidth;
        pgRipple.classList.add('is-pop');
      }
      if (pgStatus) pgStatus.textContent = '拖拽中…松手创建日程';
    });
    pgLoupe.addEventListener('pointermove', function (e) {
      if (!dragging) return;
      var dy = Math.max(0, e.clientY - startY);
      var h = Math.min(18.0, 5.8 + (dy / 160) * 12.0);
      pgBoxSel.style.height = h.toFixed(2) + '%';
    });
    function finishDrag() {
      if (!dragging) return;
      dragging = false;
      pgBoxSel.classList.remove('is-manual');
      var h = parseFloat(pgBoxSel.style.height || '5.8');
      var twoSlots = h >= 8.5;
      pgBoxSel.style.height = twoSlots ? '11.7%' : '5.9%';
      pgPill.textContent = twoSlots
        ? '+ 教3-101 · 周四 6–7 节 · 14:40–16:15'
        : '+ 教3-101 · 周四 第 6 节 · 14:40–15:25';
      pgPill.classList.add('is-shown');
      if (pgStatus) pgStatus.textContent = '松手确认：已填入时间与教室';
    }
    pgLoupe.addEventListener('pointerup', finishDrag);
    pgLoupe.addEventListener('pointercancel', finishDrag);

    if ('IntersectionObserver' in window && !reduceMotion) {
      var pgPlayed = false;
      var pgIo = new IntersectionObserver(function (entries) {
        if (entries[0] && entries[0].isIntersecting && !pgPlayed) {
          pgPlayed = true;
          setTimeout(playBoxSelDemo, 320);
          pgIo.disconnect();
        }
      }, { threshold: 0.4 });
      pgIo.observe(pgLoupe);
    } else {
      pgBoxSel.classList.add('is-active');
      pgBoxSel.style.height = '11.7%';
      pgPill.classList.add('is-shown');
    }
  }

  /* 4. Screens filter, sliding thumbs, light/dark toggle & lightbox */
  var shotGrid = document.getElementById('shotGrid');
  var filterWrap = document.getElementById('screenFilters');
  var modeWrap = document.getElementById('galleryModeSwitch');
  if (shotGrid) {
    var cards = Array.prototype.slice.call(shotGrid.querySelectorAll('.shot-card'));

    function applyFilter(cat) {
      cards.forEach(function (card) {
        var match = cat === 'all' || card.getAttribute('data-cat') === cat;
        card.classList.toggle('is-hidden', !match);
        if (match) card.classList.add('is-in');
      });
    }

    if (filterWrap) {
      var filterBtns = Array.prototype.slice.call(filterWrap.querySelectorAll('[data-cat]'));
      filterBtns.forEach(function (btn) {
        btn.addEventListener('click', function () {
          var cat = btn.getAttribute('data-cat') || 'all';
          filterBtns.forEach(function (b) {
            var on = b === btn;
            b.classList.toggle('is-active', on);
            b.setAttribute('aria-selected', on ? 'true' : 'false');
          });
          positionSmallThumb(filterWrap, btn);
          applyFilter(cat);
        });
      });
      var activeFilter = filterWrap.querySelector('.is-active') || filterBtns[0];
      positionSmallThumb(filterWrap, activeFilter);
    }

    if (modeWrap) {
      var modeBtns = Array.prototype.slice.call(modeWrap.querySelectorAll('[data-mode]'));
      modeBtns.forEach(function (btn) {
        btn.addEventListener('click', function () {
          var mode = btn.getAttribute('data-mode') || 'light';
          modeBtns.forEach(function (b) {
            var on = b === btn;
            b.classList.toggle('is-active', on);
            b.setAttribute('aria-pressed', on ? 'true' : 'false');
          });
          positionSmallThumb(modeWrap, btn);
          cards.forEach(function (card) {
            var img = card.querySelector('img');
            if (!img) return;
            var nextSrc = mode === 'dark' ? (img.getAttribute('data-dark') || img.getAttribute('data-light')) : img.getAttribute('data-light');
            if (nextSrc) img.setAttribute('src', nextSrc);
          });
        });
      });
      var activeMode = modeWrap.querySelector('.is-active') || modeBtns[0];
      positionSmallThumb(modeWrap, activeMode);
    }

    function syncGalleryThumbs() {
      if (filterWrap) positionSmallThumb(filterWrap, filterWrap.querySelector('.is-active'));
      if (modeWrap) positionSmallThumb(modeWrap, modeWrap.querySelector('.is-active'));
    }
    window.addEventListener('resize', syncGalleryThumbs);
    window.addEventListener('load', syncGalleryThumbs);

    /* Lightbox */
    var lightbox = document.getElementById('shotLightbox');
    var lbImg = document.getElementById('lightboxImg');
    var lbTitle = document.getElementById('lightboxTitle');
    var lbDesc = document.getElementById('lightboxDesc');
    var lbClose = document.getElementById('lightboxClose');
    var lbPrev = document.getElementById('lightboxPrev');
    var lbNext = document.getElementById('lightboxNext');
    var activeCard = null;

    function getVisibleCards() {
      return cards.filter(function (c) { return !c.classList.contains('is-hidden'); });
    }

    function showCardInLightbox(card) {
      if (!lightbox || !card) return;
      activeCard = card;
      var img = card.querySelector('img');
      if (lbImg && img) {
        lbImg.setAttribute('src', img.getAttribute('src') || '');
        lbImg.setAttribute('alt', img.getAttribute('alt') || '');
      }
      if (lbTitle) lbTitle.textContent = card.getAttribute('data-title') || '';
      if (lbDesc) lbDesc.textContent = card.getAttribute('data-desc') || '';
      if (!lightbox.open && typeof lightbox.showModal === 'function') {
        lightbox.showModal();
      }
    }

    function stepLightbox(delta) {
      var list = getVisibleCards();
      if (!list.length) return;
      var idx = list.indexOf(activeCard);
      var nextIdx = (idx + delta + list.length) % list.length;
      showCardInLightbox(list[nextIdx]);
    }

    cards.forEach(function (card) {
      var btn = card.querySelector('.shot-btn');
      if (btn) btn.addEventListener('click', function () { showCardInLightbox(card); });
    });

    if (lbClose && lightbox) {
      lbClose.addEventListener('click', function () { lightbox.close(); });
      lightbox.addEventListener('click', function (e) {
        if (e.target === lightbox) lightbox.close();
      });
      lightbox.addEventListener('close', function () {
        if (activeCard) {
          var trigger = activeCard.querySelector('.shot-btn');
          if (trigger) trigger.focus();
        }
      });
    }
    if (lbPrev) lbPrev.addEventListener('click', function () { stepLightbox(-1); });
    if (lbNext) lbNext.addEventListener('click', function () { stepLightbox(1); });
    document.addEventListener('keydown', function (e) {
      if (!lightbox || !lightbox.open) return;
      if (e.key === 'ArrowLeft') { e.preventDefault(); stepLightbox(-1); }
      else if (e.key === 'ArrowRight') { e.preventDefault(); stepLightbox(1); }
    });
  }

  /* 5. Hydrate latest Android (v*) & iOS (ios-v*) release info */
  function formatMiB(bytes) {
    return (bytes / (1024 * 1024)).toFixed(2) + ' MiB';
  }
  function setText(id, text) {
    var el = document.getElementById(id);
    if (el) el.textContent = text;
  }

  var androidHydratedFromMirror = false;
  var iosHydratedFromGitHub = false;

  /* Copy SideStore / AltStore source URL button */
  var copySourceBtn = document.getElementById('dlCopySourceBtn');
  if (copySourceBtn) {
    var copyResetTimer = null;
    var defaultCopyLabel = copySourceBtn.textContent;

    function markCopied() {
      if (copyResetTimer) clearTimeout(copyResetTimer);
      copySourceBtn.textContent = '已复制源地址';
      copySourceBtn.classList.add('is-copied');
      copyResetTimer = setTimeout(function () {
        copySourceBtn.textContent = defaultCopyLabel;
        copySourceBtn.classList.remove('is-copied');
        copyResetTimer = null;
      }, 2000);
    }

    function fallbackCopyText(text) {
      var ta = document.createElement('textarea');
      ta.value = text;
      ta.setAttribute('readonly', '');
      ta.className = 'sr-copy-buf';
      document.body.appendChild(ta);
      ta.select();
      var ok = false;
      try { ok = document.execCommand('copy'); } catch (_) { ok = false; }
      document.body.removeChild(ta);
      return ok;
    }

    copySourceBtn.addEventListener('click', function () {
      var sourceUrl = copySourceBtn.getAttribute('data-source-url') || '';
      if (!sourceUrl) return;
      if (navigator.clipboard && typeof navigator.clipboard.writeText === 'function') {
        navigator.clipboard.writeText(sourceUrl).then(markCopied).catch(function () {
          if (fallbackCopyText(sourceUrl)) markCopied();
        });
      } else if (fallbackCopyText(sourceUrl)) {
        markCopied();
      }
    });
  }

  function applyAndroidRelease(info) {
    if (!info || !info.apkName || !info.href) return;
    var ver = info.tag || 'latest';
    var sizeStr = info.size ? formatMiB(info.size) : '2.88 MiB';

    setText('heroVer', ver);
    setText('heroVerSize', sizeStr);
    setText('heroLeadSize', sizeStr);
    setText('heroBtnSize', sizeStr);
    setText('dlApkName', info.apkName);
    setText('dlSize', sizeStr);

    ['heroApkBtn', 'dlApkBtn'].forEach(function (id) {
      var a = document.getElementById(id);
      if (a) a.setAttribute('href', info.href);
    });

    var shaEl = document.getElementById('dlSha');
    if (shaEl) {
      if (info.sha256) {
        shaEl.textContent = 'SHA-256: ' + info.sha256;
      } else if (info.source) {
        shaEl.textContent = info.source;
      }
    }
  }

  function applyIosRelease(info) {
    if (!info || !info.ipaUrl) return;
    var ver = info.tag || '';
    var sizeStr = info.size ? formatMiB(info.size) : '';

    if (ver) {
      setText('heroIosVer', ver);
      setText('dlIosVer', ver);
    }
    if (info.ipaName) {
      setText('dlIosName', info.ipaName);
    }
    setText('dlIosNote', '推荐先「添加到 SideStore 源」，一次订阅即可在 SideStore 内一键更新；也可直接下载未签名 IPA。');

    var sizeEl = document.getElementById('dlIosSize');
    if (sizeEl && sizeStr) {
      sizeEl.textContent = sizeStr;
      sizeEl.classList.remove('is-hidden');
    }

    var iosBtn = document.getElementById('dlIosBtn');
    if (iosBtn) {
      iosBtn.setAttribute('href', info.ipaUrl);
      iosBtn.textContent = '下载 IPA';
    }
  }

  function pickApkAsset(assets) {
    if (!Array.isArray(assets)) return null;
    var apks = assets.filter(function (a) {
      return a && typeof a.name === 'string' && /\.apk$/i.test(a.name) && a.browser_download_url;
    });
    if (!apks.length) return null;
    for (var i = 0; i < apks.length; i++) {
      if (/arm64-v8a/i.test(apks[i].name)) return apks[i];
    }
    return apks[0];
  }

  function pickIpaAsset(assets) {
    if (!Array.isArray(assets)) return null;
    for (var i = 0; i < assets.length; i++) {
      var a = assets[i];
      if (a && typeof a.name === 'string' && /\.ipa$/i.test(a.name) && a.browser_download_url) {
        return a;
      }
    }
    return null;
  }

  if (typeof fetch === 'function') {
    fetch('./downloads/latest.json', { cache: 'no-cache' })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        if (!data || !data.apk_name) return;
        var latest = data.stable || data;
        androidHydratedFromMirror = true;
        applyAndroidRelease({
          tag: latest.tag_name || ('v' + latest.version),
          apkName: latest.apk_name,
          size: latest.apk_size,
          href: './downloads/' + latest.apk_name,
          sha256: latest.apk_sha256
        });
      })
      .catch(function () {});

    fetch('./altstore.json', { cache: 'no-cache' })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (data) {
        if (iosHydratedFromGitHub || !data || !Array.isArray(data.apps) || !data.apps.length) return;
        var versions = data.apps[0] && data.apps[0].versions;
        if (!Array.isArray(versions) || !versions.length) return;
        var latest = versions[0];
        if (!latest || !latest.downloadURL) return;
        var urlParts = latest.downloadURL.split('/');
        var ipaName = urlParts[urlParts.length - 1] || '';
        var tagMatch = latest.downloadURL.match(/\/releases\/download\/([^/]+)\//);
        applyIosRelease({
          tag: tagMatch ? decodeURIComponent(tagMatch[1]) : ('v' + latest.version),
          ipaName: ipaName,
          size: latest.size,
          ipaUrl: latest.downloadURL
        });
      })
      .catch(function () {});

    fetch('https://api.github.com/repos/xihale/goke/releases?per_page=20', {
      headers: { Accept: 'application/vnd.github+json' }
    })
      .then(function (res) { return res.ok ? res.json() : null; })
      .then(function (releases) {
        if (!Array.isArray(releases)) return;
        var androidFound = false;
        var iosFound = false;

        for (var i = 0; i < releases.length; i++) {
          var rel = releases[i];
          if (!rel || rel.draft || typeof rel.tag_name !== 'string') continue;

          if (!androidFound && /^v/.test(rel.tag_name)) {
            var apk = pickApkAsset(rel.assets);
            if (apk) {
              androidFound = true;
              if (!androidHydratedFromMirror) {
                applyAndroidRelease({
                  tag: rel.tag_name,
                  apkName: apk.name,
                  size: apk.size,
                  href: apk.browser_download_url,
                  source: 'GitHub Releases · ' + rel.tag_name
                });
              }
            }
          }

          if (!iosFound && /^ios-v/.test(rel.tag_name)) {
            var ipa = pickIpaAsset(rel.assets);
            if (ipa) {
              iosFound = true;
              iosHydratedFromGitHub = true;
              applyIosRelease({
                tag: rel.tag_name,
                ipaName: ipa.name,
                size: ipa.size,
                ipaUrl: ipa.browser_download_url
              });
            }
          }

          if (androidFound && iosFound) break;
        }
      })
      .catch(function () {});
  }
})();
