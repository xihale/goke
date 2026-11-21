/* MintCal landing — 极简交互：Hero 四视图真机切换 */
(function () {
  'use strict';

  var CAPTIONS = {
    today: '下一节课、大号取件码与今日时间线，一屏看清。',
    timetable: '广工四校区节次预设，多机房自动折叠为「实4\u2011401 等 8 处」。',
    calendar: '月视图与周视图随手切换，色点标出课程与待办。',
    todo: '自然语言解析时间与优先级，考试与快递自动归档。'
  };

  var tablist = document.getElementById('heroTabs');
  var screen = document.getElementById('heroScreen');
  var caption = document.getElementById('heroCaption');
  if (!tablist || !screen) return;

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
    if (caption && CAPTIONS[key]) caption.textContent = CAPTIONS[key];
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
})();
