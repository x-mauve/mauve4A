# Glyph API spec

Конспект по Glyph Developer Kit (GDK) от Nothing: что есть в SDK и как оно
работает на каждой модели. Про то, как SDK используется в mauve сейчас, см.
`glyphs-spec.md`.

Пометки достоверности:

- **[doc]** официальный README (Glyph-Developer-Kit, GlyphMatrix-Developer-Kit);
- **[sdk]** прочитано в байткоде AAR (`javap`), сигнатуры и константы точные,
  логика восстановлена по байткоду;
- **[wiki]** nothing.wiki, неофициальный ресурс сообщества;
- **[issue #N]** issue в репозитории Glyph-Developer-Kit, опыт сторонних
  разработчиков, не подтверждено Nothing.

Все, что не [doc] и не [sdk], перед использованием проверять на устройстве.

## 1. Общие положения

### 1.1 Что такое GDK

- Один AAR на все: Glyph Interface (LED-зоны) и Glyph Matrix. Пакет классов
  `com.nothing.ketchum`, AIDL `com.nothing.thirdparty.IGlyphService`. [doc][sdk]
- SDK сам ничего не зажигает: он биндится к системному сервису
  `com.nothing.thirdparty/.GlyphService` (action
  `com.nothing.thirdparty.bind_glyphservice`) и шлет ему массивы яркостей. [sdk]
- Работает только на телефонах Nothing с Android 14+. minSdk у AAR 33. [doc][sdk]
- История: февраль 2024 первый GDK для Phone (1) и (2), потом (2a); 1 июля 2025
  вторая версия с Glyph Matrix для Phone (3); март 2026 Phone (4a) и (4a) Pro;
  июль 2026 Phone (4b). [wiki]

### 1.2 Версии AAR

Файл всегда называется `glyph-matrix-sdk-2.0.aar`, но сборок две:

| Где лежит | Размер | Что внутри |
|---|---|---|
| `GlyphMatrix-Developer-Kit/glyph-matrix-sdk-2.0.aar` | 58 757 Б | без Phone (4b). **Именно эта сборка сейчас в `TMessagesProj/libs`** (sha256 совпадает) |
| `Glyph-Developer-Kit/sdk/glyph-matrix-sdk-2.0.aar` | 114 689 Б | добавлены `Common.is25131()`, `Glyph.DEVICE_25131`, `Glyph.Code_25131`, `GlyphMatrixNTypeUtils` и библиотека текста `com.nothinglondon.text` |

Остальной публичный API у сборок одинаковый. [sdk]

### 1.3 Манифест и ключ

```xml
<uses-permission android:name="com.nothing.ketchum.permission.ENABLE"/>
<!-- inside <application> -->
<meta-data android:name="NothingKey" android:value="{API Key}"/>
```

- Начиная с Android 16 (Nothing OS 4.0) ключ не проверяется, запрашивать его не
  нужно. Тег `NothingKey` рекомендуют оставлять ради совместимости. [doc]
- Исключение: Phone (1) остался на Nothing OS 3, там ключ нужен. Без тега вообще
  на Phone (1) не работает. [issue #23]
- Получить боевой ключ сейчас можно только письмом на `GDKsupport@nothing.tech`,
  форма на сайте убрана, отвечают не всегда. [issue #23]
- Отладка без ключа: значение `test` плюс
  `adb shell settings put global nt_glyph_interface_debug_enable 1`. Режим сам
  выключается через 48 часов, при включении приходит уведомление. [doc]
- `register()` читает `NothingKey` из meta-data и передает в сервис вместе с
  кодом устройства (`registerSDK(key, device)`). Matrix-регистрация ключ не
  передает (`registerMatrixSDK(device)`). [sdk]

### 1.4 Ограничения и подводные камни

- **Только foreground.** README: «Only foreground applications are allowed to be
  used». [doc] На практике из обычного `Service` на свежих прошивках работает,
  даже после смахивания приложения из недавних. [issue #11] Для mauve это
  критично (сообщения приходят, когда приложение в фоне), нужно проверить на
  устройстве.
- **Открытая сессия блокирует системные глифы.** Пока `openSession()` активна,
  индикатор Essential Notifications не горит, даже если ничего не анимируется.
  [issue #14] README тоже требует закрывать сессию, когда глифы не нужны. [doc]
- **После ребута** инициализация сразу на старте процесса может не сработать,
  помогает отложить `init()` на несколько секунд. [issue #12]
- **Зависшие глифы.** Если процесс умер с зажженными зонами, они могут остаться
  гореть. Гасить явно (`turnOff()`), потом `closeSession()`. [issue #11]
- **Одна задача за раз.** Каждый вызов `toggle/animate/turnOff/displayProgress`
  отменяет предыдущую задачу. Очередей и параллельных анимаций нет. [sdk]
  [issue #4, #18]
- `toggle()` не переключает, а просто выставляет кадр: повторный вызов не гасит.
  [sdk][issue #21]
- Красный индикатор записи на Phone (1), (4a), (4b) через SDK недоступен.
  [issue #26][sdk]

## 2. Общие механизмы

### 2.1 Жизненный цикл

LED-зоны:

```
GlyphManager.getInstance(ctx)
  .init(callback)                 // bindService, асинхронно
callback.onServiceConnected ->
  register(Glyph.DEVICE_xxxxx)    // boolean, авторизация + выбор устройства
  openSession()                   // throws GlyphException
... toggle / animate / displayProgress / setFrameColors ...
turnOff()
closeSession()
unInit()                          // unbindService
```

Matrix: то же без сессии, `GlyphMatrixManager.init` → `register(device)` →
`setAppMatrixFrame(...)` → `closeAppMatrix()` → `unInit()`.

- До `register` методы молча ничего не делают (лог `Non registed`). До
  подключения сервиса `openSession/closeSession/setFrameColors` бросают
  `GlyphException("Please use it after service connected.")`. [sdk]
- `onServiceDisconnected` обнуляет сервис внутри SDK. Переподключения нет, надо
  заново `init`. [sdk]

### 2.2 Определение модели

`Common.isXXXXX()` сравнивает `Build.MODEL` со строкой модели. [sdk]

| Метод | Сравнение | `Build.MODEL` |
|---|---|---|
| `is20111()` | equals | `A063` |
| `is22111()` | equals | `A065` или `AIN065` |
| `is23111()` | equals | `A142` |
| `is23113()` | equals | `A142P` |
| `is24111()` | contains | `A059` (покрывает `A059P`) |
| `is23112()` | contains | `A024` |
| `is25111()` | equals | `A069` |
| `is25111p()` | equals | `A069P` |
| `is25131()` | contains | `A009P` (только в новой сборке) |

`Glyph.DEVICE_xxxxx` и есть эти строки. `is24111()` истинна и для (3a), и для
(3a) Pro. `is25111()` для (4a) Pro ложна, так как там `equals`.

### 2.3 Модель кадра LED-зон

- Кадр это `int[]` длиной в число каналов устройства, индекс = канал, значение =
  яркость. [sdk]
- Яркость 0..4095. `GlyphFrame.DEFAULT_LIGHT = 4000`, его ставят `buildChannel(i)`
  и `buildChannelX()`. `buildChannel(i, light)` задает свою яркость. [sdk]
- Размеры массива: 20111 = 15, 22111 = 33, 23111/23113 = 26, 24111 = 36,
  25111 = 6, 25131 = 4. [sdk][doc]
- Индексы каналов лежат в `Glyph.Code_20111/22111/23111/24111/25111/25131`.
  Для 23113 используется `Code_23111`. [sdk]

### 2.4 Как работает `animate()`

Анимация целиком программная, на стороне SDK, в его single-thread executor.
[sdk]

```
repeat cycles:
  в течение period мс, каждые 10 мс:
    яркость = треугольник 0 -> 4096 -> 0 (полпериода вверх, полпериода вниз)
    всем ненулевым каналам кадра ставится эта яркость
    setFrameColors(...)
  в течение interval мс, каждые 100 мс:
    setFrameColors(все нули)
```

Следствия:

- форма одна: линейное «дыхание». Яркость из `buildChannel(i, light)` игнорируется,
  важно только «ноль или не ноль»;
- шаг считается целочисленно как `4096 / (period / 2)`: при `period > 8192`
  шаг равен 0 и ничего не горит, при `period < 2` деление на ноль;
- по умолчанию `period = 0`, `cycles = 1`, `interval = 0`, то есть `animate` без
  параметров ничего не покажет;
- отмена только новой командой (`turnOff()` и т.п.);
- после последнего цикла при `interval = 0` яркость остается около нуля, но
  явного нуля SDK не шлет. В issue #4 пишут, что кадр остается гореть. Надежнее
  после анимации звать `turnOff()`.

**Свои анимации.** `GlyphManager.setFrameColors(int[])` публичный: можно самому
слать кадры с любой яркостью по каждому каналу с нужной частотой (SDK шлет раз в
10 мс). Это единственный путь к волнам, бегущим огням, разным формам затухания и
к последовательностям кадров. Вызов синхронный (binder), отменяет текущую задачу
SDK. [sdk]

## 3. Общие классы и методы

### 3.1 `Common`

`is20111()` … `is25131()`, `getDeviceMatrixLength()` (25 для 23112, 13 для 25111p,
иначе 0). [sdk]

### 3.2 `Glyph`

Строки `DEVICE_*`, `DEVICE_23112_MATRIX_LENGTH = 25`,
`DEVICE_25111p_MATRIX_LENGTH = 13`, вложенные `Code_*` с индексами каналов. [sdk]

### 3.3 `GlyphFrame.Builder`

Получать через `GlyphManager.getGlyphFrameBuilder()` (подставляет устройство из
`register`). Конструктор без аргументов берет `Build.MODEL`, с `null` считает
устройство Phone (2). [sdk]

| Метод | Что делает |
|---|---|
| `buildChannel(int)` | зажечь канал на `DEFAULT_LIGHT` |
| `buildChannel(int, int)` | канал с заданной яркостью |
| `buildChannelA()` … `buildChannelE()` | вся зона целиком, см. разделы моделей |
| `buildPeriod(ms)` | длительность одного цикла `animate` |
| `buildCycles(n)` | число циклов |
| `buildInterval(ms)` | пауза между циклами |
| `build()` | `GlyphFrame` (`getChannel()`, `getPeriod()`, `getCycles()`, `getInterval()`) |

`buildChannelX()` для зоны, которой нет на устройстве, молча ничего не делает.
Билдер накапливает каналы: для нового кадра брать новый билдер. [sdk]

### 3.4 `GlyphManager`

| Метод | Что делает |
|---|---|
| `getInstance(Context)` | синглтон |
| `init(Callback)` / `unInit()` | bind / unbind сервиса |
| `register()` / `register(String device)` | авторизация, возвращает boolean |
| `openSession()` / `closeSession()` | захват и освобождение глифов |
| `getGlyphFrameBuilder()` | билдер под зарегистрированное устройство |
| `toggle(frame)` | выставить кадр статично |
| `animate(frame)` | «дыхание», см. 2.4 |
| `setFrameColors(int[])` | сырой кадр |
| `displayProgress(frame, progress)` / `(frame, progress, reverse)` | прогресс-бар 0..100 на «длинной» зоне |
| `displayProgressAndToggle(frame, progress, reverse)` | прогресс плюс остальные каналы кадра статично |
| `turnOff()` | погасить все |

`displayProgress` требует, чтобы в кадре был первый канал прогресс-зоны, иначе
`GlyphException`. Зоны по моделям см. ниже. [sdk]

`GlyphManager.Callback`: `onServiceConnected(ComponentName)`,
`onServiceDisconnected(ComponentName)`.

## 4. Платформы

Сводка моделей. Даты и коднеймы [wiki], остальное [doc][sdk].

| Модель | Код SDK | `Build.MODEL` | Коднейм | Релиз | Глифы | SDK |
|---|---|---|---|---|---|---|
| Phone (1) | 20111 | A063 | Spacewar | июль 2022 | Glyph Interface, 5 зон, 15 каналов | да, нужен ключ |
| Phone (2) | 22111 | A065, AIN065 | Pong | июль 2023 | Glyph Interface, 11 полос, 33 канала | да |
| Phone (2a) | 23111 | A142 | Pacman | март 2024 | 3 зоны вокруг камеры, 26 каналов | да |
| Phone (2a) Plus | 23113 | A142P | Pacman Pro | август 2024 | как (2a) | да |
| Phone (3a) | 24111 | A059 | Asteroids | март 2025 | 3 полосы, 36 каналов | да |
| Phone (3a) Pro | 24111 | A059P | | март 2025 | как (3a) | да |
| Phone (3) | 23112 | A024 | Metroid | июль 2025 | Glyph Matrix 25x25, 489 LED, Glyph Button | да, matrix |
| Phone (3a) Lite | нет | A001T | Galaxian | октябрь 2025 | один Glyph Light | **нет** |
| Phone (4a) | 25111 | A069 | Frogger | март 2026 | Glyph Bar, 6 каналов (плюс красный индикатор) | да |
| Phone (4a) Pro | 25111p | A069P | FroggerPro | март 2026 | Glyph Matrix 13x13, 137 LED, без кнопки | да, matrix |
| Phone (4b) | 25131 | A009P | SuperContra | июль 2026 | Glyph Bar, 4 канала (плюс красный индикатор) | да, только новая сборка AAR |

Телефоны CMF глифов не имеют, в SDK их нет.

Схемы расположения зон есть картинками в README Glyph-Developer-Kit
(`assets/Frame5..9`).

### 4.1 Phone (1), 20111

15 каналов, 5 зон.

| Зона | Константы | Индексы | Что это |
|---|---|---|---|
| A | `A1` | 0 | камера |
| B | `B1` | 1 | диагональ |
| C | `C1..C4` | 2..5 | кольцо зарядки, 4 сегмента |
| E | `E1` | 6 | точка внизу |
| D | `D1_1..D1_8` (снизу вверх) | 7..14 | полоса внизу, 8 сегментов |

`buildChannelA..E` работают все. Прогресс только на D1 (в кадре должен быть
`D1_1`). Нужен API-ключ даже на последней прошивке. Поддержка модели со стороны
Nothing закончилась 2 июля 2026. [wiki]

### 4.2 Phone (2), 22111

33 канала, 5 зон.

| Зона | Константы | Индексы |
|---|---|---|
| A | `A1`, `A2` | 0, 1 |
| B | `B1` | 2 |
| C | `C1_1..C1_16` (справа снизу к левому верху) | 3..18 |
| C | `C2..C6` | 19..23 |
| E | `E1` | 24 |
| D | `D1_1..D1_8` (снизу вверх) | 25..32 |

`buildChannelA..E` работают все. Прогресс на C1 (`C1_1`) или D1 (`D1_1`).
Индийская версия имеет `Build.MODEL = AIN065`, SDK ее учитывает.

### 4.3 Phone (2a) и (2a) Plus, 23111 / 23113

26 каналов, 3 зоны. У Plus свой код и своя строка модели, но раскладка та же и
используется `Code_23111`.

| Зона | Константы | Индексы |
|---|---|---|
| C | `C_1..C_24` (слева снизу к правому верху) | 0..23 |
| B | `B` | 24 |
| A | `A` | 25 |

`buildChannelA/B/C` работают, `D/E` пустые. Прогресс на C (`C_1`).

### 4.4 Phone (3a) и (3a) Pro, 24111

36 каналов, 3 зоны. Одна раскладка на обе модели.

| Зона | Константы | Индексы |
|---|---|---|
| C | `C_1..C_20` (слева снизу к правому верху) | 0..19 |
| A | `A_1..A_11` (сверху вниз) | 20..30 |
| B | `B_1..B_5` (справа снизу к левому верху) | 31..35 |

`buildChannelA/B/C` работают, `D/E` пустые. Прогресс на любой из трех зон
(первый канал зоны в кадре). По issue #21 `toggle()` тут не гасит кадр.

### 4.5 Phone (4a), 25111

Glyph Bar: вертикальный ряд квадратов. 6 каналов.

| Зона | Константы | Индексы |
|---|---|---|
| A | `A_1..A_6` (сверху вниз) | 0..5 |

Работает только `buildChannelA`, остальные пустые. Прогресс на A (`A_1`).
Физически квадратов 7, седьмой красный (индикатор записи), 63 мини-LED всего
[wiki]; в SDK адресуются 6.

### 4.6 Phone (4b), 25131

Уменьшенный Glyph Bar. 4 канала. Есть только в новой сборке AAR (см. 1.2).

| Зона | Константы | Индексы |
|---|---|---|
| A | `A_1..A_4` (сверху вниз) | 0..3 |

Работает только `buildChannelA`. Прогресс на A (`A_1`). Физически 5 зон с
красным квадратом, 45 LED [wiki]; в SDK адресуются 4.

### 4.7 Phone (3a) Lite

Один Glyph Light в правом нижнем углу. В системе умеет Flip to Glyph, Essential
Notifications, обратный отсчет камеры. В SDK модели нет: нет ни `Common.is…`, ни
`DEVICE_…`. Поддержать нельзя.

### 4.8 Glyph Matrix: Phone (3) и Phone (4a) Pro

Сейчас над ним не работаем, конспект на будущее.

| | Phone (3) | Phone (4a) Pro |
|---|---|---|
| Идентификатор | `Glyph.DEVICE_23112` | `Glyph.DEVICE_25111p` |
| Сторона матрицы | 25 | 13 |
| LED | 489 (круг) | 137 |
| Glyph Button / Touch | да | нет |
| Glyph Toys | все | только AOD |

Два способа использования [doc]:

1. **Из приложения.** `setAppMatrixFrame(...)` и `closeAppMatrix()`. Нужна
   прошивка от 20250801. У Glyph Toys приоритет выше: нажатие Glyph Button
   перекрывает картинку приложения. `setMatrixFrame(...)` из приложения не
   использовать, конфликтует с тоями.
2. **Glyph Toy.** Сервис с action `com.nothing.glyph.TOY` и meta-data
   `com.nothing.glyph.toy.name`, `.image`, опционально `.summary`,
   `.introduction`, `.longpress`, `.aod_support`. События приходят через
   `Messenger`: `GlyphToy.MSG_GLYPH_TOY`, данные `EVENT_CHANGE` (долгое нажатие),
   `EVENT_ACTION_DOWN/UP`, `EVENT_AOD` (раз в минуту). На (4a) Pro только AOD-тои,
   `aod_support = 1` обязателен.

`GlyphMatrixManager`: `init`, `unInit`, `register(device)`,
`setMatrixFrame(int[] | GlyphMatrixFrame)`,
`setAppMatrixFrame(int[] | GlyphMatrixFrame)`, `closeAppMatrix()`,
`setGlyphMatrixTimeout(boolean)`, `turnOff()`. [sdk]

`GlyphMatrixObject.Builder`: `setImageSource(Bitmap)` (квадратный 1:1),
`setText(...)`, `setTextStyle`, `setPosition(x, y)`, `setOrientation(deg)`,
`setScale(0..200)`, `setBrightness(0..255)`, `setReverse(bool)`. [doc][sdk]

`GlyphMatrixFrame.Builder`: до трех объектов по слоям `addTop/addMid/addLow`
(объект или сырой `int[]`), `build(Context)`, `buildWithMarquee(...)`;
`frame.render()` дает `int[]`. Размер по умолчанию 25x25. [doc][sdk]

`GlyphMatrixUtils`: `drawableToBitmap`, `convertToGlyphMatrix`,
`toGrayscaleArray`, `generateMatrixProgress`, текст в bitmap. В новой сборке еще
`GlyphMatrixNTypeUtils` (бегущий текст шрифтом NType: `animateText`,
`animateTextOnce`, `measureAnimateText`, колбэк `AnimationDataListener`). [sdk]

Анимаций у матрицы нет: приложение само шлет кадры.

## 5. Источники

- https://github.com/Nothing-Developer-Programme/Glyph-Developer-Kit (README,
  `sdk/`, issues #4, #11, #12, #13, #14, #18, #21, #23, #26)
- https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit
- https://github.com/KenFeng04/GlyphMatrix-Example-Project (пример Glyph Toy, не
  читал)
- https://nothing.tech/pages/glyph-developer-kit
- https://nothing.wiki/software/glyph_developer_kit,
  https://nothing.wiki/software/glyph_interface и страницы моделей в
  `/hardware/`
- Поддержка: `GDKsupport@nothing.tech`, https://nothing.community/t/glyph-sdk
