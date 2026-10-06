# Release Notes

## v3.2.3
**Журнал отвечает на вопрос «можно ли выполнить файл»**
- При отказе запуска proot пишется результат прямой проверки прав, владелец, контекст SELinux и флаги монтирования — по каждому неудачному запуску.

## v3.2.2
**Песочница: подробности причин сбоя**
- В журнал пишутся права на нативные файлы, контекст SELinux, флаги монтирования, argv запуска и полный вывод proot.
- Отличается «процесс не стартовал» от «процесс отработал с ошибкой».

## v3.2.1
**Полная сборка встаёт сама**
- В `full` компоненты Debian и proot устанавливаются при первом запуске, без вопроса «доскачать?».

**Зеркало компонентов на сетевом диске**
- Если адрес из настроек недоступен, приложение пробует запасные адреса и публичную папку на Яндекс.Диске.

**Запланированные задачи не пропадают при обновлении**
- Список задач читается построчно: одна нечитаемая задача больше не убирает остальные, и непонятные ошибки видны в журнале.

**Список компонентов**
- Из него убрана служебная запись о первом запуске, у которой не было ссылки.

## v3.2.0
**Два дистрибутива: `full` и `lite`**
- `Katya-<версия>-full.apk` — Debian-окружение и proot внутри сборки. Песочница поднимается сразу после установки, без единого запроса в сеть.
- `Katya-<версия>-lite.apk` — примерно на 100 МБ легче: ничего лишнего внутри, все компоненты загружаются по требованию.
- Выбирайте `full`, если важно, чтобы всё работало сразу и без интернета; `lite` — если сборка должна быть компактной.

**Компоненты ставятся из сборки, а если её нет — с зеркал**
- Установка сначала смотрит внутрь APK и, только если нужного файла там не нашлось, идёт в сеть.
- Если адрес из настроек недоступен, автоматически пробуются запасные адреса — компонент не остаётся «не установлен» из-за одного недоступного хоста.

**Приложение похудело на 21 МБ**
- Убрана неиспользуемая полезная нагрузка из сборки.

## v3.1.9
**Приложение восстанавливается после очистки данных**
- База проверяется при открытии и, если схемы за ней нет, создаётся заново. Раньше после очистки данных установка оставалась нерабочей: компоненты не ставились, туннели не поднимались.

**Импорт конфигурации — без перегруженного списка**
- По умолчанию показывается количество изменений одним предложением, полный список свёрнут под «Показать подробности».

**Транспорт теперь один**
- Домашний сервер по SSH и VLESS исключают друг друга в обе стороны.

## v3.1.8
**Песочница запускается**
- Приложение поднимает Debian-окружение с proot: команды, файлы, локальные серверы, Node.js и туннели.

**Голос и микрофон**
- Системный синтез речи доступен всегда, добавлено управление маршрутизацией звука и микрофоном Bluetooth-гарнитуры.

**Катя знает о своих возможностях**
- В системный промпт добавлено описание собственной архитектуры: голос, микрофон, манифест и права доступа.
- Новый навык `/self-modification`: доработка собственного манифеста и запросов доступов.

**Статус соединения**
- Называет конкретную модель вместо названия сервиса и показывает, сколько идёт ожидание ответа.

**Нагрузка на телефон**
- Загрузка процессора считается честно, по интервалу, а не как среднее с момента загрузки телефона. В статус добавлена температура.

**Интерфейс**
- Переключатель темы перенесён в начало вкладки «Общее».
- Единый шеврон у сворачиваемых блоков.
- «Быстрые действия» — отдельный блок с заголовком слева.

## v3.1.7
**Туннели включаются и выключаются по-настоящему**
- Переключатели VLESS и домашнего сервера останавливают транспорт, а не только меняют флаг.

**Импорт и экспорт**
- Понимаются конфигурации, сохранённые предыдущими версиями.
- Свой собственный файл импортируется без изменений.
- Импорт не затирает настройки, которых в файле нет.

**Настройки прокси**
- Конфигурация VLESS видна и редактируется после восстановления из старого бэкапа.

**Поля входа DeepSeek**
- Логин и пароль не очищаются при переключении вкладок и неудачной попытке входа.

**Диалог**
- Статус показывает реальную доступность модели, а не состояние радиоинтерфейса.

## v3.1.6
Третий полевой раунд. Начали с краша — он оказался моей ошибкой, а дальше по логам нашлись две настоящие.

**Вкладка «Общее» падала при каждом входе (п.11)**
- Новая карточка «Перезапросить все права» брала проверку root через `koinInject<CommandExecutor>()`. А `CommandExecutor` — это `expect class` с пустым конструктором, в Koin он **не зарегистрирован**; онбординг создаёт его через `remember { CommandExecutor() }`. Поэтому каждый вход на вкладку ронял `NoBeanDefFoundException` прямо в композиции.
- Именно поэтому ничего не было в логах: падение происходило до всякого логирования.

**Песочница и DeepSeek: запуск не мог случиться в принципе (п.4, п.14)**
- В логе каждый запуск проваливался одинаково: `Cannot run program ".../libproot-loader.so": error=13, Permission denied`. То есть нативные файлы лежали **без exec-бита** — их распаковал старый установщик, упавший на `ETXTBSY` до `chmod`, а компонент с тех пор не перекачивали. Переход на loader лишь перенёс тот же отказ на другой файл.
- Теперь права восстанавливаются на месте, без требования перекачать: чиним биты при создании исполнителя, потому что это единственная точка, через которую проходит любой запуск.

**Карточка VLESS врала работающему туннелю (п.2)**
- По логу туннель был жив: 313 проверок вернули 200. А карточка писала «Не доступен».
- Статус считался как `vlessConnected && localPortOk`, где второе — 12-секундная догадка UI, способная устареть, тогда как менеджер проверяет через туннель каждые 2 секунды. Теперь вердикт менеджера единственный авторитетный.
- Собственная причина менеджера («Туннель не отвечает, попытка N») собиралась из flow и **никогда не выводилась** — карточка физически не могла показать, в чём дело.

**Без конфига прокси нечего было проверять (п.1)**
- Цикл проверки крутился каждые 12 секунд, ни разу не посмотрев, есть ли конфиг: опрашивал порт, где никто не слушает, и запускал xray. Строка «Прокси не заданы» была в том же `when` — просто не использовалась.

**Введённый логин не стирался (п.5)**
- Поля логина и пароля DeepSeek лежали в `remember`, но всё тело карточки — за `if (isExpanded)`. Сворачивание уводило их из композиции вместе с состоянием.

**MCP: сказано, почему не подключилось (п.7)**
- Обе точки подключения читали `isSuccess` и **выбрасывали результат**. Отказ соединения, битый URL и отказ авторизации выглядели одинаково — «Ошибка», и ничего не попадало в лог. Теперь причина видна в карточке и в логе.

**Прочее**
- «Серверы MCP», «Навыки» и «Инструменты» получили полукруглый переключатель, как включение звука, вместо простого треугольника.
- Кнопка «Добавить навык» — сразу под шапкой, а не под всем списком.
- Текст про инструменты переехал внутрь своего спойлера.
- Встроенные навыки получили русские названия: список показывал «Android App Api», «Bypass Proxy», «Create Skill», хотя описания были уже на русском.

### Как проверять
Вкладка «Общее» — открывается без падения. Песочница — команда и инструмент. DeepSeek — вход. Карточка VLESS — соответствует реальному состоянию и показывает причину, если туннель не поднялся. MCP — видна причина ошибки.


## v3.1.5
Второй полевой раунд. Начли с логов и нашли одну причину сразу у трёх жалоб.

**Песочница, инструменты и DeepSeek были сломаны одной ошибкой (п.14, п.9, п.5)**
- Установка нативных библиотек писала прямо поверх рабочего каталога. Пока xray запущен, запись в его файл падала с `ETXTBSY` — а исключение срабатывало **до** `setExecutableRecursive()`. В итоге все бинарники ложились без exec-бита, и `libproot.so` не запускался вообще: `error=13, Permission denied`. Это вся песочница, а значит и все инструменты, которые идут через шелл.
- Установка идёт через staging, права ставятся там, файлы заменяются `rename(2)` — он не требует открытый файл и работает поверх работающего бинарника. После установки проверяется, что proot исполняемый, иначе понятная ошибка вместо тишины.
- `recheckInstallation()` проверял только `exists()`, поэтому UI показывал зелёную галочку у набора, который физически не мог запуститься. Теперь требует `canExecute()`.

**Песочница запускается так же, как отдельная (п.14)**
- Вендорный `libproot-loader.so` лежал рядом, путь к нему вычислялся — и не использовался: код запускал `libproot.so` напрямую. Раньше именно поэтому встроенная песочница заметно отличалась от скачиваемой отдельно. Теперь запуск через loader, 32-битный вариант подключён для 32-битных программ, с откатом на прямой запуск, если loader'а нет.

**Free DeepSeek Proxy: сессии, порт и удаление (п.5)**
- Удаление прокси ничего не давало: процесс оставался жить со старым auth-файлом, а менеджер продолжал указывать на удалённый инстанс. Добавлена остановка с переходом на оставшийся.
- Несколько прокси делили одну сессию DeepSeek и мешали друг другу. Вендор сам изолирует сессии по агенту, поэтому вместо N контейнеров на одном порту запросы получили `x-agent-session`.
- Прокси стартовал на `11434` — это порт ollama и дефолтный порт SSH-туннеля. Он либо не мог занять порт, либо отвечал вместо ollama, пока приложение продолжало стучаться туда за моделями. Перенёс на `9655`.

**Инструменты перестали показываться как код (п.9, п.13)**
- Распознавался только один формат вызова инструмента, а FreeDeepseekAPI документирует четыре. Остальные три уезжали в чат текстом — «в интерфейсе видно код с tool call, а на телефоне ничего не происходит». Теперь разбираются все четыре, включая незакрытый блок, если ответ оборвался.
- Через `model-capabilities` определяется, какая модель вообще умеет инструменты, и неподходящие помечаются в списке.

**Импорт наконец доходит до экрана (п.8)**
- Вся вкладка «Сервера» брала значения через `remember` без ключей — то есть один раз, при первом появлении. После импорта хранилище менялось, прокси реально поднимался, а экран показывал старое: «сервис работает, а названия нет, и добавить/удалить нельзя». Ключи проставлены.

**Аудио: фокус и пауза, а не громкость**
- При озвучке и при включении микрофона приложение забирает аудиофокус, ставит плеер на паузу и по окончании возвращает фокус и включает воспроизведение обратно. Только если музыка играла до этого — если не играла, она не запускается.
- Громкость при этом не меняется вовсе, громкость пользователя не страдает.

**Планировщик заговорил по-русски**
- Ручное добавление задач работало, инструменты модели тоже были на месте, а промпт с прямым требованием их вызвать — тоже. Но всё это было на английском. Переведены раздел «Автоматизация», описания `schedule_task` / `cancel_task` / `list_tasks`, список задач и guidance по навыкам.

**Прочее**
- Приветствие говорит один раз, а не при каждом заходе в настройки.
- Кнопка «Перезапросить все права»: Magisk отзывает права после переустановки, и повторно спросить было нечем.
- «Слух», «Речь» и «Песочница» — раздельные сворачиваемые островки.
- «Навыки» и «Серверы MCP» — по центру, со своим переключателем; у инструментов появилось описание.
- Больше мусора в логах: полные стектрейсы на каждый ретрай убраны, попытки достучаться до несуществующего локального сервера больше не повторяются.

### Как проверять
Песочница: команда в sandbox, инструмент, DeepSeek — после установки компонентов. Импорт: конфигурация с VLESS и SSH, затем проверка, что карточки заполнены и удаляются. Аудио: включённая музыка должна встать на паузу на время ответа и продолжиться после; при выключенной музыке она не должна запускаться. Планировщик: попросить что-то отложенное и убедиться, что задача появилась, а не «сделаю позже».


## v3.1.4
### Что изменилось
Десять пунктов полевой обратной связи. Каждый — с найденной причиной, а не с symptom fix.

**Обновление больше не просит переустановки (п.1)**
- APK не накладывался на предыдущий, потому что мог быть подписан другим ключом. Теперь CI **проверяет сертификат собранного APK** и останавливает релиз, если SHA-256 не совпадает с ключом, которым подписан весь текущий ряд релизов. Раньше проверялось только, что секрет с ключом вообще существует — этого мало: секрет мог оказаться не тем ключом, а Gradle — тихо взять debug-подпись.

**Первое приветствие наконец слышно (п.2)**
- Катя молчала на первом экране: движок TTS на холодном старте создаётся асинхронно, а озвучка запускалась раньше, чем он был готов, — приветствие уходило в пустоту. Теперь оно ждёт готовности движка.
- Скорость речи по умолчанию поднята с 1.2 до 1.5 — на 1.2 речь тянулась. Уже сохранённую скорость не трогаем.

**God Mode больше не говорит «root нет», когда он есть (п.3)**
- Проверка `su` упиралась в 15 секунд и возвращала таймаут, пока Magisk ещё показывал свой диалог «выдать права?» — приложение объявляло отказ, хотя права выдавались секундой позже. Таймаут 60 секунд, плюс до 10 повторов с индикатором ожидания. Таймаут больше не считается отказом и не кэшируется как «прав нет».

**«Подключить всё» и «Добавить всё» — внутри диалога (п.4)**
- Кнопки были на вкладке, а открывали модальное окно — то есть в момент, когда они нужны, их не было видно. Теперь обе внутри окна выбора, прогресс на кнопке.

**Инструменты: всё включено и спрятано (п.5)**
- Стена переключателей вынесена в спойлер, свёрнут по умолчанию.
- Три инструмента показывались выключенными, хотя `isToolEnabled()` по умолчанию `true` — тумблер врал, инструмент при этом был активен. Исправлено: по умолчанию включено всё.

**Настройки собраны на «Общих» (п.6, п.7)**
- Управление слухом и речью и выбор песочницы перенесены на вкладку «Общие» — к агенту они отношения не имели.
- Альтернативные ссылки на компоненты (rootfs, Proot, Xray) переехали под выбор песочницы, где их и ищут, вместо отдельной вкладки «Серверы».

**FreeDeepSeek: вход по кнопке, без белого экрана (п.8, п.9)**
- Проверки подключения больше нет в момент добавления сервиса, где ещё нет ни логина, ни пароля. Логин, пароль и кнопка «Подключить»; выбор модели появляется **после** успешного подключения.
- Исчез белый артефакт: headless WebView рисовал белый холст. Теперь он прозрачный и при нулевой альфе — вклад работает, на экране ничего нет.
- Больше нет вечной перезагрузки «токен устарел». Устаревший токен из `localStorage` распознаётся и **один раз** зачищается вместе с `sessionStorage` и cookie, после чего страница возвращается на форму входа. Раньше токен оставался на месте, и каждая проверка находила то же самое значение.
- Статус пишет один источник: отсчёт секунд больше не затирает реальные сообщения, и цифры не зацикливаются на нуле.
- Файл сессии теперь проверяется внутри контейнера с нормальным таймаутом, а если он туда не виден — доставляется через сам proot. Раньше проверка падала, а сервер стартовал без сессии и отвечал «нет challenge».
- У поля пароля появился глаз.

**Импорт снова показывает правду (п.10)**
- «Изменений нет» при явных различиях: если в бэкапе не было блока `app_settings`, diff считался по пустому объекту. Теперь верхнеуровневые настройки читаются как источник, а служебные ключи (версия, базы разговоров, состояние) в сравнение не попадают.
- VLESS больше не остаётся «подключённым» после импорта того, что его не описывает. «Подключено» — состояние в памяти, оно жило своей жизнью; теперь после импорта флаг сбрасывается, а туннель переоценивается по импортированным значениям.

**Конфиденциальность логов и бэкапов (сверх десяти пунктов)**
Ровно то, чего пользователь не замечает, пока не отдаст кому-то баг-репорт или скриншот.
- Авторизация DeepSeek больше не печатает префиксы живого токена и антибот-заголовков в logcat. В лог уходит только длина и короткий SHA-256 — два значения по-прежнему можно различить, но сам секрет не покидает процесс. Раньше туда уходили первые 20–16 символов токена, что вместе с отчётом о баге — утечка рабочих учётных данных.
- Конфигурация MCP серверов помечена секретной. У каждого сервера есть свои `headers`, а в них регулярно лежит `Authorization: Bearer …`; значение целиком попадало в экран сравнения при импорте открытым текстом. Теперь там маска, как у паролей и ключей, — в файле бэкапа настоящие значения остаются, маска только на экране.

### Как проверять
Обязательно на устройстве: накладка APK поверх 3.1.3-fix без удаления; God Mode с включённым Magisk (сверить, что права выдаются); знакомство с включённым голосом; импорт бэкапа, сделанного **до** появления `app_settings`.

Отдельно на сборке: `apksigner verify --print-certs` должен показать SHA-256 `79:FF:04:D2:…:2C:70`. Если отличается — сборка подписана отладочным ключом и обновление поверх релиза не пройдёт. Локально для этого нужны `KEYSTORE_FILE`, `KEYSTORE_PASSWORD` и `KEY_ALIAS`; без них Gradle молча берёт debug-подпись, и это теперь описано в README явно.

## v3.1.3-fix
### Что изменилось
Это один релиз на всю обратную связь полевых тестов: подписи, знакомство, God Mode, навыки, VLESS, DeepSeek, бэкап и планировщик.

**Бэкап — теперь весь, и импорт показывает разницу до применения**
- Экспорт пишет **все** настройки, а не выборочный список. В бэкап попадает только то, что отличается от заводского, а заводской берётся запуском настоящих геттеров на пустом хранилище — второй таблицы, которая могла бы разъехаться, теперь нет.
- В диалоге импорта выбор «Дополнить» / «Заменить» вместо переключателя, и **построчный diff** `было → станет` со счётчиком изменений. «Дополнить» берёт только нестандартные значения, «Заменить» делает файл всей конфигурацией и возвращает отсутствующее к заводскому. Оба diff считаются при чтении файла — переключение мгновенное.
- Пароли, API-ключи и ключи VLESS/серверов в diff показаны как `••••••••`; в сам файл пишутся как есть.
- **Читать и применять разделили.** Раньше zip распаковывался на диск сразу после выбора файла: открыл предпросмотр и отменил — а база разговоров уже заменена. Теперь до подтверждения не пишется ничего.
- **Zip Slip закрыт.** Проверка пути смотрела только на префикс `vosk/`, который обходится именем вида `vosk/../../databases/evil.db`. Теперь имя проверяется, а итоговый путь подтверждается на нахождение внутри каталога назначения.

**Катя действует, а не обещает (п.15)**
- Модель отвечала «Хорошо», не вызывала инструмент, и напоминание создавалось только на следующем heartbeat — через полчаса. Ответ без вызова инструмента, который целиком состоит из обещаний или заявляет о действии, которого не было, теперь получает один повторный запрос в том же такте.
- Повторный запрос — служебный: он не попадает в чат и в сохранённую переписку и не тратит бюджет итераций инструментов.

**Планировщик переживает перезагрузку**
- Android стирает все будильники при ребуте, после чего список задач выглядел исправным и просто переставал срабатывать. `BOOT_COMPLETED` (включая варианты QUICKBOOT/LOCKED_BOOT) перевзводит и задачи, и heartbeat. Восстановленный бэкап делает то же самое.

**Знакомство, God Mode, навыки, DeepSeek, VLESS**
- Знакомство: четыре кнопки на первом экране, затем выбор свободы. Модель, выбранная при первом запуске, сразу выставлена во всех Legacy Free-инстансах.
- God Mode: «Разрешить все!» — Катя сама выдаёт себе нужные права и показывает отчёт, а не говорит «готово» вслепую.
- Навыки и MCP: кнопки «добавить всё, что вижу» и «подключить всё», прогресс на самой кнопке.
- DeepSeek: без поля токена — лампа, логин/пароль и кнопка; прогресс прямо в карточке; у каждого аккаунта своя сессия.
- VLESS: зелёная галочка только при реальном подключении и живой проверке, причина в карточке.
- Поле API-ключа показывается только там, где ключ действительно нужен.

### Нативные компоненты: настоящая причина, почему VLESS не подключается
Оказалось, `libxray.so` лежал **только** в arm64-архиве:
- `native-x86_64.zip` — был только proot. Туннель запускать было нечем. Добавлен официальный Xray-core для android/amd64.
- `native-armeabi-v7a.zip` — тоже только proot, официальной сборки xray под Android arm32 не существует. Собран из официального исходника `github.com/XTLS/Xray-core` `v26.3.27` (commit `d2758a0`) ровно так же, как уже работающий arm64-бинарник: статический `linux/arm`, `CGO_ENABLED=0`, `GOARM=7`, go1.26.1:
  ```
  CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=7 \
    go build -trimpath -ldflags "-s -w" -o xray-linux-arm32 ./main
  ```
  Проверено: ELF 32-bit LSB, machine=40 (ARM), EABI5, статический, без интерпретатора.
- Плюс **общий баг доверительных сертификатов**: наши xray собраны как `linux/*`, а Go добавляет `/system/etc/security/cacerts` только при `GOOS=android`. Статическая сборка не находила корней вообще, и VLESS с `security=tls` падал на проверке сертификата. Теперь при запуске xray выставляется `SSL_CERT_DIR=/system/etc/security/cacerts` (и для root-, и для обычного запуска) — чинит и arm32, и arm64.
- Если бинарника почему-то нет (битая перекачка), приложение **больше не просит root вслепую**: проверка идёт до запроса прав, и в карточке прямое объяснение — перекачай компонент или пользуйся обычным подключением.

Нативные архивы лежат в отдельном релизе `v3.1.3` (seed-тег): патчи не обязаны менять URL скачивания.

### Изменения в сборке
- **Стабильная подпись:** релиз подписывается тем же ключом, что и v3.1.1/v3.1.2, поэтому обновление ставится поверх прежней установки. Ключ лежит в GitHub Secrets и в репозиторий не попадает; сборка без секрета падает.
- Тесты: 19 штук, первые в проекте. Гейт теперь запускает `:composeApp:testAndroidHostTest` — до этого `testFossDebugUnitTest` был зелёным, не выполнив ни одного теста.

### Проверено
- Подпись: APK v3.1.2 и этот релиз — один сертификат (CN=KatYa, SHA-256 `79FF:04:D2:…:2C:70`), v2-схема, versionCode 3204. Скачанный из релиза APK проверен `apksigner` — не только локальная сборка.
- Seed-URL: `native-arm64-v8a.zip`, `native-armeabi-v7a.zip`, `native-x86_64.zip` отдают 206; внутри arm32/x86_64 теперь есть `libxray.so` (проверено распаковкой).
- 19 unit-тестов, `spotlessCheck`, `assembleFossDebug` — зелёные.
- Требует прогона на телефоне: обновление поверх установленной 3.1.2, песочница, VLESS-туннель на 32-битном устройстве, DeepSeek, God Mode, цвета.

## v3.1.2
### Feedback round (field tests)
- **VLESS subscription:** subscriptions that redirect with HTTP 307 (http→https) are now followed manually; BOM/CR stripped, first `vless://` link picked. The app no longer fails with "Could not find vless:// link".
- **VLESS proxy card redesign:** the proxy launch button and status moved into a "VLESS Прокси" section with a live status at the title level — "Подключен" (green), "Проверка доступа" (warning/countdown), "Не доступен" (cross), "Отключен" (gray dot). Each saved config row shows the name (fallback from the URI `#fragment`), an edit (pencil) and delete action; the input form hides once configs exist behind a "Добавить новый" link.
- **Alternative links:** incompatible-ABI components are hidden from the "Alternative links" list (only binaries matching the device ABI are shown).
- **DeepSeek headless auth:** the browser/WebView is no longer shown — the user enters Email and Password right in the Free DeepSeek card and taps "Подключить". Login runs under the hood (hidden WebView) with a "проверяю доступность… N с" countdown, everything is written to logs; when connected a green lamp replaces the form and the model list becomes available.
- **Adaptive text color:** settings text color now adapts to the current theme (OLED black vs. light/dark) instead of hard-coded white/onSurface.
### Added & Improved
- **Downloadable Components Registry:** Sandbox stack (Debian rootfs + native proot/xray binaries) now lives in a component registry with per-ABI seeds, background downloads with progress in the notification, and editable "Alternative links" on the Servers tab.
- **Slim APK:** native binaries moved out of the APK (downloaded on demand per ABI) — much smaller install.
- **Working default URLs:** Debian rootfs links fixed for proot-distro v4.29.0 (`arm` instead of `armhf`) and native binaries now point to release `v3.1.1` — the sandbox installs out of the box.
- **Corrupted TAR fix:** archive compression is now detected by magic bytes (xz/gz/bz2), not by the temp file name, which read XZ as raw TAR and broke rootfs extraction with "Corrupted TAR archive".
- **VLESS resilience:** proxy is used only while the tunnel is actually Connected; after 3 failed checks the app announces fallback, stops retrying and shows "VLESS недоступен — стандартный канал"; when the tunnel recovers it switches back. No more dead `127.0.0.1:10809` ECONNREFUSED on every request.
- **Sandbox auto-repair:** broken/missing rootfs is detected and reinstalled (wipe + reinstall), with a patient wait for a slow first install.
- **Single source of truth for version** (`gradle/libs.versions.toml` → generated `Version.kt`), label and UI show the same app version.
- **Agent visibility hint:** "Сначала включите озвучку на вкладке «Агент»" caption with a one-tap shortcut to the Agent tab.
- **DeepSeek auth:** clicking "Войти" immediately injects the auto-login JS (no need to wait for a page reload).
- **Vocal modes:** added system-prompt tuning for "Собеседник" (Conversational) and "Сыскун" (Detective) agent modes.
- **Documentation:** full "How to Use" step-by-step guide in README (RU/EN).
### Fixed
- Rootfs download HTTP 404 for armeabi-v7a (wrong arm arch name in proot-distro assets).
- "Corrupted TAR archive" breaking rootfs extraction for arm64/x86_64.
- Requests hanging on a dead VLESS proxy instead of falling back to the standard channel.
- VLESS status indicator now reflects the real tunnel state (red/green/gray).
- SSH tunnel retry storm (exactly 3 attempts, throttled error logs).

### Feedback round (field tests)
- **VLESS subscription parsing:** subscriptions that redirect with HTTP 307 (http→https) are now followed manually; BOM/CR stripped, first `vless://` link picked, base64 payloads still supported. The app no longer fails with "Could not find vless:// link".
- **Model status honesty:** removed the three blinking dots from the top bar; when a model fails, a visible banner shows which one failed, why, and the next model being tried. Free/keyless endpoints (kai9000) fail fast instead of spinning through ~30s of blind retries.
- **DeepSeek auth dialog:** close button hidden while the autopilot or anti-bot-header wait is in progress (with a live countdown instead), WebView renders white with no cache (no more black screen on the second open), added a "⟳ Обновить" button.
- **TTS defaults:** Speed slider moved to the top, Pitch below; speed range widened to 0.5–3.0 and the default rate raised to 1.2 so Katya speaks noticeably faster on a fresh install.
- **First-run "Давай познакомимся":** skippable spoken intro with a default-model picker (Free Fast / Free Expert), a "Пропустить" button and a persistent "Не озвучивать знакомство" option.

## v3.1.6-git
### Added & Improved
- **README Update:** Полностью переработан README, добавлено описание проекта, структура, инструкция по сборке, переключатель RU/EN.

## v3.1.5-git
### Added & Improved
- **VLESS Proxy:** Запуск Vless переведен на нативное выполнение без использования `proot`. Если включен God Mode, он запускается нативно от имени root, что решает проблему с отсуствием библиотек и скоростью.
- **DeepSeek Auth:** Реализованы нативные поля для Email и Пароля в диалоге с инъекцией JavaScript (WebView) для автоматического входа.
- **Proot Sandbox:** Исправлен вылет `libtalloc.so.2` путем динамического создания symlink и проброса `LD_LIBRARY_PATH`.
- **UI Почты:** Дизайн почтовых клиентов (Gmail, Outlook, Yandex, Mail.ru) переделан в красивые крупные плитки.
- **Диктовка:** Добавлено авторасширение поля ввода при голосовой диктовке в полноэкранном режиме. Переименован ползунок отправки.
- **God Mode:** Добавлена задержка 15 секунд для запроса Root прав, а также автоматическая синхронизация галочек пермишенов.
- **Импорт/Экспорт:** Добавлено сохранение новых полей: `send_delay_ms`, `sys_tts_pitch`, `sys_tts_rate` (и другие профили VLESS).

## v3.1.4-git
### Added & Improved
- **Agent Settings Crash Fix:** Restored the missing `textToSpeech` parameter in `AgentContent`/`AudioEnginesCard`, fixing `Unresolved reference` compile errors.
- **Email Icons:** Gmail/Outlook/Yandex/Mail.ru chips now use bitmap assets via `ResourceImage` (`files/ic_email_*.png`).
- **HuggingFace Button:** HF search button now uses the `ic_hf.png` asset instead of a placeholder.
- **Spotless Fix:** Pinned ktlint `1.7.1` to resolve the spotless 8.8.0 + ktlint 1.8.0 "0 lint error(s)" incompatibility.
- **DeepSeek LTR Fix:** Strengthened the LTR enforcement script in the DeepSeek auth web view.
- **TopBar & Voice UX:** TopBar polish and STT/TTS/Piper spoiler clean-up across voice settings.

## v3.1.3-git
### Added & Improved
- **Deepseek Proxy Login:** Fixed caret shift issue in internal browser by avoiding `requestFocus()` on recomposition.
- **Proxy Connection Logging:** Added detailed Kermit logs for `127.0.0.1:10809` proxy checks.
- **Dynamic Connection Status:** Replaced static 'no connection' bar with dynamic connection speed visibility during generation.
- **Operation Modes Descriptions:** Added descriptive texts for 'Только по делу', 'Собеседник', and 'Сыскун' modes.
- **Chat UI Clean-up:** Removed uninformative gray top bar from the main chat.
- **GGUF Local Models:** Added prominent UI suggestion to download local models and updated HuggingFace button to search specifically for GGUF models.
- **GodMode One-Click Permissions:** Request all system runtime permissions at once when GodMode is selected during startup.
## v3.1.2-git
### Added & Improved
- **Voice Settings UI Tweaks:** Removed Pyper and Rhvoice options, added a 3-choice Voice selector ("По умолчанию", "Локальный", "Cloud API"), and added "Локальный" dropdown (2 voices) + Pitch & Speed sliders. Disabled Cloud API and added "(в разработке)".
- **Operation Modes & Send Delay:** Added toggle for "Короткий" vs "Собеседник" operation modes, and a slider for configuring a send delay for voice input (STT).
- **System Prompt Injection:** Injected system prompts based on the selected operation mode to control response length.
- **Offline STT Enforced:** Enforced `EXTRA_PREFER_OFFLINE = true` for SpeechRecognizer intents.
- **Joplin WebDAV Sync Skill:** Added Joplin WebDAV synchronization skill to manage notes via rclone and curl.
## v3.1.1
### Fixed & Improved
- **Skill Installation Sandbox Support:** Android app now correctly writes custom skills downloaded from marketplaces into the PRoot sandbox.
- **Compose Coroutines Stability:** Addressed a critical crash (`Unbalanced enter/exit`) when navigating away from ViewModels, by updating `androidx.lifecycle` to `2.11.0`.
- **VLESS Error Diagnostics:** Extended Vless proxy error messages with full stack traces for easier connectivity troubleshooting.
- **STT Settings Layout:** STT (Распознавание речи) toggle in settings was properly wrapped in `ToggleableHeadline`.

## v3.1.0
### Added & Improved
- **Sandbox Root Access:** Added UI button and manifest permission (`MANAGE_EXTERNAL_STORAGE`) to allow root-like access to all files for the Linux PRoot sandbox.
- **Connection Status Reliability:** Expanded network transport checks to support VPNs (like VLESS) so connection status displays accurately.
- **Agent Work Visibility:** Added inline indicators (e.g. `🛠 Читаю файл`) in ChatScreen when the agent performs tool calls, even if reasoning is hidden.
- **UI Layout Refresh:** Redesigned the top bar with cleanly separated icon rows and device/connection status blocks on a gray container background.
- **DeepSeek Authentication Fix:** Removed `bidi-override` CSS which inverted manually pasted tokens, strictly enforcing left-to-right alignment.
- **Voice Logic Sync:** Linking interface speakers and agent speech synthesis controls; logic prevents enabling voice thoughts if text-to-speech is disabled.

## v3.0.4
### Added & Improved
- **Mode Switcher:** Added three-mode toggle in the top bar — "Чат" (Chat), "Интерактив" (Interactive), "Мысли" (Thinking) — for quick interaction style switching.
- **Speech Controls UI:** Redesigned "Слушать" (Listen) and "Говорить" (Speak) buttons with white labels for better visibility and usability.
- **Device Admin & Trust Agent Cleanup:** Removed Device Admin and Trust Agent checks to simplify permissions and app startup.
- **Authorization Text Direction Fix:** Guaranteed LTR text layout for login, credentials, and authorization dialogs.
- **Header & Title Styling:** Set light color for "Слух и Речь (STT/TTS)" and updated UI card header titles.
- **Settings Import & Deduplication:** "Заменить настройки" defaults to OFF (`false`). Importing settings automatically merges new items with existing ones without duplicate entries.
- **VLESS Proxy Connection & Xray Core:** Fixed Xray JSON output (removed empty `"flow": ""` field that caused parse crashes), added binary execution fallback paths in proot/Termux, and updated connection ping target.
- **Local STT/TTS Model Download Checks:** Added model readiness checks when selecting local STT (Vosk) or TTS (Piper/HRVoise) engines with download prompt dialogs.
- **Microphone "Внимаю" Banner:** Replaced full-screen listening overlay with a non-blocking bottom banner showing "Внимаю" and real-time speech recognition text.
- **Device & Connection Status Header:** Added status header lines in main chat for Battery %, charging, CPU/RAM/sensor stats, and API connection status.
- **Reasoning & Thoughts Display:** Added toggle support to unspoiler `<think>` blocks and read reasoning aloud via TTS voice synthesis.
- **Email Provider Quick Presets:** Added quick preset chips (Gmail, Outlook, Yandex, Mail.ru) in the Add Email Account dialog with automatic IMAP/SMTP server configuration.
- **Camera Runtime Permission Fix:** Added runtime `Manifest.permission.CAMERA` permission request and try-catch safety wrapper, resolving camera launch crashes.

## v3.0.3
### Fixed & Synchronized
- **Git Release Synchronization:** Restored missing v3.0.2 release commits (`7763299`) and build files into master, resolving GitHub release desynchronization.
- **VLESS Proxy UI:** Redesigned VLESS Proxies section to match expandable UI with Edit (`VlessEditor`), Delete, and Selection radio controls. Fixed socket TCP connectivity check to target host:port.
- **Agent Settings Styling:** Corrected dark theme text color for "Слух и Речь (STT/TTS)" header.
- **Offline Models Prompt:** Verified model readiness checks and download triggers when selecting offline Vosk STT and TTS engines.

## v3.0.2
### Added & Improved
- **Device Admin & Trust Agent Cleanup:** Removed Device Admin and Trust Agent checks to simplify permissions and app startup.
- **Authorization Text Direction Fix:** Guaranteed LTR text layout for login, credentials, and authorization dialogs.
- **Header & Title Styling:** Set light color for "Слух и Речь (STT/TTS)" and updated UI card header titles.
- **Settings Import & Deduplication:** "Заменить настройки" defaults to OFF (`false`). Importing settings automatically merges new items with existing ones without duplicate entries.
- **VLESS Proxy Connection & Xray Core:** Fixed Xray JSON output (removed empty `"flow": ""` field that caused parse crashes), added binary execution fallback paths in proot/Termux, and updated connection ping target.
- **Local STT/TTS Model Download Checks:** Added model readiness checks when selecting local STT (Vosk) or TTS (Piper/HRVoise) engines with download prompt dialogs.
- **Microphone "Внимаю" Banner:** Replaced full-screen listening overlay with a non-blocking bottom banner showing "Внимаю" and real-time speech recognition text.
- **Device & Connection Status Header:** Added status header lines in main chat for Battery %, charging, CPU/RAM/sensor stats, and API connection status.
- **Reasoning & Thoughts Display:** Added toggle support to unspoiler `<think>` blocks and read reasoning aloud via TTS voice synthesis.
- **Email Provider Quick Presets:** Added quick preset chips (Gmail, Outlook, Yandex, Mail.ru) in the Add Email Account dialog with automatic IMAP/SMTP server configuration.
- **Camera Runtime Permission Fix:** Added runtime `Manifest.permission.CAMERA` permission request and try-catch safety wrapper, resolving camera launch crashes.

## v2.4.3
### Added
- **Clean Sandbox Install:** `LinuxSandboxManager` now performs a clean wipe of `rootfs`, `home`, and `tmp` directories when installing the sandbox to avoid caching bugs.
- **Action Logging:** Added robust UI logging of background actions like "Запрашиваю root-права для VLESS" directly into the UI state.
- **Redesigned Debug & Server Settings:** Consolidated debug logging settings into a new "Отладка" block on the Servers tab with levels: Выкл, Размышления, Кратко, and Полная.
- **Memory Tool Fixes:** Fixed a localization issue where all tools falsely displayed the same memory string resources.
- **Task Scheduling Refinements:** Tasks and triggers have been renamed to "Однократно", "Расписание", and "Пульс" for clarity.

## v2.4.15
### Added
- **Manual Memory & Tasks:** Added `AddMemorySheet` and `AddEditTaskSheet` UI to manually add memories and schedule tasks (with TIME, CRON, and HEARTBEAT triggers) without relying solely on Katya's autonomous actions.
- **Operating Modes (Sandbox / God Mode / Bare Android):** Added UI settings to switch Katya between full system access (God Mode) and restricted modes. System prompts and permissions dynamically adjust based on the selected mode.
- **Interactive Onboarding:** Introduced `StartupPermissionFlow` with a voice greeting on the very first launch, offering a beautiful checklist for granting necessary permissions.
- **Agent-Reach & Skills Knowledge:** Integrated dynamic capability awareness into the system prompt, so Katya knows whether she has network access (Agent-Reach) and can leverage available Hermes skills.
- **Dynamic Local Proxy Bypass:** Fixed connection issues by routing local loopback traffic (localhost, 127.0.0.1) directly to services like FreeDeepSeekAPI, bypassing the VLESS proxy.
### Changed
- **Token Extraction:** Improved token extraction reliability for DeepSeekAuthDialog by correctly parsing both cookies and localStorage.
- **Model Selector:** Unified the model selector to robustly fallback to default models when connection status is unknown.

## v2.3
### Added
- **System Control (Root/Sandbox):** Katya can now execute local shell commands natively via the new `ExecuteCommandTool`. It automatically detects Root (`su`) and uses it if available, or falls back to the app sandbox.
- **Native File Downloader:** Katya can download files directly from the web and place them into system directories (if rooted) using the new `DownloadFileTool`.
- **Advanced SSH & SFTP:** Added generic `SshTool` and `SftpTool` allowing Katya to connect to any server to execute arbitrary commands or transfer files seamlessly.
- **Calendar Integration:** Added `CalendarTool` to allow Katya to read and create calendar events using the native Android calendar provider.
- **App Guts Analyzer:** Added `AppGutsTool` giving Katya root-level dumpsys package insight (like AppManager backend).
- **System Intents:** Added `IntentTool` to allow Katya to navigate the Android UI, open activities, and trigger services dynamically.
- **Root Apps Catalog:** Added `RootAppsCatalogTool` to allow Katya to parse and suggest apps from the awesome-android-root repository.
- **Voice UI Modes:** Added support for switching between Full Screen and Bottom Sheet voice interfaces in the settings.
- **Smart Truncation:** Large text file uploads (> 1MB) are now intelligently truncated (keeping the top 100 and bottom 1000 lines) to avoid API token limits while preserving crucial log information.
- **Default Assistant:** Katya can now be set as the default digital assistant in Android.
- **Auto Backup:** Added automatic ZIP configuration backups triggered on successful SSH tunnel connections.
- **Import/Export:** Support for importing and exporting backups in ZIP format.
### Fixed
- **UI:** Fixed crash and scrolling issues on the App Logs screen when network output is large.
- **Tasks:** Fixed an issue where automated backups would spam the Scheduled Tasks list with completed tasks.
- **Voice (VAD):** Fixed `SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS` passing a Long instead of an Int, which broke voice auto-send.
- **Models:** Removed non-functional AutoSearch (proxy search) to favor the seamless fallback system.
### Added
- **Exact Alarms:** The background Heartbeat is now scheduled using Android's `AlarmManager` (with `setExactAndAllowWhileIdle`), guaranteeing execution exactly on time, even during deep Doze sleep.
- **Sequential Startup Permission Flow:** The app now gracefully requests critical system permissions (Notifications, Exact Alarms, Battery Optimization exclusions) one by one at startup, providing clear explanations from Katya for each requirement.
- **Manual Heartbeat Trigger ("Пинок"):** Added a dedicated forced-refresh button for the Heartbeat in the `Agent -> Heartbeat` settings, giving you instant manual control over background tasks.
- **Scheduling via Tools:** Katya can now independently manage tasks (`schedule_task`) and add them to your `ScheduledTaskList` behind the scenes, without relying on the old sandbox UI.
### Changed
- Refactored `ScheduledTaskList` to display tasks correctly and integrated with tool-based task scheduling.

## v1.3.2
### Added
- **Out of the sandbox:** Katya now fully utilizes Root privileges and has access to the full Android file system, Termux, and system APIs.
- **RHVoice Support:** Added ability to directly download and select RHVoice synthesizers in settings.
- **Smart Reconnection:** SSH tunnel now automatically retries connection using exponential backoff when network drops.
- **Termux MCP Servers:** Added pre-configured Local MCP Servers (GitHub, SQLite, Filesystem) for root environment.
### Changed
- Rebranded remaining references from Katya to Katya.
- Global package renamed to `com.katya.app`.
- Removed all multiplatform unused code (iosApp, site, flatpak, aur) to focus heavily on the Android application.
- Exported configurations now default to `[date]_Katya_config.json`.

## [Unreleased]

## v1.0.4
### Added
- Added Root access confirmation dialog on first launch.
- Implemented SSH Tunnel via JSch library for connecting to local models on srv-llm.
- Added Battery Optimization explanation dialog in MainActivity.
- Translated Quick Actions to Russian and added configuration examples.
- Updated Local API AI description with reference to the Servers tab for SSH tunnels.

### Fixed
- Fixed black text on dark theme in GeneralSettings (Dropdowns/Inputs).
### Fixed
- Fixed an issue causing `ScreenshotTest` to crash by gracefully bypassing Koin initialization for STT and `AudioPermissionController` components when running in Compose `LocalInspectionMode`.
- Resolved unresolved references to `SttController` by ensuring Kotlin safe calls (`?.`) are correctly used within `QuestionInput.kt` when STT is disabled in preview mode.
- CI/CD Unit Test pipeline is now restored and `screenshotTests` run successfully alongside unit tests.
