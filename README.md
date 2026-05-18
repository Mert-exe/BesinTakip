# 🥗 NutriTrack (BesinTakip): Akıllı Beslenme ve Sağlık Yönetim Sistemi

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-orange.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://www.android.com/)
[![Material3](https://img.shields.io/badge/UI-Material%203-blue.svg)](https://m3.material.io/)
[![ML Kit](https://img.shields.io/badge/AI-Google%20ML%20Kit-red.svg)](https://developers.google.com/ml-kit)

**NutriTrack**, modern mobil teknolojiler ve yapay zeka algoritmalarını harmanlayarak kullanıcıların beslenme alışkanlıklarını bilimsel ve pratik bir şekilde takip etmelerini sağlayan kapsamlı bir Android uygulamasıdır. 

---

## 🌟 Öne Çıkan Özellikler

### 📸 Yapay Zeka Destekli Besin Tanıma
Google'ın **ML Kit** altyapısını kullanan gelişmiş görüntü işleme modülü sayesinde, tabağınızdaki yemeği saniyeler içinde tanımlayın. 
- **ROI (Region of Interest):** Akıllı odaklama ile nesne tespiti.
- **CanHub Image Cropper:** Görselleri API sorgusuna uygun hale getirmek için profesyonel kırpma araçları.

### 🍎 Bilimsel Besin Verileri (USDA Entegrasyonu)
**USDA (Amerika Birleşik Devletleri Tarım Bakanlığı)** FoodData Central API'si ile binlerce besinin makro ve mikro değerlerine anlık erişim.
- **Dinamik Hesaplama:** Gram bazlı porsiyon yönetimi ile gerçek zamanlı kalori, protein, karbonhidrat ve yağ analizi.
- **Otomatik Birim Dönüşümü:** Emperyal birimlerin (oz) otomatik olarak metrik sisteme (g) dönüştürülmesi.

### ⚖️ Sağlık Analiz Motoru
Kişiselleştirilmiş sağlık hedefleri belirlemek için gelişmiş hesaplama araçları:
- **BMI (Vücut Kitle Endeksi):** Vücut tipinizin anlık analizi.
- **BMR (Bazal Metabolizma Hızı):** Harris-Benedict formülü ile bazal enerji ihtiyacınızın tespiti.
- **Hedef Belirleme:** Kilo verme veya koruma modları ile dinamik günlük kalori limiti atama.

### 💧 Su Takibi ve Hatırlatıcılar
Vücudunuzun hidrasyon dengesini korumak için sezgisel su takip paneli.
- **Hızlı Ekleme:** 100ml, 250ml ve 500ml butonları ile kolay veri girişi.
- **WorkManager:** Gün sonunda besin girişi yapmanızı hatırlatan akıllı bildirimler.

### 🗺️ Aktivite Alanları (Google Maps)
Sağlıklı yaşamın bir parçası olan fiziksel aktiviteyi teşvik etmek için çevrenizdeki parkları ve yürüyüş alanlarını harita üzerinde görüntüleyin.

---

## 🛠 Teknik Mimari ve Teknoloji Yığını

Uygulama, sürdürülebilirlik ve performans odaklı **MVVM (Model-View-ViewModel)** mimarisi üzerine inşa edilmiştir.

| Katman | Teknoloji | Açıklama |
| :--- | :--- | :--- |
| **Dil** | Kotlin | Modern, güvenli ve performanslı kodlama. |
| **UI** | Material Design 3 | Responsive, modern ve kullanıcı dostu arayüz. |
| **Veritabanı** | Room | SQLite tabanlı lokal veri kalıcılığı. |
| **Ağ** | Retrofit & OkHttp | REST API haberleşmesi ve JSON serileştirme. |
| **Görsel İşleme** | Google ML Kit | Cihaz üzerinde (On-device) yapay zeka analizi. |
| **Arka Plan** | WorkManager | Periyodik bildirim ve veri senkronizasyon işleri. |
| **Harita** | Google Maps SDK | Lokasyon bazlı servisler ve aktivite alanları. |

---

## 🚀 Kurulum ve Çalıştırma

### 1. Hazırlık
Android Studio (Hedgehog veya üstü) sürümünün yüklü olduğundan emin olun.

### 2. Klonlama
```bash
git clone https://github.com/Mert-exe/BesinTakip.git
```

### 3. API Yapılandırması
Uygulama USDA API üzerinden veri çekmektedir. Kendi anahtarınızı kullanmak için:
1. [USDA API](https://fdc.nal.usda.gov/api-key-signup.html) üzerinden ücretsiz bir anahtar alın.
2. `local.properties` dosyasını açın ve aşağıdaki satırı ekleyin:
   ```properties
   USDA_API_KEY="Sizin_API_Anahtarınız"
   ```

### 4. Derleme
- Projeyi Gradle ile senkronize edin.
- Cihazınızı bağlayın veya Emülatör üzerinden `Run` butonuna basın.

---

## 📋 Proje Yapısı

```text
app/src/main/java/com/mertevran/besintakip/
├── data/
│   ├── local/      # Room Database, DAO ve Entity tanımları
│   ├── model/      # Veri modelleri ve POJO'lar
│   └── remote/     # Retrofit Interface ve API Client
├── ui/
│   ├── main/       # Ana ekran ve Liste yönetimi
│   ├── camera/     # Kamera entegrasyonu ve ML Kit
│   ├── health/     # BMI/BMR hesaplama mantığı
│   ├── map/        # Lokasyon bazlı servisler
│   └── history/    # Geçmiş kayıtların analizi
└── worker/         # Arka plan servisleri ve bildirimler
```

---

## 🤝 Katkıda Bulunma
Katkılarınızı bekliyoruz! Hata bildirimleri veya özellik talepleri için lütfen bir `Issue` açın veya bir `Pull Request` gönderin.

---
*Geliştirici: [Mert Evran](https://github.com/Mert-exe)*  
*Bu proje modern mobil programlama pratikleri ve veri yönetimi prensipleri ile akademik standartlarda geliştirilmiştir.*
