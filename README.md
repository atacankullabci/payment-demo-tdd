# payment-demo — Hesaplar Arası Para Transferi Katası

TDD ile, JUnit 5 kullanarak yazılmış eşzamanlı (concurrent), idempotent ve deadlock'suz bir para transferi servisi.

## Özet: Kazanımlar

| Konu | Öğrendiğim |
|---|---|
| Para tipi | `double` değil `BigDecimal`. `new BigDecimal(0.1)` değil `new BigDecimal("0.1")`. Karşılaştırmada `equals` değil `compareTo`. |
| Domain modeli | `Money` ve `Balance` value object. Kurallar (negatif olamaz, sıfır tutar olmaz) constructor'da, tek bir yerde. İşlemler yeni instance döner. |
| Exception seçimi | Bug → JDK exception'ı (`IllegalArgumentException`, NPE). İş kuralı reddi → domain exception (`InsufficientBalanceException`). |
| Idempotency | `transferId` client'tan gelir. Kontrol yan etkiden **önce** yapılır. "ID = niyet". |
| Deadlock | Hesap başına lock + **lock ordering** (küçük ID önce). Döngüsel beklemeyi kırar. |
| Eşzamanlı idempotency | `get → işlem → save` atomik olmalı. Kontrol lock'un **içine** alındı. |
| TDD disiplini | Kırmızıyı görmediğin test bir şeyi kanıtlamaz. Testi kodu bozarak doğrula. |

## Kurallar ve Testler

1. **Normal transfer:** A=100, B=50, A→B 30 → A=70, B=80, `SUCCESS`.
2. **Yetersiz bakiye:** Transfer `REJECTED` olur, iki hesap da değişmez. Tam bakiye kadar transfer ise kabul edilir (sınır testi).
3. **Idempotency:** Aynı `transferId` ile gelen tekrar para düşürmez, kayıtlı `SUCCESS` sonucu döner.
4. **Aynı hesap (A→A):** `IllegalArgumentException` fırlatılır. Kontrol idempotency ve lock'lardan önce yapılır.
5. **Eşzamanlılık:** 100 thread aynı anda A↔B ve C↔D transferi yapar. Test süresinde biter, toplam para sabit kalır.
6. **Eşzamanlı idempotency:** 100 thread aynı `transferId` ile gelir. Para tam olarak bir kez düşer.

## Detaylar

### 1. BigDecimal

- `new BigDecimal(0.1)` → `0.1000000000000000055511151231257827...` (double'ın gerçek değeri)
- `new BigDecimal("0.1")` → `0.1`
- `new BigDecimal("10.0").equals(new BigDecimal("10.00"))` → `false` (scale farklı)
- `compareTo` → `0` (değer aynı). Testlerde ve iş kurallarında her zaman `compareTo`.
- `assertEquals(40, bigDecimal)` her zaman başarısız olur: `Integer.equals(BigDecimal)` → `false`.

### 2. Value Object Tasarımı

- `Account` artık `BigDecimal` görmez: `balance = balance.subtract(money)`.
- `Balance.subtract` yetersiz bakiyede `InsufficientBalanceException` fırlatır. "Bakiye negatif olamaz" kuralı tek bir yerde yaşar.
- Kural servise taşınırsa (servis önce kontrol eder, sonra çeker) iki sorun çıkar:
  - Servisi atlayan bir çağrı kuralı deler.
  - "Kontrol et, sonra yap" yarışı (check-then-act) oluşur.
- Enum ve value object'lerde setter olmaz. Immutability temel özelliktir.
- Önce `withdraw`, sonra `deposit`. İlki patlarsa ikincisi hiç çalışmaz.

### 3. Exception Seçimi

Sorulacak soru: **"Bu bir bug mı, yoksa iş akışında beklenen bir sonuç mu?"**

| Durum | Exception | Sebep |
|---|---|---|
| `null` tutar | `IllegalArgumentException` / NPE | Kontrat ihlali (bug) |
| Negatif / sıfır tutar | `IllegalArgumentException` | Geçersiz argüman |
| A→A transfer | `IllegalArgumentException` | Geçersiz istek |
| Yetersiz bakiye | `InsufficientBalanceException` | İş kuralı reddi → `REJECTED` |
| Beklenmedik hata | Yukarı fırlatılır, saklanmaz | Retry edilebilir kalmalı |

- `catch (IllegalStateException)` gibi genel tipler alakasız hataları da yakalar. Domain exception, niyeti tipin kendisiyle anlatır.
- Unchecked (`RuntimeException`) tercih edilir: imzayı kirletmez, lambda/stream ile uyumludur.
- `assertThrows` exception'ı döner. Ardından bakiye gibi başka assert'ler de yapılabilir.

### 4. Idempotency

- **ID'yi client üretir.** Server, kasıtsız bir tekrarı (network timeout, Kafka redelivery, çift tıklama, gecikmeli retry) yeni bir istekten ayırt edemez.
- **Kontrat:** Aynı ID = aynı niyet = aynı cevap. Yeni niyet için yeni ID üretmek client'ın sorumluluğu.
- **Kontrol yan etkiden önce yapılır.** Önce para hareket edip sonra "bu ID'yi görmüş müydüm?" diye bakmak, kontrolü "önleme" değil "fark etme" mekanizmasına çevirir. Sonrasında compensation gerekir.
- **Politika:** Bu projede sadece `SUCCESS` nihai sayılıyor. `REJECTED` retry edilebilir.
  - Trade-off: "ilk sonuç döner" kuralı client'a daha tutarlı bir cevap verir. Retry edilebilir `REJECTED` ise bakiye geldiğinde işlemin tamamlanmasına izin verir. İki yol da savunulabilir; önemli olan gerekçe.
- `putIfAbsent` ile yazmak, `REJECTED → SUCCESS` geçişini kaybettiriyordu ve üçüncü retry parayı ikinci kez çekiyordu. `put` ile düzeltildi. Bunu 10 retry + bakiye assert'i yapan test yakaladı.
- **Açık kenar durum:** Aynı ID ile farklı bir payload (tutar / hesap) gelmesi. Gerçek sistemler (ör. Stripe) bunu hata olarak döner.

### 5. Deadlock ve Lock Ordering

**Global lock:** Doğrudur ve deadlock oluşmaz, ama birbiriyle ilgisi olmayan transferler de (C→D, A→B'yi bekler) tek sıraya girer. Ölçeklenmez.

**Hesap başına lock:** Sadece aynı hesaba dokunan transferler birbirini bekler. Transfer iki hesabı birlikte değiştirdiği için iki lock da transfer boyunca tutulur.

**Yeni risk, deadlock:**
```
T1: A→B  🔒A alır, 🔒B'yi bekler
T2: B→A  🔒B alır, 🔒A'yı bekler   → döngüsel bekleme 💀
```

**Çözüm, lock ordering:**
```java
Account firstLock  = from.getId().compareTo(to.getId()) < 0 ? from : to;
Account secondLock = from.getId().compareTo(to.getId()) < 0 ? to : from;

synchronized (firstLock) {
    synchronized (secondLock) {
        // idempotency kontrolü + withdraw + deposit + save
    }
}
```
- Sıralama sadece **lock alma sırasını** belirler. Para hâlâ `from`'dan `to`'ya gider.
- Coffman koşullarından **"döngüsel bekleme"** koşulunu kırar.
- Deneyle kanıtlandı: Sıralama kaldırılınca test her seferinde 5 sn bekleyip deadlock'a düştü.

**`synchronized` sınırı:** Deadlock'a giren thread kurtarılamaz. `shutdown()` çalışan thread'i durdurmaz. `shutdownNow()`'un gönderdiği interrupt'a `synchronized` girişinde bekleyen thread cevap vermez. → `tryLock(timeout)` / `lockInterruptibly()` bu yüzden önemli (bonus).

### 6. Eşzamanlı Idempotency

- Lock ordering yarışı **engellemez**, sadece işlemleri **sıraya koyar**. İki thread lock'tan önce `get` yaparsa ikisi de `null` görür ve ikisi de para çeker.
- Çözüm: `get → işlem → save` hesap lock'larının **içine** alındı.
- Sınır: Aynı ID farklı hesap çiftleriyle gelirse farklı lock'lar alınır. Gerçek sistemde ID'nin atomik rezervasyonu gerekir (DB unique constraint, Redis `SETNX`, `putIfAbsent` + `PENDING`).
- `HashMap` eşzamanlı yazmada bozulur. Görülen gerçek hata: `HashMap$Node cannot be cast to HashMap$TreeNode` (treeify sırasında yarış). Bu yüzden `ConcurrentHashMap` kullanıldı.

### 7. Eşzamanlılık Testi Yazmak

- **Başlangıç kapısı:** `ready` (herkes hazır), `start` (aynı anda başla), `done` (herkes bitti) latch'leri.
- **Her bekleme noktasına timeout.** Latch sayısı uyuşmazlığı (100 bekleyip 1 görev göndermek) deadlock gibi görünür.
- **`done.await` dönüş değeri assert edilir:** `assertTrue(await, "... possible deadlock")`. `shutdown()` sonuçtan bağımsız çalışır.
- **Yarışı görünür kılmak:**
  - Küçük tutarlar: bakiye kuralı yarışı örtmesin.
  - Thread başına çok sayıda işlem: yarış penceresi dar.
  - Her transferde farklı ID: idempotency devreye girip testi bozmasın.
  - İki yön (A→B ve B→A): deadlock ancak böyle oluşur.
- **Doğru değişmezi assert et:** Toplam para sabit. Tek hesaba bakmak lost update'i gizler (A doğru görünürken B'ye para "yoktan" eklenebilir).
- Lambda içinden dış yerel değişkene atama yapılamaz. Thread'e ait değişkenler lambda içinde tanımlanır.
- Thread içinde yutulan exception'lar testi yanlış sebeple kırmızıya çevirir.
- Eşzamanlılık testinin yeşil olması "kod doğru" demek değildir, sadece "bug bulunamadı" demektir.

## TDD Dersleri

- Önce kırmızı. Kırmızıyı görmediğin testin bir şeyi doğruladığını bilemezsin.
- Bir testi doğrulamak için koruduğu kodu yoruma al, kırmızı oluyor mu bak.
- Bug bulunca önce onu yakalayan testi yaz.
- Refactor davranışı değiştirmez. Her refactor sonrası testleri çalıştır.
- Test, kuralın **özünü** assert etmeli: "status aynı" değil, "para tekrar düşmedi".

## Açık Kalanlar

- [ ] Bonus: `ReentrantLock.tryLock(timeout)` versiyonu (lock alınamazsa ne döner?)
- [ ] Aynı `transferId` + farklı payload → hata
- [ ] Temizlik: gereksiz `AtomicReference`, kullanılmayan `Status` değerleri, repository paketi, 3. testte bakiye assert'i
- [ ] Currency kontrolü (kapsam dışı bırakıldı)
