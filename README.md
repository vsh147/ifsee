# Ifsee

Android-приложение Ifsee на Kotlin: по настраиваемым правилам обнаруживает нужные
элементы на экране и сохраняет скриншот в галерею. Другие действия не
выполняются; кликов, жестов и сетевых запросов нет.

## Модель правила

- `name`, `enabled`, `packageName`
- `matchers`: список условий (`TEXT`, `DESCRIPTION`, `VIEW_ID`) с операциями
  `CONTAINS`, `EQUALS`, `REGEX`
- `matchMode`: `ALL` (И) или `ANY` (ИЛИ)
- `cooldownMs` (по умолчанию 3000), `delayMs` (по умолчанию 0)
- `actions`: сейчас только `Screenshot`

Правила хранятся в Preferences DataStore (`rules_json`) как JSON `RulesFile`.
При ошибке разбора исходная строка копируется в `rules_json_backup`, а список
правил становится пустым. Неизвестные поля и типы действий при чтении
игнорируются.

## Сборка

```bat
.\gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Релизы

Версия задаётся тегом вида `vX.Y.Z`, например:

```bash
git tag v0.1.0
git push origin v0.1.0
```

Workflow `.github/workflows/release.yml` соберёт подписанный release APK и
создаст GitHub Release с файлами `Ifsee-0.1.0.apk` и `Ifsee-0.1.0.apk.sha256`.
Теги `v0.*` публикуются как pre-release. Для сборки нужны секреты
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Проверить контрольную сумму:

```bash
sha256sum -c Ifsee-0.1.0.apk.sha256
```

## Установка и включение

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Приложение устанавливается отдельно с applicationId `io.github.vsh147.ifsee`;
удалять старую версию не обязательно.

1. Откройте приложение и нажмите «Открыть настройки спец. возможностей».
2. Включите сервис **Ifsee**.
3. Вернитесь и нажмите «Добавить правило».
4. Укажите имя, приложение, условия, режим И/ИЛИ, интервал, задержку и действие
   «Скриншот», затем нажмите «Сохранить».

Изменения применяются в сервисе сразу, без перезапуска. Удаление правила —
через меню ⋮ в списке с подтверждением.

## Скриншоты

Скриншоты сохраняются через `MediaStore.Images` в `Pictures/Ifsee` как
`shot_yyyyMMdd_HHmmss_SSS.jpg` (JPEG, качество 90). Открыть альбом можно кнопкой
«Открыть галерею». Скопировать на компьютер:

```bash
adb pull "/sdcard/Pictures/Ifsee/" .
```

На Android 13 и ниже API `takeScreenshot` недоступен — скриншоты не создаются.
Ограничение частоты системой (`ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT`)
обрабатывается как предупреждение в логе.
