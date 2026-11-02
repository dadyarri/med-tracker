# MedTracker

Персональное офлайн-приложение для Android 16+. Сейчас реализован **этап 1**:
тестовый будильник и мастер разрешений. Медицинские функции пока не реализованы.
Спецификация: [docs/specification.md](docs/specification.md).

- Kotlin, Jetpack Compose / Material3 с системной темой и динамическими цветами.
- Room — текущий тест и журнал; DataStore — настройки мастера; Koin — зависимости.
- Application ID `dev.dadyarri.medtracker`, min/target/compile SDK 36.
- JDK 21; JVM target 17; Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21.
- Нативный `AlarmManager.setAlarmClock`, foreground mediaPlayback и уведомления.
  Нет сервера, сети приложения, аналитики и резервного копирования.

## Проверка на телефоне

Открыть мастер и выдать разрешения. Samsung Never sleeping apps проверяется
вручную, отметка в приложении не подтверждает системное состояние.
Назначить тест через 30 секунд. Новая постановка заменяет предыдущую.
Тихий тест не включает звук и не открывает полноэкранный UI.
«Отключить звук» оставляет повтор, «Отложить» заменяет следующий повтор,
«Отменить» завершает тест и все повторы. Параметры по умолчанию:
15 минут / окно 120 минут / snooze 5 минут, максимум 3 раза.
Один непрерывный звуковой сигнал ограничен 60 секундами.

Обязательный ручной протокол и ограничения:
[docs/alarm-spike-testing.md](docs/alarm-spike-testing.md).
**Проверка на физическом Samsung ещё не выполнена.**

## Сборка

Установить полный JDK 21, Android SDK с `platforms;android-36`,
`build-tools;35.0.0`, `platform-tools`; задать `JAVA_HOME` и `ANDROID_HOME`.
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

Debug APK предназначен для тестирования. Между разными CI-запусками подпись
может отличаться: для проверки обновления без удаления данных используйте
один сохранённый debug keystore. Release-подпись не настроена.
