# Glyphs spec

Конспект по поддержке Nothing Glyph в mauve. Источник требований:
Notion, «v0.3.0: Not Enough Glyphs!».

Сейчас здесь описано только текущее состояние (унаследовано от exteraless,
коммиты `9d15b912d`, `74cb562f3`). Номера строк актуальны на ветке `v0.3.0`
(`75dac3e8f`) и поплывут, поэтому ищи по именам.

## 1. Текущая система

### 1.1 Карта файлов

| Что | Где |
|---|---|
| Контроллер (вся логика, синглтон) | `TMessagesProj/src/main/java/app/exteraless/glyph/GlyphController.java` |
| Настройки (5 bool) | `TMessagesProj/src/main/kotlin/app/exteraless/glyph/GlyphConfig.kt` |
| Экран настроек | `TMessagesProj/src/main/java/app/exteraless/settings/OpenExteraGlyphActivity.java` |
| Строки (`OEGlyph*`) | `res/values/strings_oe_glyph.xml`, плюс `values-ru-rRU`, `values-ar` |
| SDK | `TMessagesProj/libs/glyph-matrix-sdk-2.0.aar` (в `.gitignore` добавлен как исключение) |
| Подключение SDK, ключ | `TMessagesProj/build.gradle` (`glyphKey`, `implementation files(...)`), `gradle.properties` (`GLYPH_API_KEY=test`) |
| Манифест | permission `com.nothing.ketchum.permission.ENABLE`, meta-data `NothingKey=${GLYPH_API_KEY}`, `tools:overrideLibrary="com.nothing.thirdparty"` (у AAR minSdk 33) |

Тестов на глифы нет. Правил R8/proguard для `com.nothing.*` нет. В `CHANGELOG.md`
глифы не упоминаются.

### 1.2 Устройства и SDK

- Поддержка определяется как `Build.MANUFACTURER == "nothing"` (`isSupported()`).
  На остальных контроллер не инициализируется вообще.
- Два взаимоисключающих режима, выбор в `init()` через `isMatrixDevice()`:
  - **matrix**: `Common.is23112() || Common.is25111p()`, работает через
    `GlyphMatrixManager`;
  - **strip** (LED-зоны): все остальные, через `GlyphManager`. Регистрируются
    `20111`, `22111`, `23111`, `23113`, `24111`, `25111`. Если модель не из списка,
    `register` не вызывается и сессия не открывается (тихо, без лога).
- Коды моделей по документации Nothing (перепроверить перед использованием):
  20111 Phone (1), 22111 Phone (2), 23111 Phone (2a), 23113 Phone (2a) Plus,
  24111 Phone (3a)/(3a) Pro, 23112 Phone (3), 25111 Phone (4a), 25111p Phone (4a) Pro.
- Жизненный цикл strip: `init(callback)` → `onServiceConnected` → `register(device)`
  → `openSession()` → `stripSessionOpen = true`. Matrix: `init` → `register` →
  `matrixRegistered = true`. Бинд асинхронный, флаги `volatile`.
- Ключ `test` отладочный (на устройстве нужен включенный Glyph debug mode), боевой
  выдается Nothing под package name и подставляется через `GLYPH_API_KEY`
  (`local.properties` или env).

Что SDK умеет, но код не использует (важно для будущих анимаций и выбора зон):

- `GlyphFrame.Builder`: `buildChannel(int)` и `buildChannel(int, int)` для
  отдельных зон, плюс `buildPeriod/Cycles/Interval`. Индексы зон лежат в
  `Glyph.Code_20111/22111/23111/24111/25111` (например у 25111 только `A_1..A_6`,
  у 24111 `A_1..A_11, B_1..B_5, C_1..C_20`). Класса `Code_23113` в AAR нет.
- `GlyphManager`: `toggle(frame)`, `animate(frame)`, `displayProgress(...)`,
  `displayProgressAndToggle(...)`, `setFrameColors(int[])`, `turnOff()`,
  `closeSession()`.
- `GlyphMatrixManager`: `setMatrixFrame` / `setAppMatrixFrame` (от объекта или от
  сырого `int[]`), `closeAppMatrix()`, `setGlyphMatrixTimeout(boolean)`, `turnOff()`.
- `GlyphMatrixObject.Builder`: картинка, текст, позиция, поворот, масштаб, яркость,
  инверсия. `GlyphMatrixFrame.Builder`: три слоя (`addTop/Mid/Low`), `buildWithMarquee`.
- `GlyphMatrixUtils`: конвертация bitmap в матрицу, текст, `generateMatrixProgress`.
- `Common.getDeviceMatrixLength()`.

### 1.3 Настройки

`GlyphConfig` хранит все в общих `NekoConfig.getPreferences()` через `ConfigItem`
(та же схема, что у `OpenExteraConfig`). Загрузка умеет только bool.

| Ключ | Геттер | По умолчанию | Смысл |
|---|---|---|---|
| `OEGlyphEnabled` | `enabled()` | false | мастер-переключатель, без него нет бинда к сервису |
| `OEGlyphNewMessage` | `onNewMessage()` | true | вспышка на входящее сообщение |
| `OEGlyphCalls` | `onCall()` | true | свечение на входящий звонок |
| `OEGlyphRecording` | `onRecording()` | true | свечение при записи голосового |
| `OEGlyphScreenOff` | `screenOffOnly()` | false | сообщения только при выключенном экране |

Ключи попадают в бэкап настроек через `GlyphConfig.getConfigTypes()`
(`SettingsBackupHelper`). Новым настройкам не-bool типа нужна поддержка в
`loadConfig` и в бэкапе.

### 1.4 Экран настроек

- Вход: General → секция Notifications, строка `glyph` (`OpenExteraGeneralActivity`,
  `glyphRow`), видна только на Nothing. Не в Experimental Features, как
  предполагает Notion.
- В `OpenExteraOtherActivity` остался мертвый код: `glyphRow`/`glyphDividerRow`
  всегда `-1`. В `SettingsHelper` есть `MOVED_ROWS "other:glyph" → "general"`,
  алиас поиска `OEGeneral:glyph` и диплинк `exteraless_glyph`.
- `OpenExteraGlyphActivity` (`BaseNekoSettingsActivity`, search guid 25000, префикс
  `OEGlyph`): заголовок, 5 переключателей в порядке Enable, New message, Calls,
  Recording, Screen off, затем Preview и инфо-текст `OEGlyphInfo`.
- Enable дергает `init()` или `shutdown()`. Остальные только пишут конфиг.
  Зависимые строки не скрываются и не дизейблятся при выключенном Enable.
- Preview: на не-Nothing показывает bulletin `OEGlyphUnsupported`, иначе
  `GlyphController.preview()`.

### 1.5 Хуки в коде Telegram

| Событие | Место | Вызов |
|---|---|---|
| Старт приложения | `ApplicationLoader` (после остальных `*Config.init()`) | `GlyphConfig.init()`, `GlyphController.init()` |
| Новые сообщения | `NotificationsController.processNewMessages`, только при `isLast`, после проверки на пустой список | `onNewMessages(messageObjects, currentAccount)` |
| Запись голосового: старт/резюм | `MediaController`, 4 места (`onRecordingStarted`, `onRecordingResumed` у recorder V2, резюм и старт у старого) | `onRecordingStarted()` |
| Запись: стоп | `MediaController.stopRecording(...)`, первой строкой | `onRecordingStopped()` |
| Входящий звонок | `VoIPService.dispatchStateChanged`: `STATE_WAITING_INCOMING && !isOutgoing` | `onIncomingCallStarted()` |
| Конец звонка | там же на `ESTABLISHED/ENDED/FAILED/HANGING_UP` и в `onDestroy` | `onIncomingCallStopped()` |

Стоп-хуки не проверяют конфиг намеренно: гасить надо всегда.

### 1.6 Поведение

Вся работа с SDK идет на main-хендлере (`handler.post`).

**Новое сообщение** (`onNewMessages`):

1. Нужны `enabled` и `onNewMessage`.
2. Хук стоит до фильтрации уведомлений, поэтому свой фильтр: в пачке должно быть
   хотя бы одно сообщение не `isOutOwner()` и из чата без `isDialogMuted`.
   Других проверок нет: открытый чат, выключенные глобально уведомления, тип
   чата, реакции и т.п. не учитываются. Какой именно чат сработал, дальше не
   передается.
3. На main: пропуск, если не готовы (`ensureReady`), если `glyphBusy` (идет запись
   или звонок), если `screenOffOnly` и экран включен (`PowerManager.isInteractive`).
4. Кулдаун `FLASH_COOLDOWN_MS = 2500` мс от прошлой вспышки, общий на все чаты и
   аккаунты. Сообщения в окне кулдауна теряются, не откладываются.
5. Matrix: `showMatrixLogo()`. Strip: `pulseStrip()`.

**Запись и звонок**: флаги `recordingActive` / `callActive`, общая точка
`updateGlyphActivity()`. Пока активна хотя бы одна причина, `glyphBusy = true` и
идет «дыхание»: strip `breatheStrip()`, matrix `startMatrixBreathing()`. Когда обе
сняты: `stopMatrixBreathing()` и `stripManager.turnOff()`. Режим «только при
выключенном экране» на них не действует. Если на старте `ensureReady()` false,
`glyphBusy` остается false и анимация не начнется до следующей смены состояния.

**Анимации** (все захардкожены, выбора нет):

| Имя | Устройство | Параметры |
|---|---|---|
| `pulseStrip` | strip | все каналы A..E, period 600, cycles 2, interval 120, `animate` |
| `breatheStrip` | strip | все каналы A..E, period 1500, cycles 600 (страховка), interval 0 |
| лого | matrix | `exteraless_icon_monochrome`, тонированный в белый, scale 150, яркость 255, слой top, `setAppMatrixFrame`, автоскрытие через `LOGO_SHOW_MS = 3000` (`closeAppMatrix`) |
| дыхание лого | matrix | тот же кадр, яркость 12..255 шагом 10 каждые 120 мс, лимит 5000 шагов (~10 минут) |

Bitmap лого кэшируется в `matrixLogoBitmap`. `setAppMatrixFrame` выбран вместо
`setMatrixFrame`, чтобы не конфликтовать с Glyph Toys.

**Preview**: если сервис еще не подключен, ставит `previewPending` и доигрывает из
колбэка подключения. Иначе на matrix показывает лого, на strip `pulseStrip()`.
Кулдаун, `glyphBusy` и `screenOffOnly` не учитывает.

**Shutdown** (выключение Enable): сброс всех флагов и таймеров, `unInit()` у обоих
менеджеров. Перед `unInit` не вызываются `turnOff()` / `closeSession()` /
`closeAppMatrix()`.

### 1.7 Известные проблемы и ограничения

Из Notion:

- светятся все глифы сразу на любое уведомление;
- большой кулдаун между срабатываниями (2.5 с, глобальный, с потерей событий);
- анимацию сменить нельзя;
- нет правил по типу уведомления и по чату.

Найдено в коде:

- в `onNewMessages` теряется контекст (чат, тип сообщения), контроллер получает
  только факт «что-то пришло»;
- фильтр уведомлений неполный (см. 1.6, п. 2), вспышка возможна без уведомления;
- strip: каналы задаются только целиком A..E, без отдельных зон и без учета
  раскладки конкретной модели;
- на `onServiceDisconnected` нет переподключения, а `initialized` остается true,
  так что `init()` повторно не биндит до `shutdown()`;
- незарегистрированная strip-модель молча ничего не делает;
- нет приоритетов между событиями: вспышка сообщения во время записи/звонка
  просто отбрасывается;
- комментарии в коде глифов на русском; по `AGENTS.md` при правке переводить на
  английский;
- инфо-текст `OEGlyphInfo` упоминает только Phone (3) как matrix-устройство, хотя
  код поддерживает и `25111p`;
- расхождение с Notion: экран живет в General → Notifications, а не в
  Experimental Features.
