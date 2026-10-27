# AChimera

> ‏Chimera Lite یک کلاینت آزمایشی پراکسی برای Android است. لایهٔ برنامه با Kotlin نوشته شده است: رابط Watfaq بر پایهٔ Jetpack Compose است و رابط به سبک MetaCubeX از نماهای بومی Android استفاده می‌کند. هستهٔ پراکسی با Rust نوشته شده و از طریق UniFFI به Kotlin متصل می‌شود.

AChimera دو رابط قابل‌تعویض دارد: Watfaq و رابط به سبک MetaCubeX. هر دو از پروفایل‌ها، تنظیمات، سرویس VPN و هستهٔ Rust یکسان استفاده می‌کنند. لایهٔ Android مسئول رابط کاربری، اتصال به VPN سیستم و هماهنگی برنامه است؛ پردازش پراکسی را هستهٔ بومی Rust انجام می‌دهد.

## هستهٔ Rust و پشتیبانی از پروتکل‌ها

هستهٔ پراکسی با Rust نوشته شده و به‌صورت کتابخانهٔ بومی Android اجرا می‌شود. طراحی آن بر پردازش کارآمد شبکه و سربار کم منابع تمرکز دارد. مصرف واقعی CPU و حافظه به دستگاه، پیکربندی، پروتکل‌ها، قوانین و تعداد اتصال‌ها بستگی دارد؛ پروژه بدون اندازه‌گیری، عدد ثابتی برای مصرف منابع تضمین نمی‌کند.

هسته، قابلیت‌های گستردهٔ پروتکل‌ها و انتقال داده را از clash-chimera بالادستی به ارث می‌برد. **پروتکل‌ها و قابلیت‌های در دسترس AChimera بر اساس نسخهٔ قفل‌شدهٔ بالادستی [`clash-lib` در Chimera_Client](https://github.com/MFSGA/Chimera_Client/blob/daf67cb09a8f37bbca59b9758801155a2989962a/clash-lib/Cargo.toml) و [قابلیت‌های صریحاً فعال‌شدهٔ هسته در این پروژه](uniffi/chimera-ffi/Cargo.toml) تعیین می‌شوند.** نسخهٔ فعلی قفل‌شده [Chimera_Client v0.28.1](https://github.com/MFSGA/Chimera_Client/releases/tag/v0.28.1) است. وجود قابلیتی در هستهٔ بالادستی لزوماً به معنای پشتیبانی آن در تجزیهٔ پیکربندی، یکپارچه‌سازی Android یا رابط AChimera نیست؛ بنابراین این README فهرست جداگانه و احتمالاً منسوخ پروتکل‌ها ارائه نمی‌کند و سازگاری با همهٔ پیکربندی‌های mihomo را تضمین نمی‌کند.

## قابلیت‌های برنامه

- جابه‌جایی میان رابط Watfaq و رابط به سبک MetaCubeX با داده‌ها و وضعیت اجرای مشترک.
- هدایت ترافیک سیستم از طریق سرویس VPN اندروید و مدیریت پروفایل‌ها، گروه‌های پراکسی و انتخاب گره.
- واردکردن و اعتبارسنجی پیکربندی‌ها و مشاهدهٔ گزارش‌های اجرا و وضعیت گره‌ها.
- اتصال لایهٔ برنامهٔ Kotlin به هستهٔ پراکسی Rust با UniFFI.

## فناوری‌ها

- Android SDK 36 و Android NDK `29.0.14206865`
- Kotlin، Jetpack Compose و Java 25 toolchain
- Rust `1.97.1` (Rust 2024 edition) و `cargo-ndk`
- UniFFI؛ وابستگی Rust `clash-lib` بالادستی با شناسهٔ کامل Git قفل شده است.

## ساخت محلی

### پیش‌نیازها

- JDK 25
- Android SDK 36 و Build Tools 36.0.0
- Android NDK `29.0.14206865`
- Rust `1.97.1` به‌همراه targetها و مؤلفه‌های Android اعلام‌شده در `rust-toolchain.toml`
- `cargo-ndk`؛ ابزار `just` فقط برای تولید یا بررسی bindingهای UniFFI لازم است.

برای یافتن SDK/NDK توسط Gradle، `sdk.dir` (و در صورت نیاز `ndk.dir`) را در فایل محلی `local.properties` تنظیم کنید یا متغیرهای `ANDROID_SDK_ROOT` / `ANDROID_HOME` (و در صورت نیاز `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT`) را تنظیم کنید. فایل `local.properties` شامل مسیرهای مخصوص دستگاه شماست و نباید commit شود.

```sh
cargo install cargo-ndk --locked
```

فقط اگر به تولید یا بررسی bindingها نیاز دارید، `just` را نیز نصب کنید:

```sh
cargo install just --locked
```

### ساخت و بررسی

ساخت Debug APK (به‌طور پیش‌فرض فقط ABI `x86_64`):

```sh
./gradlew :app:assembleDebug
```

ساخت Debug APK برای همهٔ ABIهای Android:

```sh
./gradlew :app:assembleDebug -Pchimera.fullAbi=true
```

اجرای بررسی‌های کیفیت Kotlin و Android:

```sh
./gradlew ktlintCheck testDebugUnitTest lintDebug
```

پس از تغییر رابط‌های خروجی Rust، bindingهای Kotlin UniFFI را با اسکریپت‌های مخزن تولید یا بررسی کنید:

```sh
just generate-bindings
just check-bindings
```

Gradle کتابخانهٔ بومی Rust را برای Android می‌سازد، اما bindingهای Kotlin UniFFI را خودکار دوباره تولید نمی‌کند. تولید و بررسی bindingها به Android NDK، targetهای Rust برای Android و `cargo-ndk` نیاز دارد.

برای Windows، `./gradlew` را با `.\gradlew.bat` جایگزین کنید. اسکریپت `scripts/start-emulator.sh` به محیط Bash و یک AVD ساخته‌شده در Android Studio Device Manager نیاز دارد.

## ساخت‌ها و امضا

GitHub Actions هر روز ساخت Nightly انجام می‌دهد و از صفحهٔ Actions نیز می‌توان آن را دستی اجرا کرد. Debug APK با همهٔ ABIها به‌صورت Artifact به‌مدت ۱۴ روز نگهداری می‌شود؛ فایل‌های APK و checksum همچنین در پیش‌انتشار چرخشی GitHub با نام `nightly` منتشر می‌شوند. APKهای Nightly از کلید پیش‌فرض debug اندروید استفاده می‌کنند و با کلید رسمی انتشار امضا نشده‌اند. فایل‌ها از [صفحهٔ انتشار پروژه](https://github.com/MFSGA/AChimera/releases) یا از اجرای مربوطه در Actions قابل دریافت‌اند.

APK انتشار پایدار به پیکربندی امضای release نیاز دارد. راهنما در [امضای Release APK](RELEASE_SIGNING.md) است. فایل [`SIGNING_CERT_SHA256.txt`](SIGNING_CERT_SHA256.txt) برای بررسی یکسان بودن گواهی امضای نسخه‌های مختلف استفاده می‌شود؛ تداوم گواهی، یکسان بودن هویت امضا را نشان می‌دهد اما به‌تنهایی هویت واقعی شخص یا شرکت را اثبات نمی‌کند.

## وضعیت و محدودیت‌ها

AChimera همچنان در حال توسعه است. قابلیت‌های موجود به کد فعلی، نسخهٔ قفل‌شدهٔ هستهٔ Rust و featureهای فعال بستگی دارند. پشتیبانی پروتکل در بالادست به معنای در دسترس بودن همهٔ قابلیت‌های مربوط در برنامه نیست؛ برخی گزینه‌های هسته یا پیکربندی‌های mihomo ممکن است سازگار نباشند. ساخت موفق نیز اتصال واقعی یک پروتکل، چرخهٔ عمر VPN یا رفتار روی همهٔ دستگاه‌های Android را اثبات نمی‌کند.

## مجوز و مشارکت

مجوز پروژه در [LICENSE](LICENSE) ریشهٔ مخزن است. برای گزارش مشکل یا مشارکت، Issue یا PR ایجاد کنید. [Proxy Wiki](https://mfsga.github.io/Proxy_WIKI/) و [راهنمای معماری برنامه](ARCHITECTURE.md) نیز مفید هستند.

زبان‌های دیگر: [中文](README.md) · [English](README.en.md) · [Русский](README.ru.md).
