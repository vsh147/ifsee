# YaPro Tree Dumper

Минимальное Android-приложение на Kotlin, которое через `AccessibilityService`
дампит дерево accessibility-узлов приложения Яндекс Про (`ru.yandex.taximeter`)
в текстовый файл. Приложение только читает экран: без кликов, жестов,
скриншотов и сетевых запросов.

## Что умеет

- Записывает дерево узлов с `text`, `contentDescription`, `viewIdResourceName`,
  границами и флагами `clickable`/`enabled`.
- Фильтрует события и окна по настраиваемому пакету (по умолчанию
  `ru.yandex.taximeter`).
- Не пишет одинаковые подряд экраны и ограничивает частоту дампов — не чаще
  одного блока в 500 мс.
- Фильтрует узлы по `text`/`contentDescription`: в файл попадают только узлы,
  содержащие строку из поля «Фильтр» (по умолчанию «Принять», без учёта регистра).
  Пустые блоки не создаются.
- Ротирует файл при размере больше 25 МБ: текущий файл становится
  `tree_dump.old.txt`, создаётся новый `tree_dump.txt`.
- Позволяет очистить файл и поделиться им через `FileProvider`.

## Требования к окружению

- JDK 17 или новее.
- Android SDK: platform `android-35`, build-tools `35.0.0`.
- Gradle wrapper использовать не обязательно; версия Gradle 8.12, AGP 8.8.2,
  Kotlin 2.0.21.

## Сборка

На Windows:

```bat
.\gradlew assembleDebug
```

На Linux/macOS:

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Если wrapper отсутствует, сгенерируйте его командой
`gradle wrapper --gradle-version 8.12 --distribution-type bin` из корня проекта.

## Установка

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Включение сервиса

1. Откройте приложение YaPro Tree Dumper.
2. Нажмите «Открыть настройки спец. возможностей».
3. Найдите **YaPro Tree Dumper** и включите сервис.
4. Вернитесь в приложение, при необходимости измените поле «Фильтр» и включите
   переключатель «Писать дамп».
5. Откройте Яндекс Про и посмотрите текст дампа — в файле появятся блоки:

```text
===== 12:34:56.789 STATE_CHANGED pkg=ru.yandex.taximeter windows=1
    <android.widget.Button> text="Принять" desc="null" id=ru.yandex.taximeter:id/accept_order bounds=[64,900,1016,1000] click=true enabled=true

```

## Как достать файл

Через adb:

```bash
adb pull /sdcard/Android/data/com.example.treedumper/files/tree_dump.txt .
```

Предыдущая ротированная версия лежит рядом:
`tree_dump.old.txt`.

Или нажмите «Поделиться файлом» в приложении — файл отправится через
`Intent.ACTION_SEND` с временным доступом через `FileProvider`.

## Как искать карточку заказа в дампе

Дамп — обычный текст, поэтому удобно искать по ключевым словам:

```bash
grep -n -i -E "заказ|найти|такси|забрать|подал|accept|order" tree_dump.txt
```

Полезно смотреть на строки с непустыми `text=`/`desc=` и на `id=`, содержащие
`order`, `offer`, `card`, `trip` и т.п. Чтобы увидеть, какие атрибуты появляются
именно в момент карточки, сравните блоки до и после события: заголовок блока
содержит время и тип события (`CONTENT_CHANGED`, `STATE_CHANGED`,
`WINDOWS_CHANGED`).

## Формат строки узла

```text
<className> text="..." desc="..." id=... bounds=[l,t,r,b] click=true/false enabled=true/false
```

Отступ — два пробела на уровень. Пустые значения записываются как `null`.
Переводы строк внутри `text` заменяются на литерал `\n`, чтобы один узел
всегда занимал одну строку.
