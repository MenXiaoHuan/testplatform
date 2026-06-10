const { existsSync, readFileSync, writeFileSync } = require('node:fs');
const path = require('node:path');

const REPORT_LANGUAGE = 'zh';
const REPORT_CUSTOMIZATION_MARKER = 'allure-report-ui-customizations';
const REPORT_COLLAPSED_SECTION_IDS = ['report-default-metadata', 'report-default-variables'];
const METADATA_SECTION_LABELS = ['Metadata', '元数据', 'Variables', '变量'];

const REPORT_CUSTOMIZATION_SCRIPT = `
<script id="${REPORT_CUSTOMIZATION_MARKER}">
  (function () {
    const collapsedSectionIds = ${JSON.stringify(REPORT_COLLAPSED_SECTION_IDS)};
    const metadataSectionLabels = ${JSON.stringify(METADATA_SECTION_LABELS)};
    let observerStarted = false;

    window.__allureReportUiCustomizations = window.__allureReportUiCustomizations || {
      attempts: 0,
      started: false,
    };

    function mergeCollapsedSections() {
      try {
        const rawValue = window.localStorage.getItem('collapsedTrees');
        const currentValue = rawValue ? JSON.parse(rawValue) : [];
        const mergedValue = Array.from(
          new Set([...(Array.isArray(currentValue) ? currentValue : []), ...collapsedSectionIds])
        );

        window.localStorage.setItem('collapsedTrees', JSON.stringify(mergedValue));
      } catch (error) {
        console.warn('Failed to persist default collapsed report sections.', error);
      }
    }

    function normalizeText(value) {
      return String(value || '').replace(/\\s+/g, ' ').trim();
    }

    function isMetadataSectionButton(button) {
      const label = normalizeText(button.textContent);

      return metadataSectionLabels.some((prefix) => label.startsWith(prefix));
    }

    function collapseMetadataSections() {
      const buttons = Array.from(document.querySelectorAll('button'));

      buttons.forEach((button) => {
        if (!isMetadataSectionButton(button)) {
          return;
        }

        const section = button.parentElement && button.parentElement.parentElement;
        if (!section) {
          return;
        }

        if (section.children.length < 2) {
          button.dataset.reportDefaultCollapsed = 'true';
          return;
        }

        const expandedContent = Array.from(section.children)
          .filter((element) => !element.contains(button))
          .some((element) => normalizeText(element.textContent).length > 0);

        if (!expandedContent) {
          button.dataset.reportDefaultCollapsed = 'true';
          return;
        }

        const attempts = Number(button.dataset.reportDefaultCollapseAttempts || '0');
        if (attempts >= 5) {
          return;
        }

        button.dataset.reportDefaultCollapseAttempts = String(attempts + 1);
        button.click();
      });
    }

    const ensureObserver = function () {
      if (observerStarted || !document.body) {
        return;
      }

      observerStarted = true;

      const observer = new MutationObserver(function () {
        collapseMetadataSections();
      });

      observer.observe(document.body, { childList: true, subtree: true });
      window.setTimeout(function () {
        observer.disconnect();
      }, 5000);
    };

    const start = function () {
      window.__allureReportUiCustomizations.attempts += 1;
      window.__allureReportUiCustomizations.started = true;

      mergeCollapsedSections();
      collapseMetadataSections();
      ensureObserver();
    };

    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', start, { once: true });
    } else {
      start();
    }

    window.addEventListener('load', start, { once: true });
    window.setTimeout(start, 0);
    window.setTimeout(start, 500);
    window.setTimeout(start, 1500);
  })();
</script>`;

function forceReportLanguage(html, nextLanguage = REPORT_LANGUAGE) {
  return html.replace(
    /("reportLanguage"\s*:\s*")([^"]+)(")/,
    `$1${nextLanguage}$3`
  );
}

function injectReportShell(html) {
  const patchedHtml = forceReportLanguage(html);

  if (patchedHtml.includes(`id="${REPORT_CUSTOMIZATION_MARKER}"`)) {
    return patchedHtml;
  }

  const injection = REPORT_CUSTOMIZATION_SCRIPT;

  if (patchedHtml.includes('</head>')) {
    return patchedHtml.replace('</head>', `${injection}\n</head>`);
  }

  return `${patchedHtml}\n${injection}`;
}

function patchGeneratedReportShell(deps = {}) {
  const reportDir = deps.reportDir ?? './reports/allure-report';
  const indexPath = path.join(reportDir, 'index.html');
  const fileExists = deps.existsSync ?? existsSync;

  if (!fileExists(indexPath)) {
    return false;
  }

  const read = deps.readFileSync ?? readFileSync;
  const write = deps.writeFileSync ?? writeFileSync;
  const currentHtml = read(indexPath, 'utf8');
  const nextHtml = injectReportShell(currentHtml);

  if (nextHtml !== currentHtml) {
    write(indexPath, nextHtml, 'utf8');
  }

  return true;
}

module.exports = {
  REPORT_LANGUAGE,
  forceReportLanguage,
  injectReportShell,
  patchGeneratedReportShell,
};
