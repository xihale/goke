/* MintCal landing — 五栏预览、视频章节、界面筛选与深浅切换、最新发行包同步 */
(function () {
  'use strict';

  /* 1. Hero 5-tab switcher & light/dark preview */
  var tablist = document.getElementById('heroTabs');
  var screen = document.getElementById('heroScreen');
  if (tablist && screen) {
    var tabs = Array.prototype.slice.call(tablist.querySelectorAll('[role="tab"]'));
    var imgs = Array.prototype.slice.call(screen.querySelectorAll('.screen-img'));
    var thumb = tablist.querySelector('.seg-thumb');

    function activateTab(index, focusTab) {
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

    var heroThemeBtn = document.getElementById('heroThemeToggle');
    var heroThemeLabel = document.getElementById('heroThemeLabel');
    if (heroThemeBtn) {
      heroThemeBtn.addEventListener('click', function () {
        var isDark = heroThemeBtn.getAttribute('aria-pressed') === 'true';
        var nextDark = !isDark;
        heroThemeBtn.setAttribute('aria-pressed', nextDark ? 'true' : 'false');
        if (heroThemeLabel) heroThemeLabel.textContent = nextDark ? '深色预览' : '浅色预览';
        imgs.forEach(function (img) {
          var nextSrc = img.getAttribute(nextDark ? 'data-dark' : 'data-light');
          if (nextSrc) img.setAttribute('src', nextSrc);
        });
      });
    }
  }

  /* 2. Demo video chapter jump pills */
  var video = document.getElementById('demoVideo');
  var chaptersWrap = document.getElementById('demoChapters');
  if (video && chaptersWrap) {
    var chapterBtns = Array.prototype.slice.call(chaptersWrap.querySelectorAll('.chapter-pill'));

    function setActiveChapter(targetBtn) {
      chapterBtns.forEach(function (btn) {
        btn.classList.toggle('is-active', btn === targetBtn);
      });
    }

    chapterBtns.forEach(function (btn) {
      btn.addEventListener('click', function () {
        var sec = parseFloat(btn.getAttribute('data-time') || '0');
        setActiveChapter(btn);
        try {
          video.currentTime = sec;
          var p = video.play();
          if (p && typeof p.catch === 'function') p.catch(function () {});
        } catch (err) {}
      });
    });

    video.addEventListener('timeupdate', function () {
      var t = video.currentTime;
      var active = chapterBtns[0];
      for (var i = 0; i < chapterBtns.length; i++) {
        var start = parseFloat(chapterBtns[i].getAttribute('data-time') || '0');
        if (t >= start - 0.3) active = chapterBtns[i];
      }
      if (active) setActiveChapter(active);
    });
  }

  /* 3. Screens filter, light/dark toggle & lightbox */
  var shotGrid = document.getElementById('shotGrid');
  var filterWrap = document.getElementById('screenFilters');
  var modeWrap = document.getElementById('galleryModeSwitch');
  if (shotGrid) {
    var cards = Array.prototype.slice.call(shotGrid.querySelectorAll('.shot-card'));
    var currentCat = 'all';

    function applyFilter(cat) {
      currentCat = cat;
      cards.forEach(function (card) {
        var match = cat === 'all' || card.getAttribute('data-cat') === cat;
        card.classList.toggle('is-hidden', !match);
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
          applyFilter(cat);
        });
      });
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
          cards.forEach(function (card) {
            var img = card.querySelector('img');
            if (!img) return;
            var nextSrc = mode === 'dark' ? (img.getAttribute('data-dark') || img.getAttribute('data-light')) : img.getAttribute('data-light');
            if (nextSrc) img.setAttribute('src', nextSrc);
          });
        });
      });
    }

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
      if (btn) {
        btn.addEventListener('click', function () { showCardInLightbox(card); });
      }
    });

    if (lbClose && lightbox) {
      lbClose.addEventListener('click', function () { lightbox.close(); });
      lightbox.addEventListener('click', function (e) {
        if (e.target === lightbox) lightbox.close();
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

  /* 4. Hydrate latest release info from latest.json */
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
