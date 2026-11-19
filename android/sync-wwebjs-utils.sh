#!/usr/bin/env bash
# Regenerate app/src/main/assets/wa_utils.js from the installed whatsapp-web.js.
# Run after `npm ci` or whenever you update node_modules/whatsapp-web.js.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/node_modules/whatsapp-web.js/src/util/Injected/Utils.js"
OUT="$ROOT/android/app/src/main/assets/wa_utils.js"

if [[ ! -f "$SRC" ]]; then
  echo "missing $SRC — run npm ci at the repo root first" >&2
  exit 1
fi

{
  printf '%s\n' ';(function () {'
  printf '%s\n' '  if (window.__loadWWebUtils) return;'
  printf '%s\n' '  window.__loadWWebUtils = function () {'
  printf '%s\n' "    if (typeof window.WWebJS !== 'undefined') return;"
  printf '%s\n' '    var exports = {};'
  printf '%s\n' '    /* ===== upstream whatsapp-web.js src/util/Injected/Utils.js (Apache-2.0) ===== */'
  sed "1{/^'use strict';$/d;}" "$SRC"
  printf '%s\n' '    exports.LoadUtils();'
  printf '%s\n' '    /* ===== end upstream ===== */'
  printf '%s\n' '  };'
  printf '%s\n' '})();'
} > "$OUT"

node --check "$OUT"
echo "wrote $OUT ($(wc -l < "$OUT") lines)"
