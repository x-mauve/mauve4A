# plugins-spec.md — система плагинов mauve: устройство и карта файлов

Файл состоит из двух частей. Первая — **как устроена система** и прежде всего её
защита: что где проверяется, как определяется, чей код исполняется, и какие
границы код признаёт сам. Вторая — **индекс файлов**: что где лежит и за что
отвечает. Это не пересказ логики, а ориентир, куда смотреть; детали читай в
самих файлах. Многие комментарии в коде на русском, это нормально.

**Источник истины — только код.** Публичная документация exteraGram (plugins.exteragram.app)
написана для авторов плагинов и расходится с реальной системой. Не опирайся на неё;
при любом сомнении читай код и тесты. В комментариях встречаются ссылки на
`PLUGINS-SECURITY.md`, `PLUGINS-API.md`, `PLUGINS-ELYX.md` — этих файлов в
репозитории нет.

Точки входа для чтения: `PluginsController.java` → `PythonPluginsEngine.java` →
`extera_utils/plugin_loader.py` → `base_plugin.py`; разрешения — `PluginPermissions.java`,
`PluginTrustLevel.java`, `PluginSinkGate.java`, `extera_utils/audit_gate.py`.

## Общая картина

- Плагины — Python-файлы (`*.plugin`, один модуль) или Elyx-архивы (`*.eaf`/`*.elyx`,
  ZIP с `metainfo.yml`). Все плагины исполняются **в одном интерпретаторе CPython**
  (Chaquopy) в процессе приложения. Изоляции процессов и интерпретаторов нет,
  плагины делят `sys.modules`, `builtins` и JVM.
- Через Chaquopy плагин получает живые Java-объекты (`from java.lang import ...`,
  `java.jclass`, `find_class`) и может звать любой код приложения и платформы.
  Поэтому защита стоит и в Python, и в JVM.
- Java-сторона (`app.exteraless.plugins`) — контроллер, движок, хуки (Xposed API
  поверх LSPlant, `libaliuhook.so`), меню, настройки, UI, **модель разрешений**
  (главная фича mauve).
- `com.exteragram.messenger.plugins` — shim-классы с именами exteraGram, на которые
  завязаны сторонние плагины. Реальная логика — в `app.exteraless.plugins`.
- Python-сторона (`TMessagesProj/src/main/python`) — SDK плагинов (API exteraGram),
  загрузчик, песочница импорта, аудит-гейт, сканер возможностей.
- Chaquopy — форк (`TMessagesProj/chaquopy`, сабмодуль), в нём доработан мост
  Python ↔ Java, в том числе **политика рефлексии**, на которую опирается песочница.

## Модель разрешений

**Ключи** (`PluginPermissions.java`, дублируются в `plugin_loader.py` как `PERM_*`):
`ui` (выдаётся всегда), `messages.read`, `messages.send`, `network`, `files`,
`intents`, `settings`, `hooks`, `native`. `hooks` и `native` в коде прямо названы
«full control»: с ними плагин может снять любые проверки.

**Уровни доверия** (`PluginTrustLevel.java`) стоят над разрешениями:
`ISOLATED` (по умолчанию для новых установок, только `ui`), `GATED` (разрешения по
одному, без `hooks`/`native`), `TRUSTED` (доступны `hooks`/`native`).
`PluginPermissions.getEffective` срезает выданное по уровню. Если записи об уровне
нет, уровень выводится из сохранённых разрешений и записывается.

**Согласие.** Запись `plugin_perms_<id>` пишет лист установки
(`PluginInstallHelper` → `setGranted` + `setLevel`) до установки файла. Нет записи —
у плагина только `ui` (плагин попал в каталог в обход листа). Разрешения привязаны
к **id плагина**, а не к хешу кода. Если плагин ставит другой плагин без `hooks`,
записи чистятся (`PluginsController.installPlugin`).

**Unsafe mode** (`KEY_UNSAFE_MODE`) — глобальный рубильник: все проверки в Java
и Python пропускают всё. Включается на `PluginsInfoActivity`.

**Хранение.** Гранты, уровни и unsafe mode лежат в отдельных prefs
`exteraless_plugin_grants` (`PluginGrantStore`). Каждое значение подписано
HMAC-ключом из AndroidKeyStore (`SignedGrantPreferences`). Записи без подписи или
с неверной подписью игнорируются. Если KeyStore недоступен, записи
принимаются без подписи. Если ключ пропал, все гранты сбрасываются.

## Слои принуждения

Где именно проверяются разрешения. Каждый слой закрывает дыры другого, и
ни один не задуман полным сам по себе.

Python (`extera_utils/plugin_loader.py:_install_sandbox` ставит всё сразу):

1. **Обёртки импорта** — `builtins.__import__`, `importlib.import_module`,
   `_PermissionFinder` в `sys.meta_path`. Таблицы `_IMPORT_RULES` (модуль →
   разрешение или «никогда») и `_INTERNAL_MODULES` (модули движка, плагину не
   отдаются: `audit_gate`, `plugin_loader`, `dev_server` и др.).
2. **Обёртка `open`** и fd-guards (`_install_fd_guards`).
3. **Audit hook PEP 578** (`audit_gate.py`). Проверки по месту действия: процессы
   (никогда), `ctypes` (`native`), сеть, файлы, `sqlite3`, чтение `f_globals` /
   `f_locals` у кадров (никогда). Отдельно — защищённые зоны записи: `shared_prefs`,
   `databases`, код плагинов, данные чужих плагинов. Хук снять нельзя, но вся
   логика решения живёт в обычных Python-объектах.
4. **`java.jclass` / `find_class`** (`_install_jclass_guard`, `guard_java_class`):
   таблицы `_JAVA_CLASS_RULES` (класс → разрешение) и `_JAVA_CLASS_DENIED` (классы
   движка: `PluginPermissions`, `PluginTrustLevel`, `PluginServices` и т.д.).
5. **Политика рефлексии** в форке Chaquopy (`set_reflection_policy` /
   `seal_reflection_policy`). Политика — Python-функция
   `plugin_loader._reflection_gate`. Плюс зашитый в форк список запрещённых для
   всех классов (кошелёк TON, KeyStore).
6. **Метки потоков** (`_install_thread_marking`, `_install_raw_thread_marking`) и
   охрана dynamic proxy / proxy-классов, чтобы колбэки и потоки плагина сохраняли
   атрибуцию.

Java:

7. **`PluginSinkGate`** — LSPlant-хуки на стоки JVM: `Runtime.exec` /
   `ProcessBuilder` (никогда), загрузка `.so` (`native`), `URL.openConnection` /
   `Socket.connect` / WebView / `DownloadManager` / `MediaPlayer` /
   `ImageLoader.loadHttpFile` (`network`), конструкторы dex-загрузчиков (`hooks`),
   `Class.forName` по списку имён, системные окна поверх других (`hooks`),
   отправка через `SendMessagesHelper` (`messages.send`), `ConnectionsManager.sendRequest`
   (право выбирается по имени TL-запроса), файловые стоки (`IoBridge.open`,
   `File.delete`, `Os.*`, SQLite), `getSharedPreferences` для prefs грантов и чужих
   настроек. Проверка срабатывает, только если атрибуция нашла плагин (см. ниже).
8. **Точки входа API.** `PluginServices` проверяет `hooks` / `files` / `intents` у
   переданного `pluginId`. Регистрация хуков в контроллере проверяет
   `messages.send` (хук отправки) и `messages.read` (request/update-хуки).
   Управляющие методы зовут `PluginSinkGate.refuseFromPlugin` / `refuseForeign`.
9. **`MessagesStorage.getDatabase()`** → `PluginSinkGate.allowDatabaseAccess`
   (`messages.read`).
10. **MVEL** — Java-выражения из плагина исполняются в трёх местах: условие
    видимости пункта меню (`MenuInjector`, regex-денилист `UNSAFE_CONDITION`),
    фильтры хуков (`HookFilter`), методы прокси-классов (`ClassProxyFactory`).

## Атрибуция: чей это код

От атрибуции зависит всё: если она вернула «никто», действие считается действием
приложения и не проверяется.

- **Python:** владелец кадра определяется по `co_filename` (`_owner_of_frame` →
  `_resolve_owner`). `plugin_frame_owner` смотрит весь стек (до 60 кадров) и при
  нескольких плагинах выбирает наименее доверенного (`_least_trusted`).
  `_direct_plugin_caller` берёт первый кадр вне машинерии импорта.
  `compile` / `exec` с путём чужого плагина запрещены (`audit_gate._foreign_code_owner`).
- **Python → Java:** `plugin_context` / `java_runtime_mark` ставят
  `PluginRuntime` (ThreadLocal) при диспатче хуков, в `android_utils.safe_call` и в
  размеченных потоках.
- **Java:** `PluginRuntime.current()`. Если метки нет, но на потоке исполняется
  Python-колбэк (хук на `PyInvocationHandler.invoke` → `isPythonActive`) или
  стек похож на вызов из нативного кода (`calledFromNative`), владелец достаётся
  из Python-стека (`PythonPluginsEngine.pluginFromPythonStack`).
- **Послабления по флагам потока** в `plugin_loader._context_state`:
  `engine_lookup` (SDK берёт свои классы), `engine_write` (движок пишет в
  защищённые зоны), `import_barrier`, `open_checked`.
- Почти все проверки на сбое атрибуции или моста **пропускают** действие
  (`has_permission` → `True` при исключении, `refuseFromPlugin` → `false` при
  неизвестном вызывающем). Это осознанный выбор ради совместимости.

## Что код сам признаёт границами

Это не находки, но их надо держать в голове: ревью ищет обходы **сверх** этого
и расхождения между заявленным и реальным.

- `PluginPermissions.java` (javadoc): плагин в том же процессе с доступом к Java
  обойдёт любую проверку мимо SDK. Цель модели — сделать намерения видимыми и
  ограничить случайный вред, а не построить границу против атакующего. При этом
  `README.md` подаёт безопасность как главную фичу («gets only what it declared»).
  С тех пор гейт заметно усилили, поэтому оценка «против атакующего держит / не
  держит» — часть ревью.
- `hooks` и `native` = полный контроль, уровень `TRUSTED` предупреждает об этом.
- Отложенная работа (Runnable, свой поток) закрыта частично: метки потоков и
  хук `PyInvocationHandler`.
- Горячие `java.io`-пути не хукаются. Файловая сторона в JVM держится на
  `IoBridge.open` и эвристике `directFromPython`.
- Скан возможностей (`capability_scan.py`) — подсказка для UI, а не граница.

## Прочие механики

- **Потоки.** У движка однопоточный executor (`PythonPluginsEngine`). Хуки
  запросов, апдейтов и отправки зовутся синхронно на потоках сети и UI, под GIL.
  `PythonThreadState` закрепляет thread state за часто зовущими потоками.
- **Установка.** `PluginInstallBottomSheet` (метаданные, скан, выбор уровня и прав)
  → `PluginInstallHelper` (пишет согласие) → `PluginsController.installPlugin`:
  метаданные через AST (`metadata_parser.py`), проверка id по regex, копия в
  `plugins/<id>.<ext>`, загрузка. Обновление файла с тем же id сохраняет гранты.
- **Dev-сервер** (`dev_server.py`) — только в developer mode (флаг в prefs
  контроллера), `127.0.0.1:42690`, построчный JSON. Токен лежит в
  `plugins/.devserver_token` и пишется в лог. Команды: список плагинов,
  включение/выключение, reload, `write_plugin`, удаление, отладчик, Elyx.
- **Watchdog / safe mode** (`PluginsWatchdog`). Детект зависаний по таймауту.
  Файл `plugins/.watchdog` с id активного плагина для атрибуции нативных крэшей.
  Плагин отключается при Java-исключении из Python, а повторные крэши на старте
  включают safe mode.
- **Elyx** (`elyx_runtime/`) — распаковка по хешу содержимого, bundled wheels,
  отдельное пространство имён `ElyxPlugins.<id>`.
- **pip** (`pip_controller.py`) — PyPI JSON API, только pure-Python wheels, общий
  `plugins/shared_libs/` со счётчиком ссылок.
- **Настройки плагинов** — SharedPreferences `<SETTINGS_PREFS_PREFIX><id>` на
  Java-стороне, зеркало в `plugin_settings.py`. Доступ к чужим настройкам
  проверяется в `PluginsController.pluginPrefs` и `PluginSinkGate.hookGrantStore`.

# Карта файлов

## Java: ядро — `TMessagesProj/src/main/java/app/exteraless/plugins/`

| Файл | Назначение |
|---|---|
| `PluginsController.java` | Центральный контроллер: реестр плагинов, установка, загрузка/выгрузка, включение/выключение, приоритеты, диспатч хуков (отправка сообщений, запросы, апдейты), меню, настройки плагинов, флаги safe/unsafe/developer mode. Привилегированные методы защищены вызовами `PluginSinkGate.refuseFromPlugin` / `refuseForeign` в теле метода, а не запретом класса. Самый большой Java-файл системы. |
| `PythonPluginsEngine.java` | Запуск Chaquopy/CPython, инициализация Python-рантайма, вызовы в `plugin_loader` (хуки, настройки, загрузка), `pluginFromPythonStack` для атрибуции, `pathPermission` для файловых стоков, старт dev-сервера (только developer mode). |
| `PythonBridge.java` | Статический фасад, который SDK зовёт из Python: настройки, лог, регистрация хуков и меню, проверка прав. `pluginId` приходит аргументом. В списках запрещённых классов его нет. |
| `PythonThreadState.java` | Учёт Python-потоков/GIL на стороне Java (парный к `extera_utils/thread_state.py`). |
| `PluginRuntime.java` | ThreadLocal «чей код сейчас выполняется» и счётчик активных Python-колбэков (атрибуция для Java-гейтов). |
| `Plugin.java` | Модель плагина: метаданные (`__id__`, `__name__`, версия, иконка, `requires` Elyx и т.д.), состояние. |
| `PluginsConstants.java` | Константы, ключи настроек, флаги safe mode / developer mode. |
| `PluginServices.java` | Вход SDK в подсистемы: Xposed-хуки, прокси-классы, обработчики файлов и интентов, UI-поток. Права проверяются у переданного `pluginId`. Сам класс плагину запрещён, но модули SDK держат на него ссылки. |
| `PluginMediaServices.java` | Медиа-сервисы для плагинов. |
| `PluginViewProxies.java` | Прокси/регистрация View, созданных плагинами; освобождение при уходе с экрана. |
| `ClassAliases.java` | Отображение имён Java-классов exteraGram → классы mauve (Java-часть, парная `extera_utils/class_aliases.py`). |
| `HookResult.java` | Результат хука (модификация/отмена запроса и т.п.). |
| `HookTargetCache.java` | Кэш резолва целей хуков. |
| `MenuItemRecord.java` | Описание пункта меню от плагина (тип меню, условие видимости, колбэк `on_click`). |
| `JsonUtils.java` | JSON-хелперы (null-семантика `opt()`). |
| `PluginInstallHelper.java` | Установка плагина из файла/URI/content://, валидация, копирование. |
| `PluginsBackup.java` | Бэкап/восстановление плагинов и их настроек. |
| `PluginsWatchdog.java` | Watchdog: детект падений/ANR, автоотключение проблемных плагинов, safe mode после крэша. |
| `PluginLog.java` | Логирование плагинов. |

### Модель разрешений

| Файл | Назначение |
|---|---|
| `PluginPermissions.java` | Список разрешений (сеть, файлы, хуки/Xposed, class proxy, рефлексия и т.д.) и проверки. |
| `PluginTrustLevel.java` | Уровни доверия плагина. |
| `PluginGrantStore.java` | Отдельные prefs для грантов, уровней и unsafe mode, миграция из старых prefs, `isOwnCall` по стеку. |
| `SignedGrantPreferences.java` | Обёртка SharedPreferences: HMAC-подпись записей ключом из AndroidKeyStore, проверка при чтении. |
| `PluginSinkGate.java` | Java-гейт на стоки в JVM (список — в разделе «Слои принуждения»), атрибуция на Java-стороне (`enterCheck`, `calledFromNative`, `directFromPython`), `refuseFromPlugin` / `refuseForeign` для управляющих методов. Пара к Python `audit_gate.py`. |
| `PluginCapabilityScan.java` | Статический скан «что плагин может делать» (вызывает `extera_utils/capability_scan.py`), показ в UI до запуска. |
| `PluginAuditJournal.java` | Журнал действий/отказов (кольцевой буфер Java + слияние с Python-гейтом). |
| `PluginDenialNotice.java` | Уведомление пользователя об отказе в доступе. |

### Подпакеты

| Файл | Назначение |
|---|---|
| `xposed/XposedHooks.java` | Регистрация/снятие хуков методов (XposedBridge, `libaliuhook.so`). |
| `xposed/HookGate.java` | Не проверка прав: закрывает гонку установки хука в Aliuhook (барьер до появления backup). Права на хуки проверяет `PluginServices`. |
| `xposed/HookFilter.java` | Java-фильтры срабатывания хука (из `filtersJson`), чтобы не ходить в Python зря. Поддерживает MVEL-выражения. |
| `xposed/HookStats.java`, `xposed/HookNumbers.java` | Статистика/счётчики хуков. |
| `xposed/PyMethodHook.java` | before/after-хук → Python `before_hooked_method` / `after_hooked_method`. |
| `xposed/PyMethodReplacement.java` | Хук-замена → Python `replace_hooked_method`. |
| `utils/ClassProxyFactory.java` | Генерация Java-классов-прокси (dexmaker, `InMemoryDexClassLoader`) для Python class-proxy DSL (`extera_utils/classes.py`). Методы диспатчатся в Python или исполняют MVEL. |
| `menus/MenuInjector.java` | Встраивание пунктов меню плагинов (сообщение, чат, профиль, drawer и т.д.). Условие видимости — MVEL с regex-денилистом. |
| `menus/PluginsMenuWrapper.java` | Обёртка/контейнер меню плагинов. |
| `intents/IntentsDispatcher.java` | Java-часть IntentsManager: before/after-хендлеры ссылок и интентов (парный `intents.py`). |
| `files/FilesControllerJava.java` | Реестр обработчиков открытия файлов по расширению. |
| `models/*.java` | Модели элементов экрана настроек плагина: `SettingItem` (база), `HeaderSetting`, `DividerSetting`, `SwitchSetting`, `SelectorSetting`, `InputSetting`, `EditTextSetting`, `TextSetting`, `CustomSetting`, `PluginItemFactory` (из Python-описаний → модели). Парные `ui/settings.py`. |

### UI — `ui/`

| Файл | Назначение |
|---|---|
| `ui/PluginsActivity.java` | Список плагинов. |
| `ui/PluginSettingsActivity.java` | Экран настроек плагина. |
| `ui/PluginPermissionsActivity.java` | Экран разрешений плагина. |
| `ui/PluginPermissionCell.java` | Ячейка разрешения. |
| `ui/PluginActivityLogActivity.java` | Журнал активности плагина (`PluginAuditJournal`). |
| `ui/PluginInstallBottomSheet.java` | Шит установки (метаданные, скан возможностей, права). |
| `ui/PluginsInfoActivity.java` | Информация о системе плагинов (режимы, safe mode, SDK). |
| `ui/PluginIcons.java`, `ui/PluginSliderCell.java`, `ui/PluginUiItem.java`, `ui/PluginsEmptyCell.java` | Вспомогательные ячейки/иконки. |
| `ui/components/PluginCell.java` (+ `PluginCellDelegate`) | Карточка плагина в списке. |
| `ui/components/PluginFileViewer.java` | Просмотр исходника плагина. |
| `ui/components/PluginReviewTools.java`, `ui/components/PluginAiReview.java` | Инструменты ревью кода плагина (в т.ч. AI-ревью). |
| `ui/components/SafeModeBottomSheet.java` | Шит safe mode. |
| `ui/components/PluginPermissionsDelegate.java`, `PluginSettingsLinkDelegate.java` | Интерфейсы-делегаты. |
| `ui/components/templates/UniversalFragment.java`, `UniversalView.java` | Шаблоны экранов для плагинов. |

## Java: совместимость с exteraGram — `com/exteragram/messenger/plugins/`

Shim-классы под имена exteraGram (плагины и dex-модули обращаются к ним по имени).
Делегируют в `app.exteraless.plugins`. Не удалять/переименовывать.

`Plugin.java`, `PluginsConstants.java`, `PluginsController.java`, `PythonPluginsEngine.java`,
`ui/PluginSettingsActivity.java`, `ui/PluginsActivity.java`,
`ui/components/InstallPluginBottomSheet.java`, `xposed/PyMethodHook.java`,
`xposed/PyMethodReplacement.java`. Также `com/exteragram/messenger/ExteraConfig.java`
и `package-info.java` (поверхность API, на которую опираются плагины).

## Java: точки интеграции в остальном приложении

Места, где клиент вызывает систему плагинов (хуки, меню, вход в UI). Ищи по
`PluginsController` / `app.exteraless.plugins`:

- `org/telegram/messenger/ApplicationLoader.java` — инициализация движка при старте.
- `org/telegram/tgnet/ConnectionsManager.java` — хуки запросов/ответов (pre/post request).
- `org/telegram/messenger/MessagesController.java` — хуки апдейтов.
- `org/telegram/messenger/SendMessagesHelper.java` — хук отправки сообщений.
- `org/telegram/messenger/MessagesStorage.java` — `getDatabase()` спрашивает `PluginSinkGate.allowDatabaseAccess`.
- `org/telegram/messenger/AndroidUtilities.java`, `browser/Browser.java` — открытие файлов и ссылок (обработчики плагинов).
- `org/telegram/ui/LaunchActivity.java` — открытие `.plugin`-файлов, интенты, safe mode.
- `org/telegram/ui/ChatActivity.java`, `ProfileActivity.java`, `DialogsActivity.java` — меню плагинов.
- `org/telegram/ui/Components/UItem.java`, `SharedMediaLayout.java`, `SearchDownloadsContainer.java`, `FilteredSearchView.java` — UI-интеграция (ячейки, файлы плагинов).
- `org/telegram/ui/Wallet/WalletPluginFreeze.java` — заморозка плагинов на экране кошелька.
- `app/exteraless/drawer/DrawerMenuView.java`, `MainMenuHelper.java` — пункт «Плагины» и пункты плагинов в drawer.
- `app/exteraless/settings/OpenExteraSettingsActivity.java` — вход в настройки плагинов.
- `app/exteraless/shortcuts/QuickToggleActivity.java` — быстрые переключатели.
- `app/exteraless/backup/EtgBackup.java`, `tw/nekomimi/nekogram/helpers/SettingsHelper.java` — бэкап/импорт настроек вместе с плагинами.
- `app/exteraless/crash/CrashLog.java`, `CrashReportDialog.java` — атрибуция крэшей плагинам.
- `app/exteraless/debug/PluginToggleTrace.java`, `JankProfiler.java`, `EnergyProfiler.java` — отладка/профилирование плагинов.

## Python — `TMessagesProj/src/main/python/`

### SDK (публичный API для плагинов, совместим с exteraGram)

| Файл | Назначение |
|---|---|
| `base_plugin.py` | `BasePlugin`, контракты хуков (`on_send_message_hook`, `pre/post_request_hook`, `on_update_hook`…), `HookResult`, `MenuItemData`, `MethodHook`/`MethodReplacement`. Главный файл API. |
| `client_utils.py` | Очереди, отправка сообщений, запросы TL, контроллеры (`get_messages_controller` и т.п.). |
| `android_utils.py` | UI-поток, прокси слушателей, буфер обмена, лог. |
| `hook_utils.py` | Рефлексия: `find_class`, приватные поля. |
| `file_utils.py` | Файловые хелперы (директории плагинов, чтение/запись). |
| `markdown_utils.py` | `parse_markdown`. |
| `plugin_settings.py` | Хранилище настроек плагинов (`get_setting`/`set_setting`). |
| `intents.py` | IntentsManager: before/after-хендлеры ссылок/интентов. |
| `pip_controller.py` | Мини-pip для `__requirements__`. |
| `dev_server.py` | Dev-сервер TCP/JSON на 127.0.0.1:42690 (live reload, установка плагинов). Токен в `plugins/.devserver_token`. |
| `ui/settings.py` | Декларации элементов настроек (`Header`, `Switch`, `Selector`, `Input`, `Text`, `Divider`, …). |
| `ui/alert.py` | Обёртка `AlertDialog`. |
| `ui/bulletin.py` | `BulletinHelper` (уведомления). |
| `elyxcore/__init__.py` | Shim пакета `elyxcore` для Elyx-плагинов. |

### Внутренности движка — `extera_utils/`

| Файл | Назначение |
|---|---|
| `extera_utils/plugin_loader.py` | Java-facing загрузчик и **песочница**: обёртки импорта и `open`, атрибуция по стеку, `has_permission` / `require_permission`, guard для `jclass` и рефлексии, метки потоков, импорт плагинов, жизненный цикл (`on_plugin_load`/`unload`), диспатч хуков, меню, настройки. Самый большой файл системы (~3.8k строк). |
| `extera_utils/metadata_parser.py` | Статическое чтение метаданных (`__id__`, `__name__`, …) через AST, без исполнения. |
| `extera_utils/audit_gate.py` | Гейт на audit hooks (PEP 578): применение разрешений на уровне действий (сеть, файлы, subprocess, импорт…). |
| `extera_utils/capability_scan.py` | Статический скан возможностей плагина по исходнику. |
| `extera_utils/class_aliases.py` | Имена Java-классов exteraGram → mauve. |
| `extera_utils/classes.py` | Class-proxy DSL (связь с `ClassProxyFactory.java`). |
| `extera_utils/text_formatting.py` | HTML/Markdown → entities Telegram. |
| `extera_utils/hook_profile.py` | Профилирование хуков. |
| `extera_utils/thread_state.py` | Состояние потоков (пара `PythonThreadState.java`). |

### Elyx (структурированные плагины, `.eaf`) — `elyx_runtime/`

`__init__.py` (вход), `loader.py` (загрузка/выгрузка), `metadata.py` (`metainfo.yml`),
`archive.py` (распаковка, refmap, wheels), `namespace.py` (изолированный импорт),
`facade.py` (публичный объект `elyx`), `settings.py`, `localization.py`, `assets.py`,
`dev_commands.py` (live-sync через dev-сервер), `errors.py`, `utils.py`.
Ссылки на «PLUGINS-ELYX.md §N» в докстрингах — внешняя спека Elyx, в репозитории её нет.

## Сборка и рантайм

- `TMessagesProj/build.gradle` — плагин `com.chaquo.python`, блок `python {}`, `ndk.abiFilters`
  (только `arm64-v8a`, `x86_64`), задачи сборки Chaquopy-рантайма из сабмодуля
  (`CHAQUOPY_BUILD_PYTHON`).
- `TMessagesProj/chaquopy/` — сабмодуль (форк Chaquopy 17.0.0, ветка `exteraless`), скрипт
  `exteraless/build.sh`. Инициализируется `git submodule update --init TMessagesProj/chaquopy`.
  Что изменено — `exteraless/README.md`. Для ревью важны:
  - `product/runtime/src/main/python/java/chaquopy.pyx` — `set_reflection_policy`,
    `seal_reflection_policy`, `get_reflection_policy`, зашитый `DENIED_CLASS_PREFIXES`;
  - `java/class.pxi` — проверка политики на рефлексивных вызовах, `GuardedMethod`;
  - `java/utils.pxi` (`cast`), `java/conversion.pxi` — остальные точки `class_denied`;
  - тесты `product/runtime/src/test/python/chaquopy/test/test_reflection_gate.py`,
    `test_denied_classes.py`.
- `TMessagesProj/proguard-rules.pro` — `-keep class app.exteraless.plugins.** { *; }` и прочее,
  достижимое только из Python через рефлексию. Ломается при R8 — смотри сюда.
- `TMessagesProj/src/main/AndroidManifest.xml` — intent-filter на открытие `*.plugin` файлов.
- `TMessagesProj/src/main/python/.gitignore`.

## Ресурсы

- `TMessagesProj/src/main/res/values*/strings_oe_plugins.xml` — строки UI плагинов
  (en, ru-rRU, ar, es, pl-rPL, zh-rCN).
- `TMessagesProj/src/main/res/drawable/msg_plugins_*.xml`, `plugins_filled*.xml` — иконки.

## Тесты — `tests/`

Статические проверки на чистом CPython (`python3 -m pytest tests/ -q`). Нужен внешний
корпус плагинов (`EXTERALESS_PLUGIN_CORPUS`, `EXTERALESS_PLUGIN_ARCHIVES`), без него skip.
Подробности — `tests/README.md`.

| Файл | Что проверяет |
|---|---|
| `conftest.py`, `corpus.py`, `baseline.py`, `javaapi.py` | Инфраструктура: загрузка корпуса, baseline-сравнение, индекс Java API. |
| `test_plugin_parse.py` | Парсинг плагинов корпуса, метаданные, базовый класс. |
| `test_plugin_settings_types.py` | Типы элементов настроек. |
| `test_python_runtime.py`, `test_regressions.py` | Рантайм SDK, регрессии. |
| `test_python_sdk_signatures.py`, `test_sdk_surface.py`, `test_sdk_nx_port.py`, `test_shim_surface.py` | Сигнатуры и поверхность SDK/shim против использования в корпусе. |
| `test_reference_parity.py` | Паритет с эталоном exteraGram. |
| `test_java_refs.py`, `test_java_call_shape.py`, `test_dynamic_proxy_shape.py`, `test_class_alias_bridge.py` | Ссылки плагинов на Java-классы/методы, арность, прокси, алиасы (R8-маппинг из `TMessagesProj/build/outputs/mapping/release/`). |
| `test_capability_scan.py`, `test_reflection_gate.py`, `test_storage_gate.py` | Модель разрешений: скан, гейт рефлексии, гейт хранилища. |
| `test_elyx_archive.py`, `test_elyx_namespace.py`, `test_elyx_library_plugins.py` | Elyx-рантайм. |
| `test_music_plugin_compat.py`, `test_native_plugin_compat.py` | Совместимость конкретных классов плагинов. |
| `tests/baselines/*.json` | Известные пробелы; удалять записи при исправлении. |

Не относятся к плагинам: `test_bot_privacy.py`, `test_message_bubble_layout.py`,
`test_send_preview_focus.py`, `tests/bot_privacy/`.

## Документы

- `AGENTS.md` — правила репозитория (атомарные коммиты, `MAUVE_VERSION_CODE`, CHANGELOG).
- `README.md`, `CONTRIBUTING.md` — общее описание и модель разрешений с точки зрения пользователя.
- `tests/README.md` — корпус и тесты движка.
