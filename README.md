# MedTracker

Персональное офлайн-приложение для Android 16+. Сейчас реализован **этап 1**:
тестовый будильник и мастер разрешений. Медицинские функции пока не реализованы.
Спецификация: [docs/specification.md](docs/specification.md).

- Kotlin, Jetpack Compose / Material3 с системной темой и динамическими цветами.
- Room — текущий тест и журнал; DataStore — настройки мастера; Koin — зависимости.
- Application ID `dev.dadyarri.medtracker`, min SDK 36, target/compile SDK 37.
- JDK 21; JVM target 17; Gradle 9.8.2, AGP 9.5.0 со встроенным Kotlin,
  Compose compiler 2.4.21 и KSP 2.3.13.
- Нативный `AlarmManager.setAlarmClock`, foreground mediaPlayback и уведомления.
  Нет сервера, сети приложения, аналитики и резервного копирования.

## Проверка на телефоне

Версия `0.3-onboarding` (versionCode 4) открывает пошаговый мастер разрешений:
уведомления, точное время, экран напоминания, работа в фоне и настройки Samsung.
Каждый шаг можно пропустить. Проверка Samsung выполняется вручную.
После завершения открывается «Сегодня» с навигацией «Сегодня / История /
Лекарства / Настройки»; содержимое всех четырёх экранов пока пустое.
Завершение мастера сохраняется в DataStore; обновление не сбрасывает его.
Экран постановки тестовых сигналов удалён по согласованному изменению интерфейса.
Механизм сигналов, Room и существующее состояние сохранены.

Обязательный ручной протокол и ограничения:
[docs/alarm-spike-testing.md](docs/alarm-spike-testing.md).
Основной чеклист alarm spike отмечен владельцем как пройденный на Samsung.
Новый мастер и навигация требуют отдельной ручной проверки.

## Сборка

Установить полный JDK 21, Android SDK с `platforms;android-37`,
`build-tools;36.0.0`, `platform-tools`, `ndk;29.0.14206865`;
задать `JAVA_HOME` и `ANDROID_HOME`.
Путь SDK также можно указать в игнорируемом `local.properties`.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Отчёты: `app/build/reports/`. JVM-тесты включают чистую политику расписания
и Android API 36/Room через Robolectric. Они не подтверждают звук, экран
блокировки, энергосбережение или особенности физического Samsung.

В подготовленной облачной среде перед Gradle:

```sh
source /workspace/toolchains/env.sh
cd /workspace/med-tracker
```

## GitHub Actions

Workflow **Android APK** запускается при push в `master`/`feature/**`, в PR
и вручную (`workflow_dispatch`). Он собирает debug APK, выполняет unit tests
и Lint и сохраняет `MedTracker-debug-<run>` и `checks-<run>` на 14 дней.
APK доступен после успешного запуска в Actions → Android APK → Artifacts.
Артефакты GitHub скачиваются ZIP-архивом; перед установкой извлеките APK.

Debug APK предназначен для тестирования. CI требует постоянный ключ из GitHub Secret
`ANDROID_DEBUG_KEYSTORE_BASE64`: без него workflow останавливается вместо генерации
новой несовместимой подписи. В Artifacts рядом с APK находится публичный сертификат
`signing-certificate.txt`; его SHA-256 должен совпадать у обновляемых сборок.

Однократная настройка на своей машине (JDK 21, GitHub CLI с правом управления
секретами репозитория):

```sh
./scripts/configure-ci-signing.sh
```

Скрипт сохраняет тестовый ключ вне checkout и передаёт его в GitHub Secret через stdin.
Повторный запуск использует тот же файл. Для уже сохранённого стандартного debug-ключа:

```sh
./scripts/configure-ci-signing.sh /path/to/existing/debug.keystore
```

Ключ: alias `androiddebugkey`, store/key password `android`. Это тестовая подпись,
не release signing. Храните приватный keystore надёжно, не добавляйте его в Git
и не присылайте значения секрета в чат. Для локальной сборки с той же подписью
используйте этот ключ как `~/.android/debug.keystore`, предварительно сохранив
существующий файл; не заменяйте другой нужный ключ без резервной копии.

Старые CI-сборки использовали случайную подпись каждого runner-а. Если её приватный
ключ не сохранён, обновить такое приложение новой подписью без удаления нельзя.
Однократная переустановка удалит локальные данные, настройки и выданные разрешения;
сначала настройте постоянный ключ и получите успешный CI, затем решите вопрос переноса.
Подмена сертификата, увеличение versionCode или восстановление одной лишь публичной
части сертификата не исправляют несовместимость подписей.
