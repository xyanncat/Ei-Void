/* Void — small, dependency-free browser behaviour. */
(function () {
  'use strict';

  var root = document.documentElement;
  var THEME_KEY = 'void-theme';

  /* ---------- theme ---------- */

  function readTheme() {
    return root.getAttribute('data-theme') === 'light' ? 'light' : 'dark';
  }

  function storedTheme() {
    try {
      var value = window.localStorage.getItem(THEME_KEY);
      return value === 'light' || value === 'dark' ? value : null;
    } catch (error) {
      return null;
    }
  }

  function applyTheme(theme) {
    root.setAttribute('data-theme', theme);
    var labels = document.querySelectorAll('[data-theme-toggle]');
    for (var i = 0; i < labels.length; i += 1) {
      var next = theme === 'dark' ? 'light' : 'dark';
      labels[i].setAttribute('aria-label', 'Switch to ' + next + ' theme');
      labels[i].setAttribute('title', 'Switch to ' + next + ' theme');
    }
    var metas = document.querySelectorAll('meta[name="theme-color"]');
    for (var j = 0; j < metas.length; j += 1) {
      metas[j].setAttribute('content', theme === 'dark' ? '#050505' : '#f3f1ea');
    }
  }

  var initialTheme = storedTheme();
  if (initialTheme) root.setAttribute('data-theme', initialTheme);
  applyTheme(readTheme());

  document.addEventListener('click', function (event) {
    var button = event.target.closest ? event.target.closest('[data-theme-toggle]') : null;
    if (!button) return;
    event.preventDefault();
    var next = readTheme() === 'dark' ? 'light' : 'dark';
    applyTheme(next);
    try {
      window.localStorage.setItem(THEME_KEY, next);
    } catch (error) {
      /* A blocked store is fine; the theme still applies for this page. */
    }
  });

  /* ---------- header & mobile menu ---------- */

  var header = document.querySelector('[data-site-header]');
  var menuButton = header ? header.querySelector('[data-menu-toggle]') : null;
  var nav = document.getElementById('site-menu');

  function setMenu(open, returnFocus, focusFirst) {
    if (!header || !menuButton) return;
    header.classList.toggle('is-open', open);
    document.body.classList.toggle('nav-open', open);
    menuButton.setAttribute('aria-expanded', open ? 'true' : 'false');
    if (open && focusFirst) {
      var first = nav ? nav.querySelector('a, button') : null;
      if (first) first.focus();
    } else if (!open && returnFocus) {
      menuButton.focus();
    }
  }

  if (menuButton) {
    menuButton.addEventListener('click', function (event) {
      var open = menuButton.getAttribute('aria-expanded') !== 'true';
      // A mouse click should not paint a focus ring on the first link; a
      // keyboard activation (detail === 0) should move focus into the menu.
      setMenu(open, !open, open && event.detail === 0);
    });
  }

  if (nav) {
    nav.addEventListener('click', function (event) {
      var link = event.target.closest ? event.target.closest('a') : null;
      if (link) setMenu(false, false, false);
    });
  }

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && header && header.classList.contains('is-open')) {
      setMenu(false, true, false);
    }
  });

  // While the drawer is open, the scrim above the page should swallow the
  // first tap instead of activating a link underneath it.
  document.addEventListener(
    'click',
    function (event) {
      if (!header || !header.classList.contains('is-open')) return;
      if (header.contains(event.target)) return;
      event.preventDefault();
      event.stopPropagation();
      setMenu(false, false, false);
    },
    true,
  );

  if (window.matchMedia) {
    var wide = window.matchMedia('(min-width: 901px)');
    var onChange = function (event) {
      if (event.matches) setMenu(false, false, false);
    };
    if (wide.addEventListener) wide.addEventListener('change', onChange);
    else if (wide.addListener) wide.addListener(onChange);
  }

  /* ---------- header state on scroll ---------- */

  var ticking = false;

  function updateHeader() {
    ticking = false;
    if (!header) return;
    header.classList.toggle('is-scrolled', window.scrollY > 8);
  }

  if (header) {
    updateHeader();
    window.addEventListener(
      'scroll',
      function () {
        if (ticking) return;
        ticking = true;
        window.requestAnimationFrame(updateHeader);
      },
      { passive: true },
    );
  }

  /* ---------- copy buttons ---------- */

  function copyText(text) {
    if (navigator.clipboard && window.isSecureContext) {
      return navigator.clipboard.writeText(text);
    }
    return new Promise(function (resolve, reject) {
      var area = document.createElement('textarea');
      area.value = text;
      area.setAttribute('readonly', '');
      area.setAttribute('aria-hidden', 'true');
      area.className = 'copy-fallback';
      document.body.appendChild(area);
      area.select();
      try {
        var ok = document.execCommand('copy');
        document.body.removeChild(area);
        if (ok) resolve();
        else reject(new Error('copy failed'));
      } catch (error) {
        document.body.removeChild(area);
        reject(error);
      }
    });
  }

  document.addEventListener('click', function (event) {
    var button = event.target.closest ? event.target.closest('[data-copy]') : null;
    if (!button) return;
    var selector = button.getAttribute('data-copy');
    var target = selector ? document.querySelector(selector) : null;
    if (!target) return;
    event.preventDefault();
    var original = button.textContent;
    copyText(target.textContent || '').then(
      function () {
        button.textContent = 'Copied';
        button.setAttribute('aria-live', 'polite');
        window.setTimeout(function () {
          button.textContent = original;
          button.removeAttribute('aria-live');
        }, 1800);
      },
      function () {
        button.textContent = 'Select and copy';
        window.setTimeout(function () {
          button.textContent = original;
        }, 2200);
      },
    );
  });

  /* ---------- details: keep one answer open at a time ---------- */

  var faqDetails = document.querySelectorAll('.faq-list details');
  for (var f = 0; f < faqDetails.length; f += 1) {
    faqDetails[f].addEventListener('toggle', function (event) {
      var item = event.currentTarget;
      if (!item.open || !item.parentNode) return;
      var open = item.parentNode.querySelectorAll('details[open]');
      for (var s = 0; s < open.length; s += 1) {
        if (open[s] !== item) open[s].open = false;
      }
    });
  }
})();
