# Veri kaynakları (public, anahtarsız)

Doğrulama tarihi: **2026-10-13**, kullanıcının kendi ağından (macOS curl) ve Android emülatöründen
(uygulamanın `HttpFetcher`'ı ile). Örnek yanıtlar `app/src/test/resources/datasources/` altında
fixture olarak duruyor (kişisel veri yok); ayrıştırıcı testleri `SourceParsersTest`.

Genel kurallar (CODEBASE_CONTEXT.md → "Network Policy"):

- Yalnızca herkese açık fiyat / kur / endeks / tatil verisi **indirilir**; uygulama hiçbir kişisel
  veya finansal veri göndermez, hesap/oturum yoktur.
- Her kaynak Ayarlar → Menü → Tercihler → *Veri kaynakları* altından açılıp kapatılır.
- Başarısızlık sessizdir: son bilinen değer ve "Son güncelleme: …" etiketi kalır; her fiyat için
  elle giriş mümkündür. Ağ/5xx hatasında WorkManager üstel geri çekilme ile en fazla 3 kez dener.
- Periyot: fiyatlar 12 saatte bir (ağ kısıtıyla), tatiller 14 günde bir; ayrıca "Şimdi güncelle".
- `User-Agent` **yalnızca ASCII** olmalı: TCMB güvenlik duvarı ASCII dışı karakter içeren
  User-Agent'a 200 + "Sayfa Görüntülenemedi" HTML'i döndürüyor (emülatörde yakalandı, düzeltildi).

## Kullanılanlar

| Veri | Kaynak / adres | Sonuç | Format, ayrıştırma | Kırılganlık |
|---|---|---|---|---|
| Döviz kuru (ana) | TCMB günlük bülten `https://www.tcmb.gov.tr/kurlar/today.xml` | ✅ 200, ~22 para birimi | XML; `ForexBuying` (yoksa `BanknoteBuying`) ÷ `Unit` (JPY 100 birim). Regex tabanlı, JVM'de test edilebilir. `Tarih="13.10.2026"`, `Bulten_No`. | Resmi, kalıcı. Hafta sonu/tatilde son iş günü bülteni. robots.txt yalnızca arama sonuçlarını kapatıyor. |
| Altın (sikke dahil) + döviz yedeği | Truncgil `https://finans.truncgil.com/today.json` | ✅ 200 (`/v3/today.json` İngilizce anahtarlarla aynı veri; `/v4/today.json` boş gövde döndü) | JSON; Türkçe biçimli string (`"6.583,86"`) → `TurkishNumbers` ile tam sayıya. `gram-altin`, `ceyrek-altin`, `yarim-altin`, `tam-altin`, `cumhuriyet-altini`, `ata-altin`, `resat-altin`, `22/18/14-ayar`, `gumus`; `ons` USD cinsinden → aynı dosyanın USD alışıyla TL'ye çevrilir. Değerleme "Alış" fiyatıyla. | **Resmi değil**; kullanım şartı / kota yayınlanmamış (robots.txt yok). Anahtar adı değişirse ayrıştırıcı boş liste döner, eski fiyat kalır. |
| Altın (yedek / çapraz kontrol) | `https://api.gold-api.com/price/XAU` | ✅ `{"price":4162.5,…}` USD/ons | Truncgil gram fiyatı alınamazsa: `gram = ons × USDTRY / 31,1034768`. Test: Truncgil 6.583,86 ↔ yedek ≈ 6.595 (%0,2 fark). | Sikke primi hesaplanamaz; yalnızca gram. |
| Döviz (mevcut özellik) | Frankfurter `https://api.frankfurter.dev/v2/rate/USD/TRY` | ✅ `{"rate":49.338}` | Harcama/abonelik formundaki "kur getir" ve abonelik projeksiyonu (`ExchangeRateService`, değişmedi). `v1/latest` de çalışıyor. | ECB kaynaklı, gün içi gecikmeli. |
| Resmi tatiller | Nager.Date `https://date.nager.at/api/v3/PublicHolidays/{yıl}/TR` | ✅ 2026 için 27 kayıt (yarım günler dahil) | JSON `date`, `localName`. Bu yıl + gelecek yıl önbelleğe (`public_holidays`) yazılır. | Dini bayram tarihleri resmi takvimle karşılaştırılmalı; yalnızca "bankada X'e kayar" bilgi notu üretir, tutarları etkilemez. |
| Enflasyon (yardımcı) | TCMB "Consumer Prices" HTML `https://www.tcmb.gov.tr/wps/wcm/connect/EN/TCMB+EN/Main+Menu/Statistics/Inflation+Data/Consumer+Prices` | ✅ 200, tablo okunuyor | Sütun sırası: `MM-YYYY`, **yıllık %**, **aylık %**. Yalnızca Enflasyon ekranındaki bulut düğmesiyle, kullanıcı tetikleyince okunur; eksik/farklı aylar onay listesinde gösterilir, seçilenler kaydedilir. | **HTML kazıma → kırılgan.** Okunamazsa "TCMB sayfası okunamadı, elle gir" mesajı. Birincil yol elle giriş (+ ayın 3'ünden itibaren hatırlatıcı). |
| Kripto (isteğe bağlı, varsayılan kapalı) | CoinGecko `https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum&vs_currencies=try` | ✅ 200 (bu ağdan; bulut sandbox'ta 429 almıştı) | JSON; BTC, ETH → TL. | Anahtarsız kota düşük; 429'da sessizce eski değer. |

Başlangıç TÜFE verisi (Ekim 2025 – Eylül 2026, aylık / yıllık) TCMB tablosuyla birebir aynı ve
12 aylık bileşik hesap %29,72 ↔ yayımlanan %29,73 (tolerans 0,1 puan içinde).

## Kullanılmayanlar

| Kaynak | Sonuç (2026-10-13) | Karar |
|---|---|---|
| TEFAS fon fiyatları (`tefas.gov.tr`) | ❌ `FonAnaliz.aspx` → "The requested URL was rejected" (F5 bot koruması); `api/DB/BindHistoryInfo` → 404 "Method not found or disabled!" | Otomatik fon çekimi **yok**. Fon fiyatları: ekran görüntüsü/fotoğraf OCR (cihaz içi ML Kit), PDF/Excel/CSV, metin yapıştırma veya elle giriş (Varlıklarım → Fon İçe Aktar). WebView ile bot doğrulamasını aşma denemesi yapılmadı: kırılgan ve kullanıcı onayı gerektiriyor. |
| EVDS (TCMB) | 403, `key` başlığı ister | Kullanıcı API anahtarı almayacak → kullanılmıyor. |
| TÜİK veri portalı | JS uygulaması, makine-okunur uç bulunamadı | Kullanılmıyor. |
| Dünya Bankası | Yalnızca yıllık | Aylık reel hesap için yetersiz. |
| genelpara, goldprice.org, metals.live, İş Yatırım, Fintables, Binance | 403 / Cloudflare / 401 / 451 (bulut denemesi) | Kullanılmıyor. |
| Politika faizi, mevduat faizi, akaryakıt | Anahtarsız resmi kaynak bulunamadı | Kapsam dışı; mevduat faizi varlık kaydında elle girilir. |
