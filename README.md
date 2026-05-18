# BesinTakip: Akıllı Besin Analiz ve Takip Sistemi

## Proje Özeti
BesinTakip, modern mobil teknolojileri kullanarak kullanıcıların beslenme alışkanlıklarını dijitalleştirmeyi amaçlayan kapsamlı bir Android uygulamasıdır. Sistem, Google ML Kit tabanlı yapay zeka nesne tanıma teknolojisi, USDA (Amerika Birleşik Devletleri Tarım Bakanlığı) FoodData Central API entegrasyonu ve porsiyon bazlı dinamik kalori hesaplama algoritmalarını bir araya getirerek hassas bir besin takip deneyimi sunmaktadır.

## Temel Özellikler

### Yapay Zeka Destekli Nesne Tanıma
Google ML Kit kütüphanesi kullanılarak geliştirilen görüntü işleme modülü, ROI (Region of Interest) teknolojisi ile kamera görüntüsü üzerindeki besinleri tespit eder. Tespit edilen nesneler, güven eşiği filtrelemesinden geçirilerek API sorgusu için optimize edilir.

### Dinamik Porsiyon ve Birim Yönetimi
Uygulama, USDA API'den gelen verileri işlerken dinamik birim dönüşümü gerçekleştirir. 'Ounce' (oz) birimiyle gelen veriler otomatik olarak metrik sisteme (Gram) dönüştürülür. Kullanıcının girdiği porsiyon miktarına göre makro ve mikro besin değerleri gerçek zamanlı olarak yeniden hesaplanır.

### Depolama ve Veri Optimizasyonu
Veri kalıcılığı Room Persistence Library ile sağlanmaktadır. Veritabanında görsellerin kendisi yerine dosya yolları saklanarak performans optimizasyonu sağlanmıştır. Veri bütünlüğünü korumak adına, bir öğün kaydı silindiğinde ilişkili fiziksel .jpg dosyası da dosya sisteminden (Internal Storage) kalıcı olarak temizlenmektedir.

### Dinamik Uyarı ve Görsel Geri Bildirim Sistemi
Uygulama, kullanıcının profil ayarlarında belirlediği günlük kalori limitini anlık olarak takip eder. Limit aşımı durumunda, arayüz bileşenleri (ProgressBar, CardView arka planları ve metin renkleri) programatik olarak uyarı moduna (Kırmızı tema) geçiş yaparak kullanıcıyı görsel olarak bilgilendirir.

## Teknik Altyapı

*   **Programlama Dili:** Kotlin
*   **Mimari Yapı:** MVVM (Model-View-ViewModel)
*   **Veritabanı:** Room Persistence Library (SQLite tabanlı)
*   **Ağ Katmanı:** Retrofit & OkHttp
*   **Kamera ve Görüntüleme:** CameraX API ve Glide
*   **Arka Plan İşlemleri:** WorkManager (Günlük hatırlatıcı servisler)
*   **Harita Entegrasyonu:** OpenStreetMap (osmdroid) / Google Haritalar altyapısı
*   **Analiz Motoru:** Google ML Kit Object Detection & Labeling

## Akademik Gereksinim Karşılaması

Sistem, modern Android geliştirme standartlarına uygun olarak aşağıdaki bileşenleri içermektedir:

*   **Ekran Tasarımları:** 6 temel fonksiyonel ekran (Main, Camera, Details, History, Map, Profile).
*   **Modern UI/UX:** Material Design 3 prensipleri, Dark Mode desteği ve Responsive Layout.
*   **Gelişmiş Bileşenler:** RecyclerView (Dinamik listeleme), CardView (Gruplandırma), LinearProgressIndicator (Veri görselleştirme).

## Kurulum ve Çalıştırma

### 1. Projenin Klonlanması
```bash
git clone https://github.com/Mert-exe/BesinTakip.git
```

### 2. API Yapılandırması
Uygulamanın besin verilerini çekebilmesi için geçerli bir USDA API anahtarına ihtiyacı vardır. Alınan anahtar `local.properties` dosyasına veya `BuildConfig` yapılandırmasına aşağıdaki formatta eklenmelidir:
```properties
USDA_API_KEY=YOUR_API_KEY_HERE
```

### 3. Derleme ve Çalıştırma
*   Projeyi Android Studio ile açın.
*   Gradle senkronizasyonunun tamamlanmasını bekleyin.
*   'Run' butonuna basarak fiziksel cihaz veya emülatör üzerinde uygulamayı başlatın.

---
*Bu proje, akıllı mobil sistemler ve veri yönetimi prensipleri doğrultusunda geliştirilmiştir.*
