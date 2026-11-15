#!/bin/zsh
set -eu
INSPECTOR_DIR="${0:A:h}"
INSPECTOR_JAVA="${SNAP_JAVA_HOME:-/opt/homebrew/opt/openjdk/libexec/openjdk.jdk/Contents/Home}/bin/java"
if [[ ! -x "$INSPECTOR_JAVA" || ! -x /usr/bin/sandbox-exec ]]; then
  print 'The existing Java runtime or macOS network blocker is unavailable. No scan was started.'
  read '?Press Return to close.'
  exit 1
fi
INSPECTOR_CHOICE=$(/usr/bin/osascript -e 'button returned of (display dialog "Inspect your full export locally, including custom stickers. The checker’s network access is blocked. Reports stay on this Mac. No files are sent to Codex. Choose files already downloaded to your Mac." buttons {"Cancel", "DELETEPERSONAL", "Choose export"} default button "Choose export")') || exit 0
if [[ "$INSPECTOR_CHOICE" == DELETEPERSONAL ]]; then
 /usr/bin/sandbox-exec -p '(version 1)(allow default)(deny network*)' "$INSPECTOR_JAVA" -Xmx256m -cp "$INSPECTOR_DIR/lib/*" OfflineInspector --deletepersonal
 read '?Press Return to close.'
 exit 0
fi
INSPECTOR_TYPE=$(/usr/bin/osascript -e 'button returned of (display dialog "Choose original ZIP parts, or a folder containing extracted files and ZIPs." buttons {"Cancel", "Export folder", "ZIP files"} default button "ZIP files")') || exit 0
if [[ "$INSPECTOR_TYPE" == 'Export folder' ]]; then
 INSPECTOR_SELECTED=$(/usr/bin/osascript -e 'POSIX path of (choose folder with prompt "Choose your local export folder")') || exit 0
 INSPECTOR_INPUTS=("$INSPECTOR_SELECTED")
else
 INSPECTOR_SELECTED=$(/usr/bin/osascript <<'APPLESCRIPT'
set selectedFiles to choose file with prompt "Choose your local Snapchat ZIP parts" with multiple selections allowed
set paths to {}
repeat with selectedFile in selectedFiles
 set end of paths to POSIX path of selectedFile
end repeat
set AppleScript's text item delimiters to linefeed
return paths as text
APPLESCRIPT
) || exit 0
 INSPECTOR_INPUTS=("${(@f)INSPECTOR_SELECTED}")
fi
/usr/bin/sandbox-exec -p '(version 1)(allow default)(deny network*)' "$INSPECTOR_JAVA" -Xmx256m -cp "$INSPECTOR_DIR/lib/*" OfflineInspector "${INSPECTOR_INPUTS[@]}"
INSPECTOR_REPORT_ROOT="$HOME/Library/Application Support/Snap Export Inspector"
if [[ -f "$INSPECTOR_REPORT_ROOT/latest-report.txt" ]]; then
 IFS= read -r INSPECTOR_REPORT < "$INSPECTOR_REPORT_ROOT/latest-report.txt" || true
 if [[ "$INSPECTOR_REPORT" == "$INSPECTOR_REPORT_ROOT"/report-*/report.html ]]; then
  /usr/bin/open -R "$INSPECTOR_REPORT"
 fi
fi
read '?Press Return to close.'
