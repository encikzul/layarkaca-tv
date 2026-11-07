#!/usr/bin/env bash
# LayarKaca: pemasang mudah dalam Bahasa Melayu.
#
#   curl -fsSL https://raw.githubusercontent.com/encikzul/layarkaca-tv/main/pasang.sh | bash
#
# atau, dari folder projek: bash pasang.sh
# Ia bertanya alamat IP TV, menyambung, mengambil sandaran, membuang iklan untuk model yang sudah diuji,
# memuat turun dan menyemak APK, memasang LayarKaca, dan membuka pemilih lokasi di TV. Menu yang sama boleh
# membatalkan semuanya. Untuk ujian: APK=<fail.apk> bash pasang.sh memasang APK itu, bukan dari Releases.
set -uo pipefail

REPO="${LAYARKACA_REPO:-encikzul/layarkaca-tv}"
CONF="$HOME/.layarkaca"
BACKUPS="$HOME/layarkaca-sandaran"
PKG=com.gathin.tvhome

if [ -t 1 ]; then B=$'\e[1m'; G=$'\e[32m'; Y=$'\e[33m'; R=$'\e[31m'; N=$'\e[0m'; else B=; G=; Y=; R=; N=; fi
say()  { printf '%s\n' "$*"; }
step() { printf '\n%s%s%s\n' "$B" "$*" "$N"; }
ok()   { printf '%s✓ %s%s\n' "$G" "$*" "$N"; }
warn() { printf '%s! %s%s\n' "$Y" "$*" "$N"; }
die()  { printf '\n%s✗ %s%s\n' "$R" "$*" "$N"; exit 1; }
ask()  { local a; read -r -p "$1" a </dev/tty || a=""; printf '%s' "$a"; }
yes_no() { local a; a=$(ask "$1 (y/t) "); [ "$a" = y ] || [ "$a" = Y ] || [ "$a" = ya ]; }

# ---------------------------------------------------------------------------------------------- setup

find_project() {
  local here
  here="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || true)"
  if [ -n "$here" ] && [ -f "$here/launcher/build-and-install.sh" ]; then DIR="$here"; return; fi
  DIR="$HOME/layarkaca-tv"
  step "Memuat turun LayarKaca..."
  local tmp="$DIR.baru"
  rm -rf "$tmp" && mkdir -p "$tmp"
  curl -fsSL "https://codeload.github.com/$REPO/tar.gz/refs/heads/main" | tar -xz -C "$tmp" --strip-components 1 \
    || die "Tidak dapat memuat turun projek. Semak sambungan internet dan cuba lagi."
  [ -d "$DIR/tools/venv" ] && mv "$DIR/tools/venv" "$tmp/tools/venv"
  rm -rf "$DIR" && mv "$tmp" "$DIR"
  ok "Projek di $DIR"
}

setup_python() {
  cd "$DIR" || die "Folder projek hilang: $DIR"
  if [ -n "${PREFIX:-}" ] && [ "${PREFIX#*com.termux}" != "$PREFIX" ]; then
    command -v python >/dev/null 2>&1 && python -c 'import cryptography' 2>/dev/null \
      || { step "Memasang Python untuk Termux (sekali sahaja)..."; pkg install -y python python-cryptography >/dev/null; }
    VENV_ARGS="--system-site-packages"; PYBIN=python
  else
    VENV_ARGS=""; PYBIN=python3
    command -v python3 >/dev/null 2>&1 && python3 -c 'import venv' 2>/dev/null \
      || die "Python 3 tiada. Mac: jalankan 'xcode-select --install'. Linux/ChromeOS: 'sudo apt install python3 python3-venv'. Kemudian cuba lagi."
  fi
  if ! tools/venv/bin/python -c 'import adb_shell' 2>/dev/null; then
    step "Menyediakan alat (sekali sahaja, 1 hingga 2 minit)..."
    rm -rf tools/venv
    $PYBIN -m venv $VENV_ARGS tools/venv \
      || die "Python tidak dapat membuat venv. Linux/ChromeOS: jalankan 'sudo apt install python3-venv python3-pip', kemudian cuba lagi."
    tools/venv/bin/pip install -q -r tools/requirements.txt \
      || die "Gagal memasang alat Python. Semak sambungan internet dan cuba lagi."
  fi
  ok "Alat sedia"
}

load_conf() { [ -f "$CONF" ] && . "$CONF"; return 0; }
save_conf() {
  { printf 'TV_HOST=%q\n' "${TV_HOST:-}"; printf 'LIST=%q\n' "${LIST:-}"; printf 'SETUP_OFF=%q\n' "${SETUP_OFF:-}"; printf 'SAVER=%q\n' "${SAVER:-}"; } > "$CONF"
}

T() { tools/venv/bin/python -I tools/tv.py "$@"; }

connect() {
  local ip
  step "Langkah 1: sambung ke TV"
  say "Di TV: Settings → System → About, tekan OK pada 'Android TV OS build' 7 kali,"
  say "kemudian Settings → System → Developer options → hidupkan USB debugging."
  say "Alamat IP TV ada di Settings → Network & Internet → rangkaian Wi-Fi anda."
  while :; do
    ip=$(ask "Alamat IP TV${TV_HOST:+ [$TV_HOST]}: ")
    ip="${ip:-${TV_HOST:-}}"
    if printf '%s' "$ip" | grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}$'; then break; fi
    warn "Tulis empat nombor seperti 192.168.1.50"
  done
  export TV_HOST="$ip"
  local tries=0 out
  while :; do
    say "Menyambung ke $TV_HOST... Jika TV bertanya 'Allow USB debugging?', tandakan Always allow dan tekan OK."
    out=$(T 'getprop ro.product.model; getprop ro.product.device; getprop ro.build.version.sdk; getprop ro.product.manufacturer' 2>&1)
    if [ $? -eq 0 ] && [ -n "$out" ]; then break; fi
    tries=$((tries + 1))
    if [ $tries -ge 3 ]; then
      say ""
      warn "Masih tidak dapat menyambung. Semak:"
      say "  • TV dan peranti ini pada Wi-Fi yang sama, dan IP betul"
      say "  • USB debugging hidup"
      say "  • Mac: System Settings → Privacy & Security → Local Network → hidupkan Terminal"
      say "  • Sesetengah Google TV perlu 'Wireless debugging' dan kod pairing: lihat bahagian Masalah lazim dalam panduan"
      die "Tiada apa-apa diubah pada TV."
    fi
    ask "Tidak berjaya. Tekan Enter untuk cuba lagi... " >/dev/null
  done
  MODEL=$(printf '%s\n' "$out" | sed -n 1p | tr -d '\r')
  DEVICE=$(printf '%s\n' "$out" | sed -n 2p | tr -d '\r')
  SDK=$(printf '%s\n' "$out" | sed -n 3p | tr -d '\r ')
  MAKER=$(printf '%s\n' "$out" | sed -n 4p | tr -d '\r')
  ok "Disambung: $MAKER $MODEL ($DEVICE), Android API $SDK"
  save_conf
}

# Skrin utama asal yang mungkin dimatikan oleh senarai buang iklan atau pemasangan.
STOCK_HOMES="com.google.android.tvlauncher com.google.android.apps.tv.launcherx com.google.android.leanbacklauncher com.mitv.tvhome com.mitv.tvhome.atv"

# Jika butang Home tidak membuka apa-apa (TV gelap), hidupkan semula skrin utama asal.
ensure_home() {
  local r p
  r=$(T 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' 2>/dev/null | tail -n 1 | tr -d '\r')
  case "$r" in
    */*FallbackHome*|"") ;;
    */*) return 0 ;;
  esac
  warn "TV ini tiada skrin utama yang hidup (skrin gelap). Menghidupkan semula skrin utama asal..."
  for p in $STOCK_HOMES; do
    T "pm list packages -d $p" 2>/dev/null | grep -qx "package:$p" && T "pm enable $p" >/dev/null && ok "Dihidupkan: $p"
  done
  T 'input keyevent KEYCODE_HOME' >/dev/null
}

# Senarai yang mematikan penyelamat skrin Google (Backdrop) mesti menukar penyelamat skrin dahulu,
# jika tidak TV boleh menjadi gelap bila dibiarkan.
use_clock_saver() {
  grep -qE '^\s*com\.google\.android\.backdrop' "$1" || return 0
  SAVER=$(T 'settings get secure screensaver_components' | tail -n 1 | tr -d '\r')
  T "settings put secure screensaver_components $PKG/.ClockDream" >/dev/null && save_conf \
    && ok "Penyelamat skrin ditukar kepada jam LayarKaca"
}

# Waktu solat dan panel masuk waktu perlukan zon waktu Malaysia. Box yang dibeli dari luar negara kadang-kadang
# kekal pada zon lain (contohnya New York), dan jam TV menjadi salah. LayarKaca membetulkan dirinya sendiri, tetapi
# jam aplikasi lain tetap salah, jadi cuba betulkan TV juga.
fix_timezone() {
  local z
  z=$(T 'date +%z' 2>/dev/null | tail -n 1 | tr -d '\r ')
  [ "$z" = "+0800" ] && return 0
  warn "Zon waktu TV ini bukan Malaysia ($z, $(T 'getprop persist.sys.timezone' | tail -n 1 | tr -d '\r'))."
  T 'settings put global auto_time_zone 0; service call alarm 3 s16 Asia/Kuala_Lumpur' >/dev/null 2>&1
  z=$(T 'date +%z' 2>/dev/null | tail -n 1 | tr -d '\r ')
  if [ "$z" = "+0800" ]; then
    ok "Zon waktu TV ditukar kepada Kuala Lumpur (GMT+8)"
  else
    T 'settings put global auto_time_zone 1' >/dev/null 2>&1
    say "LayarKaca tetap guna waktu Malaysia, tetapi jam TV untuk aplikasi lain masih salah. Betulkan di TV:"
    say "  Settings → System → Date & time → Time zone → Kuala Lumpur (GMT+08:00)"
  fi
}

stock_launcher() {
  if T 'pm list packages com.google.android.apps.tv.launcherx' | grep -q launcherx; then
    echo com.google.android.apps.tv.launcherx
  else
    echo com.google.android.tvlauncher
  fi
}

# ---------------------------------------------------------------------------------------------- actions

install() {
  [ "${SDK:-0}" -ge 26 ] 2>/dev/null || die "TV ini Android API $SDK. LayarKaca perlukan Android 8 (API 26) ke atas. Tiada apa-apa diubah."
  [ "$MAKER" = Amazon ] && die "Ini Fire TV: Fire OS sentiasa mengambil semula butang Home. Tiada apa-apa diubah."
  ensure_home

  step "Langkah 2: sandaran"
  local dir="$BACKUPS/$DEVICE-$(date +%Y%m%d-%H%M)"
  mkdir -p "$dir"
  T 'pm list packages -f' > "$dir/packages.txt"
  T 'pm list packages -d' > "$dir/disabled.txt"
  T 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' > "$dir/home.txt"
  [ -s "$dir/packages.txt" ] || die "Sandaran kosong; sambungan terputus. Tiada apa-apa diubah."
  ok "Sandaran di $dir"

  step "Langkah 3: muat turun LayarKaca"
  local apk="${APK:-}"
  if [ -z "$apk" ]; then
    local urls
    urls=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" | tools/venv/bin/python -I -c '
import json, sys
for a in json.load(sys.stdin).get("assets", []):
    n = a["name"]
    if n.endswith(".apk") or n.endswith(".apk.sha256"):
        print(a["browser_download_url"])') || die "Tidak dapat mencari keluaran terkini. Semak internet dan cuba lagi."
    local apk_url sha_url
    apk_url=$(printf '%s\n' "$urls" | grep '\.apk$' | head -1)
    sha_url=$(printf '%s\n' "$urls" | grep '\.sha256$' | head -1)
    [ -n "$apk_url" ] && [ -n "$sha_url" ] || die "Keluaran terkini tiada APK. Cuba lagi nanti."
    apk="$DIR/$(basename "$apk_url")"
    curl -fsSL -o "$apk" "$apk_url" && curl -fsSL -o "$apk.sha256" "$sha_url" || die "Muat turun gagal. Cuba lagi."
    local want got
    want=$(awk '{print $1}' "$apk.sha256")
    got=$( (command -v sha256sum >/dev/null && sha256sum "$apk" || shasum -a 256 "$apk") | awk '{print $1}')
    [ "$want" = "$got" ] || { rm -f "$apk"; die "Fail APK tidak sepadan dengan SHA-256 rasmi. Tidak dipasang."; }
    ok "APK disemak (SHA-256 sepadan)"
  else
    ok "Menggunakan APK tempatan: $apk"
  fi

  step "Langkah 4: pasang"
  warn "JANGAN SENTUH alat kawalan jauh sehingga selesai (kira-kira 30 saat)."
  local log; log=$(mktemp)
  if ! APK="$apk" INPUT_KEY_SERVICE=1 bash launcher/build-and-install.sh >"$log" 2>&1; then
    tail -5 "$log"
    grep -q UPDATE_INCOMPATIBLE "$log" \
      && say "LayarKaca versi lain sudah ada di TV ini. Pilih 'Batalkan semua' dalam menu, kemudian pasang semula."
    ensure_home
    die "Pemasangan tidak berjaya. Skrin utama asal TV dikekalkan; tiada aplikasi dimatikan."
  fi
  ok "LayarKaca dipasang dan menjadi skrin utama"

  # Buang iklan hanya SELEPAS LayarKaca menjadi skrin utama. Senarai mematikan skrin utama asal
  # (contoh Mi Box S: PatchWall dan tvlauncher); jika dibuat lebih awal dan pemasangan gagal, TV tiada
  # skrin utama langsung dan menjadi gelap.
  step "Langkah 5: buang iklan"
  local list="" f
  for f in examples/*/debloat.txt; do
    if head -3 "$f" | grep -qE "[ (,]$DEVICE([ ,)]|$)"; then list="$f"; break; fi
  done
  if [ -n "$list" ]; then
    local n; n=$(grep -cvE '^\s*(#|$)' "$list")
    say "Ada senarai yang sudah diuji untuk TV ini: $list ($n aplikasi iklan, penjejak dan demo)."
    say "Aplikasi hanya dimatikan, tidak dibuang. HDMI, alat kawalan jauh, Wi-Fi, carian suara dan cast dikekalkan."
    if yes_no "Matikan aplikasi dalam senarai ini?"; then
      bash tools/disable.sh "$list" >/dev/null && ok "Iklan dan penjejak dimatikan" && LIST="$list" && save_conf
      use_clock_saver "$list"
    else
      say "Dilangkau."
    fi
  else
    say "Tiada senarai yang diuji untuk $DEVICE; buang iklan dilangkau (skrin utama LayarKaca tetap tiada iklan)."
  fi
  if [ -z "$list" ] || [ -z "${LIST:-}" ]; then
    if T 'pm list packages com.google.android.tungsten.setupwraith' | grep -q setupwraith; then
      T 'pm disable-user --user 0 com.google.android.tungsten.setupwraith' >/dev/null && SETUP_OFF=1 && save_conf
      ok "Wizard persediaan Google TV dimatikan (supaya butang Home kekal pada LayarKaca)"
    fi
  fi


  if ! T "[ -f /sdcard/Android/data/$PKG/files/config.json ] && echo ADA" 2>/dev/null | grep -q ADA; then
    local theme; theme=$(mktemp -d)
    tools/venv/bin/python -I -c '
import json, sys
c = json.load(open(sys.argv[1]))
for k in ("prayer_zone", "weather_location", "weather_place", "haze_station"):
    c.pop(k, None)
json.dump(c, open(sys.argv[2], "w"), indent=2)' themes/malaysia/config.json "$theme/config.json"
    bash tools/theme.sh "$theme" >/dev/null && ok "Tetapan asal Malaysia dipasang"
  fi

  T "appops set $PKG SYSTEM_ALERT_WINDOW allow" >/dev/null && ok "Panel waktu solat dibenarkan"
  fix_timezone

  step "Langkah 6: pilih lokasi di TV"
  T "am start -n $PKG/.PlaceActivity" >/dev/null
  say "Lihat TV: pilih negeri, kawasan dan stesen udara dengan alat kawalan jauh, tekan OK."
  say "Untuk menukar lokasi nanti: tekan Bawah di skrin utama dan buka jubin 'Lokasi'."
  printf '\n%sSiap!%s Tekan Home pada alat kawalan jauh.\n' "$G$B" "$N"
}

change_place() {
  T "am start -n $PKG/.PlaceActivity" >/dev/null && ok "Pemilih lokasi dibuka di TV"
}

test_panel() {
  T "touch /sdcard/Android/data/$PKG/files/azan-test; input keyevent KEYCODE_HOME" >/dev/null \
    && ok "Lihat TV: panel waktu solat dan bunyi bip 7 saat"
}

undo() {
  step "Batalkan semua"
  say "Ini menghidupkan semula aplikasi yang dimatikan, mengembalikan skrin utama asal dan membuang LayarKaca."
  yes_no "Teruskan?" || { say "Tiada apa-apa diubah."; return; }
  local stock; stock=$(stock_launcher)
  if [ -n "${LIST:-}" ] && [ -f "$LIST" ]; then
    bash tools/restore.sh "$LIST" "$stock" >/dev/null && ok "Aplikasi yang dimatikan dihidupkan semula"
  fi
  T "pm enable $stock" >/dev/null
  [ -n "${SETUP_OFF:-}" ] && T 'pm enable com.google.android.tungsten.setupwraith' >/dev/null
  case "${SAVER:-}" in */*) T "settings put secure screensaver_components $SAVER" >/dev/null ;; esac
  T "cmd package set-home-activity $stock" >/dev/null 2>&1 || true
  T "pm uninstall $PKG" >/dev/null && ok "LayarKaca dibuang"
  T 'input keyevent KEYCODE_HOME' >/dev/null
  LIST=""; SETUP_OFF=""; SAVER=""; save_conf
  ok "TV kembali seperti asal. Anda boleh matikan USB debugging di Developer options."
}

# ---------------------------------------------------------------------------------------------- main

printf '%sLayarKaca%s: skrin utama tanpa iklan, dengan waktu solat, cuaca dan IPU.\n' "$B" "$N"
load_conf
find_project
setup_python
connect
while :; do
  step "Apa yang anda mahu buat?"
  say "  1) Pasang atau kemas kini LayarKaca"
  say "  2) Tukar lokasi"
  say "  3) Uji panel waktu solat"
  say "  4) Batalkan semua (kembalikan TV seperti asal)"
  say "  5) Keluar"
  case "$(ask 'Pilih 1-5: ')" in
    1) install ;;
    2) change_place ;;
    3) test_panel ;;
    4) undo ;;
    5|"") exit 0 ;;
    *) warn "Pilih nombor 1 hingga 5." ;;
  esac
done
