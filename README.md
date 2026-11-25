# Расписание ТУСУР — Android-клиент

Неофициальный клиент расписания ТУСУР: расписание группы и преподавателя, локальные
напоминания, схема корпуса ФЭТ и 4 виджета для домашнего экрана.

**Статус:** `assembleDebug`, `assembleRelease` (R8 + shrinkResources), `testDebugUnitTest` (72 теста),
`connectedDebugAndroidTest` (7 Compose-тестов на эмуляторе), `lintDebug` (**0 ошибок, 0 предупреждений**),
`detekt` (0 замечаний) — всё зелёное. Debug APK ≈ 13.9 МБ, release ≈ 2.9 МБ, 92 Kotlin-файла / ~10.5k строк.

## Решения (коротко)

- **Стек:** Kotlin 2.0.21, JDK 17, Compose + Material 3 (dynamic color на Android 12+),
  Glance 1.1 для виджетов, Hilt, Room, DataStore, WorkManager, AlarmManager.
- **Сеть:** Retrofit 2.11 + OkHttp + kotlinx.serialization. Ошибки HTTP → человеческие состояния
  в одном `ApiErrorMapper`; сеть изолирована интерфейсами `ScheduleRemoteSource`.
- **Архитектура:** MVVM + Clean-ish (`data`/`domain`/`ui`), однонаправленный поток, `StateFlow`.
  Вся логика «что идёт сейчас» — чистые функции в `domain`, тестируются без Android.
- **Время:** «сегодня/сейчас» всегда в **Asia/Tomsk (UTC+7)**, независимо от пояса устройства.
- **Источники:** основной — прокси-API сайта `/api/week`, `/api/search`, `/api/load`;
  резерв — официальный публичный `api.tusur.ru` (включается в настройках, по умолчанию выкл.).
- **Схема корпуса:** данные, не картинка. `assets/fet.json` с сайта + геометрия из `map.js`.
- **Офлайн:** Room по ключу `sourceKey + monday`, TTL 5 мин в памяти; при старте показывается
  кэш, затем фоновое обновление; при отсутствии сети — плашка с временем данных.

## Структура

```
app/src/main/java/ru/tusur/timetable/
├─ core/                     TomskTime, RoomRefs, RussianPlurals
├─ domain/
│  ├─ model/                 Lesson, Week, Timeline, Building, Errors, AppSettings, SourceRef
│  └─ repository/            контракты репозиториев
├─ data/
│  ├─ network/               API (прокси, официальный), ApiErrorMapper, NetworkRetry, источники
│  ├─ mapper/                DTO → домен
│  ├─ db/                    Room: недели, события, люди, справочник поиска, настройки виджетов
│  ├─ prefs/                 DataStore
│  ├─ map/                   парсер и геометрия fet.json
│  └─ repository/            offline-first репозитории, RefreshCoordinator
├─ notifications/            каналы, AlarmManager, приёмники, тексты уведомлений
├─ work/                     RefreshWorker, MaintenanceWorker, WorkScheduler
├─ widget/                   4 Glance-виджета, снапшоты, обновление, конфигурация
├─ di/                       Hilt-модули
└─ ui/                       MainActivity, навигация, экраны, тема
```

## Сборка

Нужны JDK 17+ и Android SDK (platform 35, build-tools 35.0.0).

```bash
echo sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk > local.properties

./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest   # эмулятор/устройство
./gradlew detekt
./gradlew :app:lintDebug
```

Свой базовый URL без правки кода:

```bash
./gradlew :app:assembleDebug -Ptimetable.baseUrl=https://example.org/
```

## Разрешения

| Разрешение | Зачем |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | расписание и загрузка аудиторий |
| `POST_NOTIFICATIONS` (13+) | напоминания и сводки |
| `SCHEDULE_EXACT_ALARM` (12+) | точное время; без него — неточный режим + предупреждение в настройках |
| `RECEIVE_BOOT_COMPLETED` | пересборка будильников и виджетов после перезагрузки |
| `WAKE_LOCK` | доставка напоминаний в простое |

## Виджеты

| Виджет | Размер | Содержимое |
|---|---|---|
| Следующая пара | 2×2 | «Идёт N-я пара · ещё X мин» / «Следующая через …», дисциплина, аудитория |
| Сегодня | 4×2 | до 5 пар, «+N ещё», «Сегодня пар нет» |
| Неделя | 4×4 | пн–сб, число и число пар, выделение сегодня; шире 280 dp — 3 колонки, иначе список |
| Быстрый выбор | 2×2 | своё расписание + режим «сегодня / следующая пара», config activity |

Обновление: WorkManager раз в 30 минут (страховка) + немедленно при старте приложения, смене
расписания, успешной загрузке, добавлении виджета и по `BOOT_COMPLETED`, `TIME_SET`,
`TIMEZONE_CHANGED`, `DATE_CHANGED`. Виджеты читают только Room — в сеть из composition не ходят.
Живой посекундный отсчёт не обещается: «ещё N мин» обновляется по расписанию WorkManager.

## Deep links

```
timetable://day/2026-11-24
timetable://group/fit/023
timetable://teacher/35663
timetable://map?r=331%D0%B1
```

## Тесты

`app/src/test` (72 теста, JUnit4 + MockWebServer + Turbine + MockK):

- `TomskTimeTest` — пояса, «сегодня» по Томску, окно ±30 недель, формат длительности/дат;
- `LessonDayTest` — типы занятий по подстроке, перерывы ≤30 мин vs окна, «идёт/следующая/
  закончились», границы фаз, прогресс;
- `RoomOccupancyTest` — индексация `/api/load` (день × пара), протяжка занятости, нормализация
  «331б» ↔ «331b»;
- `SourceRefTest` — ключи `group:`/`teacher:`, официальный id, фамилия для API, `webUrl`;
- `ApiErrorMapperTest`, `ProxyApiContractTest` — 404 `unknown_target`, 400 `bad_week`, 429, 5xx,
  таймаут, `stale`, маппинг полного ответа `/api/week` через MockWebServer;
- `EntityMappersTest` — roundtrip Room с пустыми днями и ссылками;
- `OfficialWeekMapperTest` — официальный API: аудитория ФЭТ, преподаватели, группы;
- `FeetBuildingParserTest` — реальный `fet.json`: 5 этажей, «331б», план 1-го этажа, двор и пристройка;
- `ReminderPlannerTest` — за N минут, вечерняя/утренняя сводка, пропуск прошедших, стабильные id;
- `DeepLinksTest`, `WidgetDeepLinkTest` — разбор и сборка ссылок, пустые состояния виджетов;
- `PickerViewModelTest` — debounce схлопывает ввод, короткий запрос не идёт в сеть.

`app/src/androidTest` — Compose-тесты, прогнаны на эмуляторе (7/7):
- `ScheduleScreenTest` — пустой день с переходом к ближайшему, выбор дня, «идёт N-я пара» сегодня,
  ошибка сети с кнопкой «Повторить»;
- `PickerContentTest` — ввод доходит до ViewModel, подсказка при коротком запросе, выбор результата.

## Известные ограничения

- Официальный API не имеет поиска и ищет преподавателя по фамилии: переход на преподавателя
  из карточки работает, только если фамилия известна (из поиска или ссылки).
- Glance не поддерживает `font-feature-settings`, табличные цифры включены только в приложении.
- Скриншот-тесты (Roborazzi/Paparazzi) не подключены: без эталонных изображений они дают шум,
  а не сигнал; место под них — `app/src/test/screenshot`.
