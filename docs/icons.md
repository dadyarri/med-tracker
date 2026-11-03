# Иконки

Иконки интерфейса и уведомлений — официальные Material Symbols Rounded,
24 dp, стандартное начертание (weight 400, fill 0).
Источник: https://github.com/google/material-design-icons/tree/master/symbols/android
Файлы скачаны из `<symbol>/materialsymbolsrounded/<symbol>_24px.xml`.
Удалён только `android:tint`, чтобы цвет задавался Material 3 Icon или системой
уведомлений. Геометрия оригинальная. Иконка запуска приложения остаётся логотипом.
Лицензия Apache 2.0 включена в APK: `assets/licenses/material-symbols.txt`.
Внешняя библиотека иконок и загрузка ресурсов во время работы не нужны.

| Ресурс | Material Symbol |
|---|---|
| ic_bell | notifications |
| ic_blood_pressure | blood_pressure |
| ic_calendar | calendar_today |
| ic_clock | schedule |
| ic_context_after_meal | ramen_dining |
| ic_context_before_meal | restaurant |
| ic_context_day | light_mode |
| ic_context_evening | wb_twilight |
| ic_context_fasting | no_food |
| ic_context_morning | wb_sunny |
| ic_context_night | bedtime |
| ic_context_unlinked | link_off |
| ic_context_unplanned | event_busy |
| ic_glucose | glucose |
| ic_heart | cardiology |
| ic_history | history |
| ic_info | battery_saver |
| ic_instructions | description |
| ic_lock | screen_lock_portrait |
| ic_notification | health_and_safety |
| ic_settings | settings |
| ic_weight | monitor_weight |
| ic_medication | medication |
| ic_meal | restaurant_menu |

`ic_meal` используется для еды и перекуса; `ic_context_fasting` — только для
контекста «Натощак». Подписи доступности и подсказки контекстов сохранены.

Проверки: `./gradlew assembleDebug testDebugUnitTest lintDebug --warning-mode all`
— exit 0, 23s. Preview нижней навигации отрисован и визуально проверен.

Остаётся ручная проверка на устройстве: ряд добавления, контексты форм,
настройки, мастер разрешений; светлая/тёмная тема и малая иконка уведомления.
