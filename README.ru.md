# AChimera

Язык этой инструкции: Русский · Другие языки: [中文](README.md) · [English](README.en.md) · [فارسی](README.fa.md)

> Chimera Lite — экспериментальный прокси-клиент для Android. Слой приложения написан на Kotlin: интерфейс Watfaq использует Jetpack Compose, а интерфейс в стиле MetaCubeX — нативные представления Android. Прокси-ядро написано на Rust и подключено к Kotlin через UniFFI.

AChimera предлагает два переключаемых интерфейса: Watfaq и вариант в стиле MetaCubeX. Оба используют общие профили, настройки, VPN-сервис и ядро Rust. Слой Android отвечает за интерфейс, интеграцию с системным VPN и координацию приложения; обработку прокси-трафика выполняет нативное ядро Rust.

## Ядро Rust и поддержка протоколов

Прокси-ядро написано на Rust и работает как нативная библиотека Android. Оно спроектировано для эффективной обработки сети с низким потреблением ресурсов. Фактическое использование CPU и памяти зависит от устройства, конфигурации, протоколов, правил и числа соединений; проект не обещает фиксированные показатели без измерений.

Ядро наследует широкие возможности прокси-протоколов и транспорта от вышестоящего clash-chimera. **Доступные в AChimera протоколы и функции определяются закреплённой версией [`clash-lib` в Chimera_Client](https://github.com/MFSGA/Chimera_Client/blob/daf67cb09a8f37bbca59b9758801155a2989962a/clash-lib/Cargo.toml) и [явно включёнными функциями ядра в этом проекте](uniffi/chimera-ffi/Cargo.toml).** Сейчас закреплена версия [Chimera_Client v0.28.1](https://github.com/MFSGA/Chimera_Client/releases/tag/v0.28.1). Наличие функции в вышестоящем ядре не означает, что она уже доступна через разбор конфигурации, интеграцию Android или интерфейс AChimera. Поэтому здесь нет отдельного списка протоколов, который может устареть, и не обещается совместимость со всеми конфигурациями mihomo.

## Возможности приложения

- Переключение между интерфейсами Watfaq и в стиле MetaCubeX с общими данными профилей и состоянием приложения.
- Маршрутизация системного трафика через VPN-сервис Android, управление профилями и прокси-группами, выбор узлов.
- Импорт и проверка конфигураций, просмотр журналов работы и состояния узлов.
- Связь слоя приложения Kotlin с прокси-ядром Rust через UniFFI.

## Технологический стек

- Android SDK 36 и Android NDK `29.0.14206865`
- Kotlin, Jetpack Compose и Java 25 toolchain
- Rust `1.97.1` (Rust 2024 edition) и `cargo-ndk`
- UniFFI; зависимость Rust `clash-lib` закреплена по полному Git revision.

## Локальная сборка

### Требования

- JDK 25
- Android SDK 36 и Build Tools 36.0.0
- Android NDK `29.0.14206865`
- Rust `1.97.1`, а также Android targets и компоненты из `rust-toolchain.toml`
- `cargo-ndk`; `just` нужен только для генерации или проверки UniFFI bindings.

Чтобы Gradle нашёл Android SDK/NDK, задайте `sdk.dir` (и при необходимости `ndk.dir`) в локальном `local.properties` либо настройте `ANDROID_SDK_ROOT` / `ANDROID_HOME` (и при необходимости `ANDROID_NDK_HOME` / `ANDROID_NDK_ROOT`). `local.properties` содержит локальные пути и не должен попадать в Git.

```sh
cargo install cargo-ndk --locked
```

Установите `just`, только если нужно создавать или проверять bindings:

```sh
cargo install just --locked
```

### Сборка и проверки

Сборка Debug APK (по умолчанию только ABI `x86_64`):

```sh
./gradlew :app:assembleDebug
```

Сборка Debug APK для всех Android ABI:

```sh
./gradlew :app:assembleDebug -Pchimera.fullAbi=true
```

Проверки качества Kotlin и Android:

```sh
./gradlew ktlintCheck testDebugUnitTest lintDebug
```

После изменения экспортируемых интерфейсов Rust создайте или проверьте Kotlin bindings UniFFI с помощью скриптов репозитория:

```sh
just generate-bindings
just check-bindings
```

Gradle собирает нативную библиотеку Rust для Android, но **не генерирует Kotlin bindings UniFFI автоматически**. Для генерации и проверки нужны Android NDK, Rust targets для Android и `cargo-ndk`.

В Windows замените `./gradlew` на `.\gradlew.bat`. Скрипту `scripts/start-emulator.sh` нужна среда Bash и AVD, созданный в Android Studio Device Manager.

## Сборки и подпись

GitHub Actions ежедневно выполняет Nightly-сборку; её также можно запустить вручную на странице Actions. Debug APK со всеми ABI хранится как Actions Artifact в течение 14 дней; APK и файл контрольных сумм также публикуются в обновляемом GitHub prerelease `nightly`. Nightly APK подписываются стандартным Android debug-ключом, а не ключом стабильного релиза. Их можно скачать на [странице релизов проекта](https://github.com/MFSGA/AChimera/releases) или из соответствующего запуска Actions.

Для стабильного Release APK нужна настройка подписи. Инструкции приведены в документе [Подпись Release APK](RELEASE_SIGNING.md). Файл [`SIGNING_CERT_SHA256.txt`](SIGNING_CERT_SHA256.txt) позволяет проверить, подписаны ли разные версии одним сертификатом. Совпадение сертификата подтверждает непрерывность ключа подписи, но само по себе не подтверждает реальную личность человека или компании.

## Статус и ограничения

AChimera продолжает разрабатываться. Фактические возможности зависят от текущего исходного кода, закреплённой версии ядра Rust и включённых функций. Поддержка протокола в upstream не означает, что все связанные возможности доступны в приложении; некоторые параметры ядра или конфигурации mihomo могут не поддерживаться. Успешная сборка также не доказывает соединение по конкретному протоколу, корректность жизненного цикла VPN или работу на всех устройствах Android.

## Лицензия и участие

Лицензия проекта находится в корневом файле [LICENSE](LICENSE). Для сообщений об ошибках и предложений открывайте Issue или PR. Также могут быть полезны [Proxy Wiki](https://mfsga.github.io/Proxy_WIKI/) и [описание архитектуры приложения](ARCHITECTURE.md).
