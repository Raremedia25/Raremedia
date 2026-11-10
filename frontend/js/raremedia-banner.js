/* Raremedia banner — "Always offering productive digital tools that transform hard work into simple."
 *
 * Drop-in for plain HTML pages and prebuilt bundles:
 *   <script defer src="/js/raremedia-banner.js" data-target=".main-area"></script>
 *
 * data-target    CSS selector of the element that receives the banner as its first child (default: body).
 *                If that element only appears later (shells built by script, single-page apps), the banner
 *                waits for it and follows it when the app re-renders.
 * data-fallback  Selector of a holder used while the target does not exist (for example "body", so the
 *                sign-in page of a single-page app also shows the banner).
 * data-mode      "fixed" pins the banner to the top of the viewport (for small centred pages such as sign-in).
 * data-rounded   "true" renders it as a rounded card (for targets that already have inner padding).
 *
 * The close button hides the banner for the rest of the browser session. Hidden when printing.
 */
(function () {
  "use strict";

  var STORAGE_KEY = "raremedia-banner-hidden";
  var SITE_URL = "https://www.raremedia.com";
  var SLOGAN = "Always offering productive digital tools that transform hard work into simple.";

  var CSS =
    ".rm-banner{position:relative;z-index:1;display:flex;align-items:center;gap:12px;box-sizing:border-box;width:100%;min-height:42px;margin:0;padding:7px 16px;background:linear-gradient(90deg,#0c0a1d 0%,#1e1b4b 55%,#312e81 100%);color:#fff;font-family:Inter,system-ui,-apple-system,\"Segoe UI\",Roboto,sans-serif;font-size:13px;line-height:1.35;text-align:left;border-bottom:1px solid rgba(255,255,255,.14);box-shadow:inset 0 2px 0 0 #22d3ee}" +
    ".rm-banner *{box-sizing:border-box}" +
    ".rm-banner--fixed{position:fixed;top:0;left:0;right:0;z-index:2147483000}" +
    ".rm-banner--rounded{border-radius:12px;border:0;margin-bottom:14px}" +
    ".rm-banner__mark{flex:none;display:block;width:26px;height:26px;border-radius:7px}" +
    ".rm-banner__copy{display:flex;flex:1 1 auto;min-width:0;flex-wrap:wrap;align-items:baseline;gap:2px 10px}" +
    ".rm-banner__name{color:#fff;font-weight:800;font-size:12px;letter-spacing:.14em;text-decoration:none;white-space:nowrap}" +
    ".rm-banner__name:hover{color:#a5f3fc;text-decoration:none}" +
    ".rm-banner__text{margin:0;color:#e0e7ff;font-weight:500}" +
    ".rm-banner__cta{flex:none;display:inline-flex;align-items:center;gap:6px;padding:6px 13px;border:0;border-radius:999px;background:#22d3ee;color:#0c0a1d;font-weight:700;font-size:12px;line-height:1;text-decoration:none;white-space:nowrap;transition:background .15s ease,transform .15s ease}" +
    ".rm-banner__cta:hover{background:#67e8f9;color:#0c0a1d;text-decoration:none;transform:translateY(-1px)}" +
    ".rm-banner__cta svg{width:14px;height:14px}" +
    ".rm-banner__close{flex:none;display:inline-grid;place-items:center;width:28px;height:28px;margin:0 -6px 0 0;padding:0;border:0;border-radius:999px;background:transparent;color:#c7d2fe;cursor:pointer;transition:background .15s ease,color .15s ease}" +
    ".rm-banner__close:hover{background:rgba(255,255,255,.14);color:#fff}" +
    ".rm-banner__close svg{width:14px;height:14px}" +
    "@media (max-width:640px){.rm-banner{gap:10px;padding:7px 12px}.rm-banner__copy{flex-direction:column;gap:1px}.rm-banner__text{font-size:11.5px;line-height:1.3}.rm-banner__cta{width:30px;height:30px;padding:0;justify-content:center}.rm-banner__cta-label{display:none}}" +
    "@media print{.rm-banner{display:none!important}}";

  var script = document.currentScript;
  var attr = function (name, fallback) {
    var value = script && script.getAttribute(name);
    return value === null || value === undefined || value === "" ? fallback : value;
  };
  var config = {
    target: attr("data-target", "body"),
    fallback: attr("data-fallback", ""),
    mode: attr("data-mode", ""),
    rounded: attr("data-rounded", "") === "true"
  };

  function isHidden() {
    try {
      return window.sessionStorage.getItem(STORAGE_KEY) === "1";
    } catch (e) {
      return false;
    }
  }

  function remember() {
    try {
      window.sessionStorage.setItem(STORAGE_KEY, "1");
    } catch (e) {
      /* storage blocked: the banner simply returns on the next page */
    }
  }

  if (isHidden()) return;

  function ensureStyle() {
    if (document.getElementById("raremedia-banner-style")) return;
    var style = document.createElement("style");
    style.id = "raremedia-banner-style";
    style.textContent = CSS;
    document.head.appendChild(style);
  }

  var SVG = "http://www.w3.org/2000/svg";

  function svgElement(name, attributes) {
    var el = document.createElementNS(SVG, name);
    Object.keys(attributes).forEach(function (key) { el.setAttribute(key, attributes[key]); });
    return el;
  }

  function mark() {
    var svg = svgElement("svg", { "class": "rm-banner__mark", viewBox: "0 0 32 32", "aria-hidden": "true" });
    var defs = svgElement("defs", {});
    var gradient = svgElement("linearGradient", { id: "rmBannerMark", x1: "0", y1: "0", x2: "1", y2: "1" });
    gradient.appendChild(svgElement("stop", { offset: "0", "stop-color": "#22d3ee" }));
    gradient.appendChild(svgElement("stop", { offset: "1", "stop-color": "#6366f1" }));
    defs.appendChild(gradient);
    svg.appendChild(defs);
    svg.appendChild(svgElement("rect", { width: "32", height: "32", rx: "8", fill: "url(#rmBannerMark)" }));
    var text = svgElement("text", { x: "16", y: "21", "text-anchor": "middle", "font-family": "Inter,system-ui,sans-serif", "font-weight": "800", "font-size": "13", fill: "#0c0a1d" });
    text.textContent = "RM";
    svg.appendChild(text);
    return svg;
  }

  function icon(path, extra) {
    var svg = svgElement("svg", { viewBox: "0 0 16 16", "aria-hidden": "true", fill: "none", stroke: "currentColor", "stroke-width": "2", "stroke-linecap": "round", "stroke-linejoin": extra || "round" });
    svg.appendChild(svgElement("path", { d: path }));
    return svg;
  }

  function link(className, title) {
    var a = document.createElement("a");
    a.className = className;
    a.href = SITE_URL;
    a.target = "_blank";
    a.rel = "noopener noreferrer";
    if (title) a.title = title;
    return a;
  }

  function build() {
    var aside = document.createElement("aside");
    aside.className = "rm-banner" + (config.mode === "fixed" ? " rm-banner--fixed" : "");
    aside.setAttribute("role", "complementary");
    aside.setAttribute("aria-label", "Raremedia");

    aside.appendChild(mark());

    var copy = document.createElement("div");
    copy.className = "rm-banner__copy";
    var name = link("rm-banner__name");
    name.textContent = "RAREMEDIA";
    var text = document.createElement("span");
    text.className = "rm-banner__text";
    text.textContent = SLOGAN;
    copy.appendChild(name);
    copy.appendChild(text);
    aside.appendChild(copy);

    var cta = link("rm-banner__cta", "Discover Raremedia digital tools");
    var label = document.createElement("span");
    label.className = "rm-banner__cta-label";
    label.textContent = "Discover our tools";
    cta.appendChild(label);
    cta.appendChild(icon("M3 8h10M9 4l4 4-4 4"));
    aside.appendChild(cta);

    var close = document.createElement("button");
    close.type = "button";
    close.className = "rm-banner__close";
    close.setAttribute("aria-label", "Hide the Raremedia banner for this session");
    close.appendChild(icon("M4 4l8 8M12 4l-8 8"));
    close.addEventListener("click", function () {
      remember();
      aside.remove();
      if (observer) observer.disconnect();
      dismissed = true;
    });
    aside.appendChild(close);
    return aside;
  }

  var banner = null;
  var observer = null;
  var dismissed = false;

  function query(selector) {
    if (!selector) return null;
    try {
      return document.querySelector(selector);
    } catch (e) {
      return null;
    }
  }

  /** Puts the banner first inside the target, or inside the fallback while the target is missing. */
  function place() {
    if (dismissed) return true;
    var target = query(config.target);
    var host = target || query(config.fallback);
    if (!host) return false;
    if (!banner) banner = build();
    if (banner.parentNode !== host || host.firstElementChild !== banner) {
      host.insertBefore(banner, host.firstChild);
    }
    // The card look is for the padded target only; in the fallback holder the banner is a plain strip.
    if (config.rounded) banner.classList.toggle("rm-banner--rounded", !!target);
    return !!target;
  }

  function start() {
    ensureStyle();
    place();
    if (!window.MutationObserver) return;
    // Keep following the target: shells built by script after sign-in, single-page apps that re-render.
    observer = new MutationObserver(function () {
      if (!dismissed) place();
    });
    observer.observe(document.documentElement, { childList: true, subtree: true });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
})();
