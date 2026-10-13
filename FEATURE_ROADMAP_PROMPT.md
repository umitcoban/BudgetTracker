# BudgetTracker — Yeni Özellikler: Araştırma + Geliştirme Promptu

> Bu dosyayı olduğu gibi Claude Code'a (lokal, Opus) ver. Önce `CLAUDE.md` ve `CODEBASE_CONTEXT.md` okunsun.

---

## 0. Rol ve çalışma biçimi

Sen BudgetTracker'ın (yerel çalışan, tek kullanıcılı Android bütçe uygulaması) kıdemli geliştiricisisin. Kullanıcı uygulamayı yalnızca kendisi, lokalde kullanıyor; dağıtım yok. Geliştirmede kapsam sınırı yok, ama aşağıdaki **değişmez kurallar** korunacak.

Çalışma sırası:

1. **Önce oku:** `CLAUDE.md`, `CODEBASE_CONTEXT.md`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `DatabaseModule.kt`, `MonthlyBudgetCalculator`, `ExportDto.kt`, `JsonImportService.kt`.
2. **Sonra araştır (bölüm 4):** Veri kaynaklarını kendi ağından gerçekten çağırıp doğrula. Doğrulamadan koda geçme. Sonuçları `docs/DATA_SOURCES.md` dosyasına yaz (kaynak, adres, örnek yanıt, kota/şart, kırılganlık notu, tarih).
3. **Sonra planla:** Her faz için kısa bir tasarım notu yaz, kullanıcıya göster, onay almadan şema (Room) değişikliğine başlama.
4. **Sonra faz faz geliştir (bölüm 6):** Her faz sonunda `./gradlew :app:testDebugUnitTest`, ardından `./gradlew assembleDebug` çalıştır, sonuçları raporla. Şema değişen fazlarda `MigrationTest` için emülatör gerektiğini kullanıcıya hatırlat (test cihazın uygulama verisini siler).
5. Her faz ayrı commit(ler)le ilerlesin. Yeni özellikleri küçük, geri alınabilir parçalara böl.

Dürüstlük kuralı: Çalıştıramadığın/doğrulayamadığın şeyi "çalışıyor" diye raporlama. Bir kaynak bu ortamdan çalışmıyorsa nedenini yaz.

---

## 1. Değişmez ürün kuralları (CODEBASE_CONTEXT.md'den özet)

- Para her yerde `Long` minor unit. **`Double` ile para/oran hesabı yok.** Kur ve fiyatlar sabit ölçekli tam sayı (mevcut kur ölçeği 10000; fon fiyatları için 1_000_000 ölçeği gerekir, bkz. 3.3).
- Tüm kullanıcıya görünen metinler Türkçe, Kotlin içinde sabit literal (strings.xml'e taşıma yok).
- Yıkıcı işlemler (silme, içe aktarma, geri yükleme) açık onay ister.
- Domain model ≠ Room entity. Her entity için `Mappers.kt`'de `toDomain/toEntity` çifti, **named argument** kullan.
- Room şeması değişirse: sürümü artır, `DatabaseModule.migrations` içine açık migration ekle, `app/schemas/<v>.json` commit'le, `MigrationTest`'e seed-then-validate vakası ekle. Destructive migration yok. Yeni nullable kolonlar için `addNullableColumnIfMissing`.
- Dışa aktarılan her kalıcı veri için: `ExportDto`, `JsonExportService` (`schemaVersion = N`), `JsonImportService` (`dto.schemaVersion > N` kontrolü + `schemaVersion >= k` ile bölüm bazlı okuma) birlikte güncellenir. Eski dosyalar yeni alanları boş/varsayılan okumalı. ProGuard keep kuralları (`core.export.**` vb.) geçerli; yeni paketler için kural ekle.
- Ağ kullanımı: bu belgedeki yeni "veri kaynakları" kuralı için bölüm 2'ye bak. Kişisel/finansal veri **asla** dışarı gönderilmez; giden tek şey public fiyat/kur/endeks sorgusudur.
- Ayrıca: `MonthlyBudgetCalculator` "ayda ne sayılır" mantığının tek sahibidir; ViewModel'e iş kuralı koyma. `getSummariesForMonths` ile çoklu ay. Testler JUnit4 + `runBlocking` + elle yazılmış `Fake*Repository` (Mockito değil).
- Her kalıcı alan için testler: eski JSON (alan yok) → güvenli varsayılan, yeni JSON → round-trip, etkilenen calculator davranışı.

---

## 2. Yapılacak kural güncellemesi (ağ politikası)

Mevcut kural: "Ağ yalnızca kullanıcı tetiklemeli kur sorgusu." Kullanıcı bunun genişletilmesini **onayladı** ("internetten çekebileceğimiz her veriyi otomatik yapabiliriz"). Güncelle:

- `CODEBASE_CONTEXT.md` ve `CLAUDE.md` içindeki ağ kuralını şöyle genişlet: *"Opsiyonel, public veri kaynaklarından (kur, altın, enflasyon, tatil takvimi) arka planda periyodik fiyat/endeks çekimi yapılabilir. Hiçbir kişisel/finansal veri gönderilmez, hesap/oturum yok, çekirdek özellikler ağsız çalışır, her kaynak Ayarlar'dan açılıp kapatılır, her veri için 'son güncelleme zamanı' ve 'elle giriş' alternatifi bulunur."*
- Kaynaklar: `WorkManager` ile günde 1–2 kez, ağ gerekli kısıtıyla; başarısızlık sessiz (son bilinen değer + "Son güncelleme: dün 18:00" rozeti); sunucu hatasında üstel geri çekilme.
- Her kaynak için tek bir `…Source` arayüzü + implementasyon; ayrıştırıcılar **saf fonksiyon** ve örnek yanıtlarla unit test edilir (ağ erişimi olmadan).
- Dosya/ağ kodu composable içinde olmaz; ViewModel→UseCase→Repository katmanlaması korunur.
- R8 için `@Serializable` DTO'lar keep kuralı gerektirir.

---

## 3. Özellik listesi (kullanıcının seçtikleri) ve örnek ekranlar

Aşağıdaki rakamlar **uydurma örneklerdir**, ekran davranışını tarif eder.

### 3.1 Birikim hedefleri (amaç bazlı kumbaralar)
Mevcut `MonthlySavingGoal` aylık hedefin devamı olarak yeni `savings_goals` kavramı.
```
🎯 Tatil – Ağustos 2027            Hedef: 60.000 ₺
██████████░░░░░░░░░░  %48  (28.800 ₺ birikti)
Kalan 31.200 ₺ · 10 ay → ayda 3.120 ₺ ayırmalısın
Şu anki hızınla bitiş: Kasım 2027 ⚠️ 3 ay gecikme
```
- Alanlar: ad, hedef tutar, hedef tarih, mevcut birikim (katkı kayıtlarından türet), önceliği, aktif/pasif.
- Katkılar ayrı kayıt (`goal_contributions`: tarih, tutar, not); hedef birikimi = toplam katkı. Silme için güvenli-silme kuralı.
- Maaş/artan para geldiğinde **öneri** çıkar ("Bu ay artan 7.800 ₺: Tatil'e 3.120, Araba'ya 4.680?"). Onay kullanıcıdan; otomatik para hareketi yok.
- Bir hedef bir varlığa (altın, fon, mevduat) bağlanabilir olsun (opsiyonel; 3.3 ile ilişki).

### 3.2 Acil durum fonu göstergesi
`MonthlyBudgetCalculator` içinde zorunlu gider ortalaması (sabit gider + abonelik + taksit + kredi + kira + fatura; son 3 ay) → "Birikimin kaç ay yeter?"
```
Zorunlu aylık gider: 31.500 ₺ → Acil fon: 78.750 ₺ → 2,5 ay yeter
Hedef 6 ay = 189.000 ₺ · eksik 110.250 ₺
```
- Hangi kalemlerin "acil fon" sayıldığı kullanıcı seçimi (nakit, mevduat, para piyasası fonu, altın?). Varsayılan: nakit + banka + para piyasası fonu.

### 3.3 Altın, döviz ve fon varlıkları (net değer genişlemesi)
Yeni `assets` modeli (tür: ALTIN_GRAM, ALTIN_CEYREK…, DOVIZ, FON, MEVDUAT, DIGER) + alım işlemleri/maliyet.
```
Gram altın      25 gr  × 6.592 ₺      164.800 ₺  (+%4,2 maliyete göre)
USD             1.200 $ × 49,37 ₺       59.244 ₺
Fon (TTE)       3.400 pay × 2,15 ₺       7.310 ₺
Mevduat         120.000 ₺ · %45 · 32 gün kaldı
```
- **Fon fiyatları 6 ondalık** (ör. `0,141840 ₺`, `1.986,607568 ₺`) taşır → fiyat `Long` olarak **1.000.000 ölçeğinde** tutulur; değer = adet × fiyat / ölçek (tam sayı, yuvarlama kuralını belgele). Pay adedi tam sayı (kesirli pay gerekiyorsa ölçekli).
- Net değer ekranı (`NetWorthSnapshot`) otomatik olarak varlıkların güncel değerini toplar; geçmiş snapshot'lar bozulmaz.
- Kâr/zarar = güncel değer − maliyet; yüzde tam sayı aritmetiği.
- Dağılım ekranı (para piyasası / yabancı teknoloji / altın / değişken / hisse) için fonlara "kategori" etiketi ver.
- **Kullanıcının gerçek portföyü örnek test verisi** (hesap/müşteri numarası ASLA saklanmaz, uygulamaya alınmaz):

| Fon kodu | Adet | Güncel tutar | Fiyat (6 hane) | Kâr/zarar |
|---|---|---|---|---|
| GTA (altın) | 2.415 | 3.777,39 | 1,564138 | +279,13 (%7,98) |
| GTL (para piyasası) | 51.628 | 7.322,92 | 0,141840 | +823,08 (%12,66) |
| GUH (yabancı teknoloji hisse) | 7.985 | 3.907,62 | 0,489370 | +408,10 (%11,66) |
| GPI (değişken) | 108 | 2.031,97 | 18,814507 | +143,28 (%7,59) |
| YAY (yabancı teknoloji) | 1 | 1.986,61 | 1.986,607568 | +228,01 (%12,97) |
| AFT (yeni teknolojiler yabancı) | 3.683 | 3.807,91 | 1,033914 | +308,64 (%8,82) |
| KZL (altın katılım) | 137 | 3.784,60 | 27,624846 | +300,63 (%8,63) |
| IIH (hisse senedi) | 39 | 1.218,36 | 31,240080 | −22,80 (−%1,84) |
| PPK (para piyasası katılım) | 1.659 | 3.935,24 | 2,372053 | +437,90 (%12,52) |

Toplam ≈ 31.772,62 ₺, toplam kâr ≈ +2.905,97 ₺ (%10,07), maliyet ≈ 28.866,65 ₺. Bu tabloyu içe aktarma ve değer hesabı testinde fixture olarak kullan (kişisel bilgi olmadan).

**Fon fiyat/veri girişi (otomatik kaynak güvenilir değil — bkz. bölüm 4):**
1. Ekran görüntüsü/fotoğraftan **cihaz içi OCR** (ML Kit, offline) ile tablo okuma + kullanıcı onayı ekranı.
2. Metin yapıştırma (tabloyu kopyala → ayrıştır).
3. CSV/Excel içe aktarma.
4. Elle fiyat güncelleme (haftalık; 9 fon ≈ 1 dk).
5. (Deneysel, kullanıcı onayıyla) TEFAS'ı WebView ile açıp bot doğrulamasını geçme.

### 3.6 Fiş tarama (OCR)
Mevcut ek (attachment) altyapısı üstünde. Fotoğraf → cihaz içi OCR → tutar, tarih, mağaza önerisi → form ön dolu. Kategori önerisi önceki mağaza→kategori eşleşmelerinden öğrenir (kural tablosu). Her zaman kullanıcı onayı.

### 3.8 Büyük harcama planlayıcı
Tarihli tek seferlik/yıllık yükler (kasko, MTV, aidat artışı, düğün hediyesi, beyaz eşya). "N ay sonra X lazım → ayda Y ayır." Nakit akışı takviminde ileri tarihlerinde görünür. "Bu ay rezerv için ayırman gereken toplam" özeti.
```
📅 Kasko        15 Mart 2027   18.000 ₺ → ayda 3.000 ₺ (6 ay)
📅 MTV 2. taksit  Temmuz 2027    9.400 ₺ → ayda 1.567 ₺
```

### 3.10 Zam ve enflasyon planı
Kira/maaş/abonelik için gelecek zam tarihi ve oranı → gelecek ayların projeksiyonu güncellenir; "Temmuz'da kira %40 artarsa nakit akışı?" grafiği. Maaş zaten effective-month kuralıyla tutuluyor: **gelecek zam = gelecek ayda başlayan yeni kural**; bunu bozma.

### 3.11 "Ya şöyle olsaydı?" simülasyonu
Salt-okunur senaryolar (hiçbir kayıt değişmez): krediyi erken kapat, aboneliği iptal et, kategoriyi %X azalt, zam senaryosu. Çıktı: 12 aylık birikim farkı. Mevcut calculator'ların `calculatePayments` saf fonksiyonları üstüne kur.

### 3.13 Anomali ve trend uyarıları
`BudgetWarningRules` sırasına **yeni uyarıları ekle** (sıra sözleşmenin parçası; `BudgetWarningRulesTest` güncellenmeli):
- Kategori geçmiş ortalamanın %X üstünde (mevcut `CATEGORY_SPENDING_SPIKE` var — çakışma yok, genişlet)
- Abonelik zammı (fiyat geçmişinden)
- Olası mükerrer harcama (aynı gün, aynı tutar, benzer ad)
- Olumlu trend ("yakıt 3 aydır düşüyor") — düşük öncelik

### 3.14 Yıl sonu raporu
Toplam gelir/gider/birikim, en pahalı ay, en çok harcanan kategoriler, abonelik toplamı, faiz/komisyon, geçen yıla göre değişim ve **enflasyon düzeltmeli** karşılaştırma. Mevcut PDF raporuna ek sayfa (`PdfCharts` helper'ları; JVM testinde çalışmaz, emülatörde render ile doğrula).

### 3.15 Enflasyona göre gerçek (reel) değer
Harcama ve maaşın reel değeri (bugünün parasıyla), reel artış yüzdesi. Veri: bölüm 5'teki manuel enflasyon girişi.

### 3.16 Widget ve hızlı ekleme
Glance/AppWidget: "Ekim · Kalan 8.450 ₺ · %68 harcandı" + hızlı ekle butonları (Market/Yemek/…); uygulama kısayolu (uzun basış → "Harcama ekle"); tutar yazıp kaydeden minimal ekran. Widget verisi `MonthlyBudgetCalculator`'dan, kendi mantığı yok.

### 3.17 Tekrarlayan harcama önerisi
Aynı ad/tutar yakınlığında ≥3–4 ay tekrarlayan harcamalar için "Abonelik/Sabit gider yap?" önerisi. Reddedilen öneri bir daha çıkmasın (kural tablosu). Kabul edilirse mevcut `Subscription`/`FixedExpense` akışına yönlendir; **sessizce kayıt oluşturma**.

### 3.18 Biyometrik kilit / PIN
`BiometricPrompt` + PIN yedek; kilitleme gecikmesi (hemen/1 dk/5 dk); son uygulamalar ekranında içerik gizleme (`FLAG_SECURE` seçeneği). Ayar `SettingsDataStore`'da. Yedek/içe aktarma akışlarını kilitle birlikte düşün (kilit ayarı yedeğe girmesin).

### 3.20 Akıllı nakit uyarısı
Mevcut nakit akışı + 09:00 hatırlatıcı altyapısı üstüne: "20 Ekim kart ekstresi 18.400 ₺, hesapta tahmini 14.100 ₺ → 4.300 ₺ açık, maaş 28 Ekim'de". En sıkışık gün tahmini. Bildirim, `PaymentReminderScheduler` ile.

---

## 3b. Ek özellikler (konuşmada eklendi)

### Banka ekstresi içe aktarma ve karşılaştırma
PDF/CSV/Excel banka ekstresi seç (SAF) → cihazda ayrıştır → uygulamadaki harcamalarla eşleştir:
```
Ekim 2026 · 47 işlem
✅ 39 eşleşti · ➕ 6 yeni (öneri kategori) · ❓ 2 uyumsuz
Uygulama toplamı 31.870 ₺ · ekstre 32.110 ₺ · fark 240 ₺
```
- Eşleştirme: tutar tam eşit + tarih ±2 gün + metin benzerliği; her eşleşme kullanıcı tarafından onaylanabilir/düzeltilebilir.
- Yeni işlemleri **toplu/tek tek onayla** → `Expense` oluştur (kopya engeli: mevcut kayıtlarla aynı gün+tutar+benzer ad uyarısı).
- Banka bazlı ayrıştırıcı şablonları (kullanıcıdan gerçek örnek dosya/metin iste, **kişisel bilgiyi sildir**). Ayrıştırılamayan satırlar "Okunamadı" olarak listelenir.
- Kural tablosu: "A101 → Market" öğrenir.
- Ekstre dosyası ve ham metin kalıcı saklanmaz; yalnızca kullanıcının onayladığı işlemler kaydedilir.

### Bildirimlerden harcama yakalama (isteğe bağlı, düşük öncelik)
`NotificationListenerService`; yalnızca kullanıcının seçtiği banka/cüzdan uygulamaları (beyaz liste); regex şablonlarıyla tutar/mağaza/tarih çıkar → **taslak kutusu** (onay olmadan gerçek harcama olmaz). Ham bildirim metni saklanmaz. **SMS okuma kapsam dışı** (kullanıcı vazgeçti).

### Fiyat / döviz / altın / fon bildirimleri
Hedef fiyat alarmı (ör. gram altın ≥ X), vade yaklaşan mevduat, haftalık özet (Pazar 20:00), döviz hareketi. Hepsi yerel bildirim; ayarlardan tek tek kapanabilir.

### Ödeme öncesi uyarılar
- Döviz aboneliği ödemesine X gün kala güncel kurla TRY karşılığı ve geçen aya göre fark.
- Ödeme günü hafta sonu/resmi tatile denk geliyorsa "bankada Pazartesi'ye kayar" bilgisi (tatil takvimi, bölüm 4).
- Ekstre/kredi ödemesi öncesi hesapta yeterli nakit var mı kontrolü (3.20 ile ortak motor).

---

## 4. Veri kaynakları — araştırma bulguları ve senin doğrulayacakların

Aşağıdakiler bir bulut sandbox'ından, **2026-10-13** tarihinde denendi. Kendi ağından yeniden doğrula; farklı sonuç çıkabilir (özellikle TEFAS: veri merkezi IP'si engellenmiş olabilir). Sonuçları `docs/DATA_SOURCES.md`'ye yaz.

| İhtiyaç | Kaynak | Sandbox sonucu | Not |
|---|---|---|---|
| Döviz kuru (ana) | TCMB günlük kur XML: `https://www.tcmb.gov.tr/kurlar/today.xml` | ✅ 200, 22 para birimi, ForexBuying/Selling, BanknoteBuying/Selling | Resmi, anahtarsız, kalıcı. Altın içermez. Hafta sonu/tatilde son iş günü bülteni. `Tarih`, `Bulten_No` alanları var. |
| Döviz kuru (yedek) | Frankfurter: `https://api.frankfurter.dev/v1/latest?base=USD&symbols=TRY` | ✅ `{"rates":{"TRY":49.367}}` | `api.frankfurter.app` yönlendiriyor; yönlendirmeyi takip et veya yeni adresi kullan. Mevcut `ExchangeRateService` zaten kullanıyor → adresi kontrol et. |
| Döviz (ek yedek) | ECB `data-api.ecb.europa.eu` | ✅ TRY paritesi | Anahtarsız. |
| Altın (sikke dahil) | Truncgil: `https://finans.truncgil.com/today.json`, `/v3/today.json` | ✅ (ama `/v4/today.json` boş döndü, HTTP/2 hatası) | Gram, çeyrek, yarım, tam, Cumhuriyet, ata, 14/18 ayar, ons… Değerler **Türkçe biçimli string** (`"6.591,46"`) → `Long` kuruşa tam sayı ayrıştır. Resmi değil; kullanım şartı/kota doğrulanmadı; biçim değişebilir. |
| Altın (ons, doğrulama) | `https://api.gold-api.com/price/XAU` | ✅ USD ons fiyatı (4.154,70) | Çapraz kontrol: `ons / 31,1035 × USDTRY ≈ 6.594 ₺/gr`, Truncgil `6.591–6.592` ile uyuştu. Truncgil çökerse **gram altın = ons × kur / 31,1035** yedeği. Sikke primi hesaplanamaz. |
| Altın (diğer) | genelpara, goldprice.org, metals.live | ❌ Cloudflare/403/anahtar | Kullanma. |
| Enflasyon (TÜFE) | **TCMB "Consumer Prices" HTML tablosu**: `https://www.tcmb.gov.tr/wps/wcm/connect/EN/TCMB+EN/Main+Menu/Statistics/Inflation+Data/Consumer+Prices` | ✅ Tablo okunuyor: `MM-YYYY, yıllık %, aylık %` | Anahtarsız ama **resmi API değil, HTML kazıma** → kırılgan. Kullanıcı elle girişi birincil, bu kaynak **opsiyonel yardımcı** (bölüm 5). |
| Enflasyon (EVDS) | `https://evds3.tcmb.gov.tr/igmevdsms-dis/series=TP.FG.J0&...&type=json` | ⚠️ 403 `Required request header 'key'` | **Kullanıcı API anahtarı almayacak.** Atla. |
| Enflasyon (TÜİK) | `veriportali.tuik.gov.tr` | ⚠️ JS uygulaması, makine-okunur API bulunamadı | Araştır; bulamazsan bırak. |
| Enflasyon (yıllık, referans) | Dünya Bankası API | ✅ yıllık veri (2025: %34,88) | Aylık değil, işe yaramaz. |
| Resmi tatiller | `https://date.nager.at/api/v3/PublicHolidays/2026/TR` | ✅ JSON | Yıllık önbellek; dini bayram tarihleri doğrulanmalı (resmi takvimle karşılaştır). |
| Fon fiyatları | TEFAS (`tefas.gov.tr`, `fundturkey.com.tr`) | ❌ F5 `TSPD` JS bot doğrulaması; eski `BindHistoryInfo` 404 "Method not found or disabled" | **Yerelde dene**: ev IP'sinden/tarayıcı davranışıyla geçiyor mu? Python kütüphaneleri (`tefas-crawler`, `tefas`, `Tefas-API` Playwright tabanlı, `fonlar-mcp`) eski olabilir; güncelliğini doğrula. Çalışmıyorsa **otomatik fon çekimi yapma**; 3.3'teki OCR/yapıştırma/CSV/elle yolunu kur. |
| Fon (diğer) | İş Yatırım fon API | ❌ 401 | |
| Fon (diğer) | Fintables | ❌ Cloudflare | |
| Fon (diğer) | fonbulucu/bigpara/doviz.com/KAP | ❓ beklenen adreslerde veri yok (404) | Gerçek veri uç noktalarını araştır. |
| Kripto | CoinGecko | ❌ 429 (kota) | Kullanıcı kripto tutuyorsa elle giriş; otomatik opsiyonel. |
| Kripto | Binance | ❌ 451 (bölge) | |
| Politika faizi, mevduat faizi, akaryakıt | — | ❓ denenmedi/bulunamadı | Resmi, anahtarsız bir kaynak bul; yoksa kapsam dışı/elle. |

**Senden beklenenler (Opus):**
1. Yukarıdaki her satırı kendi ağından yeniden dene; çalışanlar için gerçek örnek yanıtı `app/src/test/resources/` altına (kişisel veri olmadan) fixture olarak kaydet.
2. TEFAS için: (a) tarayıcıdan geçen gerçek istek yapısını (DevTools) analiz et, (b) uygulama içinde WebView ile geçmek mümkün mü dene (çalışırsa kullanıcıya göster, **onaysız ekleme**).
3. Her kaynakta kullanım şartı/robots/oran sınırını incele; kırılgan kazıma gerektirenleri işaretle.
4. Truncgil/TCMB sayfa biçimi değişirse uygulamanın **çökmemesi** ve eski değerle devam etmesi için dayanıklı ayrıştırıcı + testler.

---

## 5. Enflasyon: manuel giriş tasarımı (kullanıcı kararı)

Kullanıcı API anahtarı **almayacak**. Aylık TÜFE'yi **elle girecek**. Kararlar:
- 12 aylık seed veri ilk kurulumda yüklensin (aşağıdaki tablo, TCMB sayfasından okundu — resmi kaynakla teyit et).
- **Aylık hatırlatıcı** olsun (ayın 3'ü, TÜİK açıklama günü civarı: "Enflasyonu gir").
- **Hem aylık hem yıllık oran** girilebilsin (ikisi de kullanıcı girişi).

Veri modeli (öneri):
```
inflation_rates
  month          YearMonth (benzersiz)
  monthlyRateBp  Long   (aylık %, baz puan: %1,84 → 184)
  annualRateBp   Long?  (yıllık %, baz puan: %29,73 → 2973)
  note           String?
```
- Aynı ay yeniden girilirse güncelle (kopya yok).
- Reel değer / kümülatif hesaplar **aylık oranlardan** tam sayı aritmetiğiyle bileşik hesaplanır; yıllık oran varsa tutarlılık kontrolü: bileşik 12 aylık ile girilen yıllık ±0,1 puan dışındaysa "Rakamları kontrol et" uyarısı.
- Eksik ay varsa yıllık hesap yapılmaz, "dönemsel" etiketiyle eldeki aylar gösterilir. Henüz girilmemiş son ay için son bilinen oran "tahmini" etiketiyle kullanılır.
- Opsiyonel: TCMB HTML tablosunu okuyup "şu aylar eksik, ekleyelim mi?" önerisi (kırılgan yardımcı; hata olursa sessizce elle girişe düş).
- Room v16 → v17 (diğer şema değişiklikleriyle birleştirilebilir; aşağıdaki faz planını gör), JSON şema +1, `MigrationTest` vakası.

Seed (aylık %, yıllık %):

| Ay | Aylık | Yıllık |
|---|---|---|
| 2026-09 | 1,84 | 29,73 |
| 2026-08 | 1,84 | 31,51 |
| 2026-07 | 1,78 | 31,75 |
| 2026-06 | 0,99 | 32,11 |
| 2026-05 | 1,71 | 32,61 |
| 2026-04 | 4,18 | 32,37 |
| 2026-03 | 1,94 | 30,87 |
| 2026-02 | 2,96 | 31,53 |
| 2026-01 | 4,84 | 30,65 |
| 2025-12 | 0,89 | 30,89 |
| 2025-11 | 0,87 | 31,07 |
| 2025-10 | 2,55 | 32,87 |

---

## 6. Önerilen faz sırası

Her faz: tasarım notu → onay → implementasyon → unit testler → `assembleDebug` → raporla.

| Faz | İçerik | Neden bu sırada |
|---|---|---|
| 0 | Kural güncellemesi (bölüm 2), `docs/DATA_SOURCES.md` araştırması | Her şeyin temeli |
| 1 | Enflasyon manuel giriş + seed + hatırlatıcı + reel değer hesabı (3.15) | Küçük, bağımsız, diğer fazları besler |
| 2 | Varlıklar: altın/döviz/fon/mevduat + net değer entegrasyonu + fon OCR/yapıştırma/CSV içe aktarma (3.3), kur/altın otomatik çekim (TCMB XML + Truncgil + ons doğrulama) | Kullanıcı için en değerli |
| 3 | Birikim hedefleri (3.1) + acil durum fonu (3.2) | Faz 2 varlıklarına bağlanır |
| 4 | Büyük harcama planlayıcı (3.8) + zam planı (3.10) + tatil takvimi kaydırma + ödeme öncesi uyarılar + akıllı nakit uyarısı (3.20) | Nakit akışı motoru |
| 5 | Simülasyon (3.11) + anomali/trend uyarıları (3.13) + tekrarlayan öneri (3.17) | Salt-okunur analiz |
| 6 | Banka ekstresi içe aktarma/karşılaştırma + fiş OCR (3.6) | Ayrıştırıcı + OCR altyapısı ortak |
| 7 | Yıl sonu raporu (3.14), widget + hızlı ekleme (3.16), biyometrik kilit (3.18) | Yüzey/UX |
| 8 | Fiyat alarmları, bildirimden harcama yakalama (isteğe bağlı) | En sona, en riskli |

Room sürümü: faz bazında artır (17, 18, …), **her sürüm için** migration + schema JSON + `MigrationTest`. JSON şema sürümünü her şema fazında +1.

---

## 7. Her faz için "bitti" tanımı

- [ ] Cross-layer checklist (CODEBASE_CONTEXT.md §"Cross-Layer Change Checklist") tamam.
- [ ] Yeni iş mantığı saf fonksiyon/calculator'da, unit testli (JUnit4 + Fake repo).
- [ ] Para/oran hesabında `Double` yok (`grep` ile kontrol et).
- [ ] Eski JSON → yeni sürüm içe aktarma testi, yeni JSON round-trip testi.
- [ ] Yıkıcı işlemler onaylı; silme güvenli (bağlı kayıt varsa engel).
- [ ] `git diff --check`, `./gradlew :app:testDebugUnitTest`, `./gradlew assembleDebug` temiz.
- [ ] Release derlemesi (R8) en az faz 2, 6, 8 sonunda denenmiş (`./gradlew clean test assembleRelease`); DTO keep kuralları kontrol.
- [ ] `CODEBASE_CONTEXT.md` ve gerekirse `CLAUDE.md` güncel (yeni kurallar, sürüm numaraları, şema geçmişi).
- [ ] Türkçe metinler doğru karakterlerle (ç, ğ, ı, ö, ş, ü).

---

## 8. Kullanıcıya sorulacak açık noktalar (başlamadan önce)

1. Hangi bankanın ekstresi/bildirimi? (ayrıştırıcı şablonları için gerçek, kişisel bilgisi silinmiş örnek iste.)
2. Fon portföyü hangi aracı kurumdan? Dışa aktarma (CSV/Excel) seçeneği var mı?
3. TEFAS WebView denemesi için onay var mı (deneysel, kırılgan)?
4. Kripto takip edilecek mi?
5. Widget için hızlı ekle butonlarında hangi kategoriler?

Bilmediğin/doğrulayamadığın bir şeyi uydurma; kullanıcıya sor veya `docs/DATA_SOURCES.md`'ye "doğrulanamadı" diye yaz.
