# plugins-spec.md — карта файлов системы плагинов mauve

Это не документация и не пересказ логики, а **индекс файлов**: что где лежит и за
что отвечает. Читай сами файлы — описания тут короткие и нужны, чтобы знать, куда
смотреть. Многие комментарии в коде на русском, это нормально.

Общая картина:

- Плагины — Python-файлы (`*.plugin`, один модуль) или Elyx-архивы (`*.eaf`, ZIP с
  `metainfo.yml`). Исполняются в CPython 3.12 через Chaquopy.
- Java-сторона (`app.exteraless.plugins`) — контроллер, движок, хуки (Xposed/LSPlant
  через libaliuhook), меню, настройки, UI, **модель разрешений** (главная фича mauve).
- `com.exteragram.messenger.plugins` — тонкие shim-классы с именами exteraGram, на
  которые завязаны сторонние плагины. Реальная логика — в `app.exteraless.plugins`.
- Python-сторона (`TMessagesProj/src/main/python`) — SDK плагинов (API exteraGram) +
  загрузчик, аудит-гейт, сканер возможностей.
- Разрешения применяются в двух местах: Python audit hooks (`audit_gate.py`) и
  Java-гейт (`PluginSinkGate.java`). Выданные права хранятся подписанными
  (`PluginGrantStore`, `SignedGrantPreferences`).

**Источник истины — только код.** Публичная документация exteraGram (plugins.exteragram.app)
написана для авторов плагинов и расходится с реальной системой. Не опирайся на неё;
при любом сомнении читай код и тесты.

Точки входа для чтения: `PluginsController.java` → `PythonPluginsEngine.java` →
`extera_utils/plugin_loader.py` → `base_plugin.py`; разрешения — `PluginPermissions.java`,
`PluginSinkGate.java`, `extera_utils/audit_gate.py`.

## Java: ядро — `TMessagesProj/src/main/java/app/exteraless/plugins/`

| Файл | Назначение |
|---|---|
| `PluginsController.java` | Центральный контроллер: реестр плагинов, загрузка/выгрузка, включение/выключение, приоритеты, диспатч хуков (отправка сообщений, запросы, апдейты), настройки плагинов. Самый большой файл системы. |
| `PythonPluginsEngine.java` | Запуск Chaquopy/CPython, инициализация Python-рантайма, вызовы в `plugin_loader`, dev-сервер (порт 42690, только developer mode). |
| `PythonBridge.java` | Обёртки вызовов Java → Python. |
| `PythonThreadState.java` | Учёт Python-потоков/GIL на стороне Java (парный к `extera_utils/thread_state.py`). |
| `PluginRuntime.java` | Контекст «чей код сейчас выполняется» (атрибуция pluginId для Java-гейтов). |
| `Plugin.java` | Модель плагина: метаданные (`__id__`, `__name__`, версия, иконка, `requires` Elyx и т.д.), состояние. |
| `PluginsConstants.java` | Константы, ключи настроек, флаги safe mode / developer mode. |
| `PluginServices.java` | Сервисы, доступные плагинам из сгенерированного байткода/прокси. |
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
| `PluginGrantStore.java` | Хранилище выданных прав. |
| `SignedGrantPreferences.java` | Подписанное хранение грантов (защита от подмены prefs плагином). |
| `PluginSinkGate.java` | Java-гейт на «стоки» в JVM: `URL.openConnection`, `Runtime.exec`, файлы, рефлексия и т.п. Пара к Python `audit_gate.py`. |
| `PluginCapabilityScan.java` | Статический скан «что плагин может делать» (вызывает `extera_utils/capability_scan.py`), показ в UI до запуска. |
| `PluginAuditJournal.java` | Журнал действий/отказов (кольцевой буфер Java + слияние с Python-гейтом). |
| `PluginDenialNotice.java` | Уведомление пользователя об отказе в доступе. |

### Подпакеты

| Файл | Назначение |
|---|---|
| `xposed/XposedHooks.java` | Регистрация/снятие хуков методов (XposedBridge, `libaliuhook.so`). |
| `xposed/HookGate.java` | Гейт на установку хуков (проверка прав, целевые методы). |
| `xposed/HookFilter.java` | Java-фильтры срабатывания хука (из `filtersJson`), чтобы не ходить в Python зря. |
| `xposed/HookStats.java`, `xposed/HookNumbers.java` | Статистика/счётчики хуков. |
| `xposed/PyMethodHook.java` | before/after-хук → Python `before_hooked_method` / `after_hooked_method`. |
| `xposed/PyMethodReplacement.java` | Хук-замена → Python `replace_hooked_method`. |
| `utils/ClassProxyFactory.java` | Генерация Java-классов-прокси (подклассы на байткоде) для Python class-proxy DSL (`extera_utils/classes.py`). |
| `menus/MenuInjector.java` | Встраивание пунктов меню плагинов (сообщение, чат, профиль, drawer и т.д.). |
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
- `org/telegram/messenger/MessagesStorage.java`, `AndroidUtilities.java`, `browser/Browser.java` — ссылки/интенты, файлы.
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
| `dev_server.py` | Dev-сервер TCP/JSON на 127.0.0.1:42690 (live reload). |
| `ui/settings.py` | Декларации элементов настроек (`Header`, `Switch`, `Selector`, `Input`, `Text`, `Divider`, …). |
| `ui/alert.py` | Обёртка `AlertDialog`. |
| `ui/bulletin.py` | `BulletinHelper` (уведомления). |
| `elyxcore/__init__.py` | Shim пакета `elyxcore` для Elyx-плагинов. |

### Внутренности движка — `extera_utils/`

| Файл | Назначение |
|---|---|
| `extera_utils/plugin_loader.py` | Java-facing загрузчик: импорт плагинов, жизненный цикл (`on_plugin_load`/`unload`), диспатч хуков, меню, настройки. Самый большой Python-файл. |
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
- `TMessagesProj/chaquopy/` — сабмодуль (форк Chaquopy, ветка `exteraless`), скрипт `exteraless/build.sh`.
  Инициализируется `git submodule update --init`.
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
