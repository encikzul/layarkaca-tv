# LayarKaca

Versi Malaysia bagi [Glass TV](https://github.com/cocombee/glass-tv): skrin utama yang ringan dan bebas iklan
untuk Android TV dan Google TV, dengan tambahan untuk Malaysia:

- **Kad waktu solat** ikut zon JAKIM: waktu seterusnya, kiraan masa, dan lima waktu hari ini
- **Peringatan masuk waktu**: panel di atas apa sahaja yang sedang ditonton, dengan bunyi 7 saat
  (atau fail azan anda sendiri). Boleh disenyapkan untuk hari itu
- **Mod Ramadan** yang hidup sendiri: kiraan ke berbuka dan imsak, waktu Imsak, hari ke berapa
- **Cuaca MET Malaysia** untuk daerah anda, dan **jalur amaran** bila MET keluarkan amaran untuk kawasan anda
- **Indeks Pencemar Udara (IPU)** dari stesen JAS terdekat, dengan jalur amaran jerebu bila melebihi 100
- **Tarikh dalam Bahasa Melayu** dan tarikh Hijri di bawah jam

Semua ciri asal Glass TV kekal: tiada iklan, aplikasi disusun sendiri, gambar tempat menarik setiap minggu,
dan cache dibersihkan sendiri.

> **Penting.** Ini projek peribadi, bukan aplikasi rasmi JAKIM, MET Malaysia atau JAS. Data diambil terus
> daripada sumber terbuka mereka (senarai di bawah). Untuk waktu solat, sentiasa rujuk
> [e-Solat JAKIM](https://www.e-solat.gov.my) jika ragu.

## Cara paling mudah

1. Di TV: **Settings → System → About**, tekan OK pada **Android TV OS build** 7 kali, kemudian
   **Developer options → USB debugging** → hidupkan. Catat alamat IP TV (**Settings → Network & Internet**).
2. Tampal satu arahan:
   - **Windows 10/11**: buka **PowerShell** dan tampal

     ```powershell
     irm https://raw.githubusercontent.com/encikzul/layarkaca-tv/main/pasang.ps1 | iex
     ```

   - **Mac, Linux, ChromeOS (Linux)**: buka **Terminal** dan tampal; **telefon Android**: dalam Termux (eksperimen)

     ```bash
     curl -fsSL https://raw.githubusercontent.com/encikzul/layarkaca-tv/main/pasang.sh | bash
     ```

3. Taip IP TV, tekan **Allow** di TV, pilih **1**, dan jangan sentuh alat kawalan jauh sehingga **Siap!**
4. Di TV, pilih negeri, kawasan dan stesen udara dengan alat kawalan jauh.

Arahan yang sama juga menukar lokasi, menguji panel waktu solat dan membatalkan semuanya. Lokasi boleh ditukar
bila-bila masa melalui jubin **Lokasi** dalam senarai aplikasi. Langkah manual penuh ada di bawah.

## TV yang boleh dan tidak boleh

| TV / kotak | Boleh? |
|---|---|
| Android TV atau Google TV, Android 8 ke atas (Sony, TCL, Xiaomi, Philips, Sharp, kebanyakan Hisense, Chromecast with Google TV, Mi Box…) | **Ya**, semua ciri |
| Android TV 5 hingga 7, Fire TV | Debloat sahaja, tiada skrin utama baru |
| Samsung (Tizen), LG (webOS), Roku, VIDAA, Vizio, Apple TV | **Tidak** |
| Kotak unifi TV (TM) | Mungkin tidak: TM boleh mengunci Developer options. Jangan matikan aplikasi unifi TV |

Untuk TV Samsung atau LG, pilihan terbaik ialah Chromecast with Google TV atau kotak Android TV di port HDMI.

Senarai model yang telah diuji: [docs/DEVICES.md](docs/DEVICES.md). Sony KD-65X85L (Malaysia) ada senarai
debloat sendiri: [examples/sony-kd-65x85l](examples/sony-kd-65x85l/debloat.txt).

## Selamat dan boleh dibatalkan

- Tiada root. Aplikasi sistem hanya **dimatikan**, tidak dibuang, dan boleh dihidupkan semula.
- Skrip pemasangan memeriksa Glass TV benar-benar berjalan sebelum mengubah apa-apa. Jika gagal, ia
  memulihkan skrin utama asal TV dengan sendiri.
- Ambil sandaran dulu (langkah 3). Cara membatalkan semuanya ada di bahagian akhir.

## Langkah 1: sediakan TV

1. **Settings → System → About**, tekan OK pada **Android TV OS build** 7 kali sehingga keluar
   "You are now a developer". (Sesetengah jenama letak di tempat lain; cari "<model TV anda> developer options".)
2. **Settings → System → Developer options** → hidupkan **USB debugging**.
3. Catat **alamat IP** TV: **Settings → Network & Internet → rangkaian Wi-Fi anda**, contohnya `192.168.1.50`.

TV dan peranti anda mesti pada **Wi-Fi yang sama**. Tiada kabel diperlukan.

## Langkah 2: pilih cara memasang

| Ada | Cara |
|---|---|
| Laptop macOS atau Linux | [Cara A](#cara-a-laptop): paling selamat dan sudah diuji |
| Laptop Windows | [Cara paling mudah](#cara-paling-mudah) (`pasang.ps1`); cara manual: skrip `.ps1` dalam [docs/CLEANYOURTV-README.md](docs/CLEANYOURTV-README.md), dengan `launcher\build-and-install.ps1 -Apk <fail.apk>` |
| Telefon Android sahaja | [Cara B](#cara-b-telefon-android-eksperimen): **eksperimen**, belum diuji |
| iPhone sahaja | Tidak boleh. iOS tiada alat ADB untuk bercakap dengan TV |

APK siap dibina ada di halaman **[Releases](../../releases)**, bersama SHA-256 dan sijil tandatangannya.
APK itu dibina oleh GitHub Actions terus daripada kod repo ini, jadi anda boleh semak apa yang dipasang.

## Cara A: laptop

Buka Terminal dan jalankan satu demi satu. Gantikan `192.168.1.50` dengan IP TV anda.

```bash
git clone https://github.com/encikzul/layarkaca-tv.git && cd layarkaca-tv
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50
tools/venv/bin/python -I tools/tv.py --probe
```

TV akan tanya **"Allow USB debugging?"**: tandakan **Always allow**, tekan OK, kemudian jalankan arahan
`--probe` sekali lagi. Ia patut paparkan enam baris maklumat model. Jika ia tamat masa, lihat
[Google TV yang perlu pairing](#google-tv-yang-perlu-pairing).

**3. Sandaran** (tiada apa-apa diubah pada TV):

```bash
mkdir -p backup
tools/venv/bin/python -I tools/tv.py 'pm list packages -f' > backup/packages.txt
tools/venv/bin/python -I tools/tv.py 'pm list packages -d' > backup/disabled.txt
tools/venv/bin/python -I tools/tv.py 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' > backup/home.txt
```

**4. Debloat** (pilihan, tetapi disyorkan). Jika model anda ada dalam [examples/](examples/), baca fail
`debloat.txt` itu dahulu dan buang baris yang anda guna, kemudian:

```bash
tools/disable.sh examples/<model-anda>/debloat.txt
```

Selepas itu tekan Home, cuba HDMI, alat kawalan jauh dan Wi-Fi. Jika model anda tiada dalam senarai,
langkau debloat. Untuk **Google TV**, matikan sekurang-kurangnya wizard persediaan, yang boleh merampas
butang Home (hidupkan semula sebelum factory reset):

```bash
tools/venv/bin/python -I tools/tv.py 'pm disable-user --user 0 com.google.android.tungsten.setupwraith'
```

**5. Pasang LayarKaca.** Muat turun APK dari [Releases](../../releases), semak SHA-256, kemudian:

```bash
shasum -a 256 ~/Downloads/layarkaca-*.apk      # mesti sama dengan fail .sha256 di Releases
APK=~/Downloads/layarkaca-v1.0.apk launcher/build-and-install.sh
```

Jangan tekan alat kawalan jauh sehingga keluar `Installed. Press Home on the remote to see it.`
Jika ada baris `ERROR:`, skrip telah memulihkan skrin utama asal; mesejnya menerangkan sebabnya.

(Mahu bina APK sendiri? Pasang JDK 17 dan Android SDK 34, kemudian jalankan
`launcher/build-and-install.sh` tanpa `APK=`. Lihat [AGENTS.md](AGENTS.md), langkah 4.)

**6. Tetapkan lokasi anda.** Salin tema contoh, ubah empat tetapan tempat (jadual di bawah), dan hantar ke TV:

```bash
cp -r themes/malaysia ~/tema-saya
nano ~/tema-saya/config.json          # ubah prayer_zone, weather_location, weather_place, haze_station
tools/theme.sh ~/tema-saya
```

**7. Benarkan panel waktu solat muncul di atas aplikasi lain** (sekali sahaja):

```bash
tools/venv/bin/python -I tools/tv.py 'appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow'
```

Tanpa langkah ini, anda hanya dapat notis kecil dan bunyi. Untuk cuba panel sekarang:
`tools/venv/bin/python -I tools/tv.py 'touch /sdcard/Android/data/com.gathin.tvhome/files/azan-test'`,
kemudian tekan Home.

## Cara B: telefon Android (eksperimen)

> **Belum diuji pada mana-mana TV.** Ikut hanya jika anda selesa dengan arahan teks. Jika ada yang tidak
> menjadi, berhenti dan buka Issue. Cara membatalkan ada di bahagian akhir.

1. Pasang **Termux** dari [F-Droid](https://f-droid.org/packages/com.termux/) (versi Play Store sudah lama).
2. Dalam Termux:

```bash
pkg update && pkg install -y git python python-cryptography android-tools
git clone https://github.com/encikzul/layarkaca-tv.git && cd layarkaca-tv
python -m venv --system-site-packages tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50
tools/venv/bin/python -I tools/tv.py --probe
```

3. Seterusnya ikut **Cara A langkah 3 hingga 7**, dengan dua perbezaan:
   - jalankan skrip dengan `bash` di depan, contohnya `bash tools/disable.sh …` dan `bash tools/theme.sh …`;
   - muat turun dan semak APK dalam Termux:

```bash
curl -LO https://github.com/encikzul/layarkaca-tv/releases/latest/download/layarkaca-v1.0.apk
curl -LO https://github.com/encikzul/layarkaca-tv/releases/latest/download/layarkaca-v1.0.apk.sha256
sha256sum -c layarkaca-v1.0.apk.sha256
APK=layarkaca-v1.0.apk bash launcher/build-and-install.sh
```

## Google TV yang perlu pairing

Sesetengah Google TV tidak membuka port 5555 dengan USB debugging sahaja. Jika `--probe` tamat masa:

1. TV: **Developer options → Wireless debugging** → hidupkan → **Pair device with pairing code**.
2. Laptop (perlukan `adb`; macOS: `brew install android-platform-tools`) atau Termux:

```bash
adb pair 192.168.1.50:<port-pairing> <kod-6-digit>
adb connect 192.168.1.50:<port-sambungan>
adb tcpip 5555
```

Selepas itu `tools/tv.py` berfungsi seperti biasa.

## Tetapan tempat

Semua dalam `config.json` tema anda. Selepas mengubahnya, hantar semula dengan `tools/theme.sh`.

| Tetapan | Contoh | Maksud |
|---|---|---|
| `prayer_zone` | `"SGR01"` | Zon JAKIM untuk waktu solat (jadual di bawah) |
| `weather_location` | `"Petaling"` | Daerah ramalan MET (senarai di bawah) |
| `weather_place` | `"Shah Alam"` | Nama yang dipaparkan pada kad, juga dicari dalam amaran MET |
| `haze_station` | `"Shah Alam"` | Stesen IPU JAS (jadual di bawah) |
| `azan` | `true` | Peringatan masuk waktu; `false` untuk matikan |
| `beep_seconds` | `7` | Berapa saat bunyi peringatan (1 hingga 60) |
| `ramadan_start` | `"2027-02-08"` | Tarikh 1 Ramadan mengikut pengumuman rasmi, jika berbeza daripada kiraan JAKIM |
| `columns` | `5` | Bilangan aplikasi sebaris |

Mahu bunyi azan, bukan bip? Letak `azan.mp3` (dan jika mahu, `azan_subuh.mp3`) dalam folder tema anda,
kemudian hantar semula tema. Fail azan tidak disertakan; pilih rakaman anda sendiri.

### Zon waktu solat

Sumber: [api.waktusolat.app/zones](https://api.waktusolat.app/zones) (data JAKIM).

| Zon | Negeri | Kawasan |
|---|---|---|
| `JHR01` | Johor | Pulau Aur dan Pulau Pemanggil |
| `JHR02` | Johor | Johor Bahru, Kota Tinggi, Mersing, Kulai |
| `JHR03` | Johor | Kluang, Pontian |
| `JHR04` | Johor | Batu Pahat, Muar, Segamat, Gemas Johor, Tangkak |
| `KDH01` | Kedah | Kota Setar, Kubang Pasu, Pokok Sena (Daerah Kecil) |
| `KDH02` | Kedah | Kuala Muda, Yan, Pendang |
| `KDH03` | Kedah | Padang Terap, Sik |
| `KDH04` | Kedah | Baling |
| `KDH05` | Kedah | Bandar Baharu, Kulim |
| `KDH06` | Kedah | Langkawi |
| `KDH07` | Kedah | Puncak Gunung Jerai |
| `KTN01` | Kelantan | Bachok, Kota Bharu, Machang, Pasir Mas, Pasir Puteh, Tanah Merah, Tumpat, Kuala Krai, Mukim Chiku |
| `KTN02` | Kelantan | Gua Musang (Daerah Galas Dan Bertam), Jeli, Jajahan Kecil Lojing |
| `MLK01` | Melaka | SELURUH NEGERI MELAKA |
| `NGS01` | Negeri Sembilan | Tampin, Jempol |
| `NGS02` | Negeri Sembilan | Jelebu, Kuala Pilah, Rembau |
| `NGS03` | Negeri Sembilan | Port Dickson, Seremban |
| `PHG01` | Pahang | Pulau Tioman |
| `PHG02` | Pahang | Kuantan, Pekan, Muadzam Shah |
| `PHG03` | Pahang | Jerantut, Temerloh, Maran, Bera, Chenor, Jengka |
| `PHG04` | Pahang | Bentong, Lipis, Raub |
| `PHG05` | Pahang | Genting Sempah, Janda Baik, Bukit Tinggi |
| `PHG06` | Pahang | Cameron Highlands, Genting Higlands, Bukit Fraser |
| `PHG07` | Pahang | Zon Khas Daerah Rompin, (Mukim Rompin, Mukim Endau, Mukim Pontian) |
| `PRK01` | Perak | Tapah, Slim River, Tanjung Malim |
| `PRK02` | Perak | Kuala Kangsar, Sg. Siput , Ipoh, Batu Gajah, Kampar |
| `PRK03` | Perak | Lenggong, Pengkalan Hulu, Grik |
| `PRK04` | Perak | Temengor, Belum |
| `PRK05` | Perak | Kg Gajah, Teluk Intan, Bagan Datuk, Seri Iskandar, Beruas, Parit, Lumut, Sitiawan, Pulau Pangkor |
| `PRK06` | Perak | Selama, Taiping, Bagan Serai, Parit Buntar |
| `PRK07` | Perak | Bukit Larut |
| `PLS01` | Perlis | SELURUH NEGERI PERLIS |
| `PNG01` | Pulau Pinang | SELURUH NEGERI PULAU PINANG |
| `SBH01` | Sabah | Bahagian Sandakan (Timur), Bukit Garam, Semawang, Temanggong, Tambisan, Bandar Sandakan, Sukau |
| `SBH02` | Sabah | Beluran, Telupid, Pinangah, Terusan, Kuamut, Bahagian Sandakan (Barat) |
| `SBH03` | Sabah | Lahad Datu, Silabukan, Kunak, Sahabat, Semporna, Tungku, Bahagian Tawau (Timur) |
| `SBH04` | Sabah | Bandar Tawau, Balong, Merotai, Kalabakan, Bahagian Tawau (Barat) |
| `SBH05` | Sabah | Kudat, Kota Marudu, Pitas, Pulau Banggi, Bahagian Kudat |
| `SBH06` | Sabah | Gunung Kinabalu |
| `SBH07` | Sabah | Kota Kinabalu, Ranau, Kota Belud, Tuaran, Penampang, Papar, Putatan, Bahagian Pantai Barat |
| `SBH08` | Sabah | Pensiangan, Keningau, Tambunan, Nabawan, Bahagian Pendalaman (Atas) |
| `SBH09` | Sabah | Beaufort, Kuala Penyu, Sipitang, Tenom, Long Pasia, Membakut, Weston, Bahagian Pendalaman (Bawah) |
| `SWK01` | Sarawak | Limbang, Lawas, Sundar, Trusan |
| `SWK02` | Sarawak | Miri, Niah, Bekenu, Sibuti, Marudi |
| `SWK03` | Sarawak | Pandan, Belaga, Suai, Tatau, Sebauh, Bintulu |
| `SWK04` | Sarawak | Sibu, Mukah, Dalat, Song, Igan, Oya, Balingian, Kanowit, Kapit |
| `SWK05` | Sarawak | Sarikei, Matu, Julau, Rajang, Daro, Bintangor, Belawai |
| `SWK06` | Sarawak | Lubok Antu, Sri Aman, Roban, Debak, Kabong, Lingga, Engkelili, Betong, Spaoh, Pusa, Saratok |
| `SWK07` | Sarawak | Serian, Simunjan, Samarahan, Sebuyau, Meludam |
| `SWK08` | Sarawak | Kuching, Bau, Lundu, Sematan |
| `SWK09` | Sarawak | Zon Khas (Kampung Patarikan) |
| `SGR01` | Selangor | Gombak, Petaling, Sepang, Hulu Langat, Hulu Selangor, Shah Alam |
| `SGR02` | Selangor | Kuala Selangor, Sabak Bernam |
| `SGR03` | Selangor | Klang, Kuala Langat |
| `TRG01` | Terengganu | Kuala Terengganu, Marang, Kuala Nerus |
| `TRG02` | Terengganu | Besut, Setiu |
| `TRG03` | Terengganu | Hulu Terengganu |
| `TRG04` | Terengganu | Dungun, Kemaman |
| `WLY01` | Wilayah Persekutuan | Kuala Lumpur, Putrajaya |
| `WLY02` | Wilayah Persekutuan | Labuan |

### Daerah ramalan MET (`weather_location`)

Tulis tepat seperti di bawah. Sumber: [api.data.gov.my](https://developer.data.gov.my/realtime-api/weather).

`Alor Gajah`, `Asajaya`, `Bachok`, `Bagan Datuk`, `Baling`, `Bandar Baharu`, `Barat Daya`, `Bario`, `Batang Padang`, `Batu Pahat`, `Bau`, `Beaufort`, `Belaga`, `Beluran`, `Beluru`, `Bentong`, `Bera`, `Besut`, `Betong`, `Bintulu`, `Bukit Mabong`, `Dalat`, `Daro`, `Dungun`, `Gedong`, `Gombak`, `Gua Musang`, `Hilir Perak`, `Hulu Langat`, `Hulu Perak`, `Hulu Selangor`, `Hulu Terengganu`, `Jasin`, `Jelebu`, `Jeli`, `Jempol`, `Jerantut`, `Johor Bahru`, `Julau`, `Kabong`, `Kalabakan`, `Kampar`, `Kanowit`, `Kapit`, `Kemaman`, `Keningau`, `Kerian`, `Kinabatangan`, `Kinta`, `Klang`, `Kluang`, `Kota Belud`, `Kota Bharu`, `Kota Kinabalu`, `Kota Marudu`, `Kota Setar`, `Kota Tinggi`, `Kuala Kangsar`, `Kuala Krai`, `Kuala Langat`, `Kuala Lumpur`, `Kuala Muda`, `Kuala Nerus`, `Kuala Penyu`, `Kuala Pilah`, `Kuala Selangor`, `Kuala Terengganu`, `Kuantan`, `Kubang Pasu`, `Kuching`, `Kudat`, `Kulai`, `Kulim`, `Kunak`, `Labuan`, `Labuk & Sugut`, `Lahad Datu`, `Langkawi`, `Larut, Matang Dan Selama`, `Lawas`, `Limbang`, `Lingga`, `Lipis`, `Lubok Antu`, `Lundu`, `Machang`, `Manjung`, `Maran`, `Marang`, `Marudi`, `Matu`, `Melaka Tengah`, `Membakut`, `Meradong`, `Mersing`, `Miri`, `Muallim`, `Muar`, `Mukah`, `Nabawan`, `Padang Terap`, `Pakan`, `Pantu`, `Papar`, `Pasir Mas`, `Pasir Puteh`, `Pekan`, `Penampang`, `Pendang`, `Perak Tengah`, `Perlis`, `Petaling`, `Pitas`, `Pokok Sena`, `Pontian`, `Port Dickson`, `Pusa`, `Putatan`, `Putrajaya`, `Ranau`, `Raub`, `Rembau`, `Rompin`, `Sabak Bernam`, `Samarahan`, `Sandakan`, `Saratok`, `Sarikei`, `Sebauh`, `Seberang Perai Selatan`, `Seberang Perai Tengah`, `Seberang Perai Utara`, `Sebuyau`, `Segamat`, `Selama`, `Selangau`, `Semporna`, `Sepang`, `Seremban`, `Serian`, `Setiu`, `Sibu`, `Siburan`, `Sik`, `Simunjan`, `Sipitang`, `Song`, `Sook`, `Sri Aman`, `Subis`, `Tambunan`, `Tampin`, `Tanah Merah`, `Tanah Tinggi Cameron`, `Tangkak`, `Tanjung Manis`, `Tatau`, `Tawau`, `Tebedu`, `Telang Usan`, `Telupid`, `Temerloh`, `Tenom`, `Timur Laut`, `Tongod`, `Tuaran`, `Tumpat`, `Yan`

### Stesen IPU (`haze_station`)

Tulis tepat seperti di bawah. Sumber: [APIMS, JAS](https://eqms.doe.gov.my/APIMS/main).

| Negeri | Stesen (`haze_station`) |
|---|---|
| Johor | `Batu Pahat`, `Kluang`, `Kota Tinggi`, `Larkin`, `Pasir Gudang`, `Pengerang`, `Segamat`, `Tangkak` |
| Kedah | `Alor Setar`, `Kulim Hi-Tech`, `Langkawi`, `Sungai Petani` |
| Kelantan | `Kota Bharu`, `Tanah Merah` |
| Melaka | `Alor Gajah`, `Bandaraya Melaka`, `Bukit Rambai` |
| Negeri Sembilan | `Nilai`, `Port Dickson`, `Seremban` |
| Pahang | `Balok Baru Kuantan`, `Indera Mahkota Kuantan`, `Jerantut`, `Rompin`, `Temerloh` |
| Perak | `Pegoh Ipoh`, `Seri Manjung`, `Taiping`, `Tanjung Malim`, `Tasek Ipoh` |
| Perlis | `Kangar` |
| Pulau Pinang | `Balik Pulau`, `Minden`, `Seberang Jaya`, `Seberang Perai` |
| Sabah | `Keningau`, `Kimanis`, `Kota Kinabalu`, `Politeknik Kota Kinabalu`, `Sandakan`, `Tawau` |
| Sarawak | `Bintulu`, `ILP Miri`, `IPD Serian`, `Kapit`, `Kuching`, `Limbang`, `Miri`, `Mukah`, `Samalaju`, `Samarahan`, `Sarikei`, `Sibu`, `Sri Aman` |
| Selangor | `Banting`, `Johan Setia`, `Klang`, `Kuala Selangor`, `Petaling Jaya`, `Shah Alam` |
| Terengganu | `Besut`, `Kemaman`, `Kuala Terengganu`, `Paka` |
| WP Kuala Lumpur | `Batu Muda`, `Cheras` |
| WP Labuan | `Labuan` |
| WP Putrajaya | `Putrajaya` |

## Apa yang dihantar ke internet

| Ke | Untuk | Apa yang dihantar |
|---|---|---|
| api.waktusolat.app | Waktu solat, sebulan sekali | Kod zon sahaja |
| api.data.gov.my | Ramalan (3 jam sekali) dan amaran (20 minit sekali) | Nama daerah sahaja |
| eqms.doe.gov.my | IPU, 30 minit sekali | Tiada; seluruh senarai stesen dimuat turun |
| bing.com | Gambar tempat, seminggu sekali | Bahasa TV |

Tiada akaun, tiada iklan, tiada penjejakan. Suapan IPU JAS bukan API rasmi: jika JAS mengubahnya, kad IPU
akan tersembunyi sehingga versi baru dikeluarkan.

## Membatalkan semuanya

```bash
tools/restore.sh examples/<model-anda>/debloat.txt com.google.android.apps.tv.launcherx
tools/venv/bin/python -I tools/tv.py 'pm uninstall com.gathin.tvhome'
```

`restore.sh` menghidupkan semula semua yang dimatikan dan mengembalikan skrin utama asal (gantikan
`com.google.android.apps.tv.launcherx` dengan `com.google.android.tvlauncher` pada Android TV lama).

Tidak buat debloat? Hidupkan semula skrin utama asal (dan wizard persediaan, jika dimatikan) **sebelum**
membuang Glass TV, supaya butang Home tidak mati:

```bash
tools/venv/bin/python -I tools/tv.py 'pm enable com.google.android.apps.tv.launcherx'
tools/venv/bin/python -I tools/tv.py 'pm enable com.google.android.tungsten.setupwraith'
tools/venv/bin/python -I tools/tv.py 'pm uninstall com.gathin.tvhome'
```

Selepas
selesai, matikan **USB debugging** jika tidak mahu pembersihan cache automatik.

Pernah bina Glass TV sendiri sebelum ini? APK di Releases ditandatangani dengan kunci lain, jadi buang
yang lama dahulu menggunakan dua arahan di atas, kemudian pasang semula.

## Untuk penerbit repo ini

Sekali sahaja: `bash tools/make-release-key.sh`, dan masukkan empat rahsia yang dipaparkan di
**Settings → Secrets and variables → Actions**. Simpan fail `.jks` dan kata laluannya di tempat selamat;
setiap keluaran mesti ditandatangani dengan kunci yang sama.

Setiap keluaran: naikkan `versionCode` dan `versionName` dalam `launcher/app/build.gradle`, commit, kemudian
`git tag v1.1 && git push --tags`. GitHub Actions membina, menandatangani dan menerbitkan APK.

## Kredit dan lesen

- [Glass TV](https://github.com/cocombee/glass-tv) oleh cocombee, dibina atas
  [CleanYourTV](https://github.com/Gathin23/cleanyourtv) © 2026 Gathin. Lesen MIT, lihat [LICENSE](LICENSE).
- Waktu solat: data JAKIM melalui [api.waktusolat.app](https://api.waktusolat.app) (projek komuniti).
- Cuaca dan amaran: MET Malaysia melalui [data.gov.my](https://data.gov.my).
- IPU: Jabatan Alam Sekitar, [APIMS](https://eqms.doe.gov.my/APIMS/main).
- Fon Outfit: SIL Open Font License.
