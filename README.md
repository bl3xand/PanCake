# Pancake (Android)

Приложение для ведения заметок и контента с синхронизацией изображений через GitHub и интеграциями Firebase.

## Что вынесено из кода

Секреты и персональные настройки больше не должны храниться в исходниках:
- `GitHub token`
- `Kinopoisk API key`
- release signing (`.keystore`, пароли)
- `google-services.json`

## Требования

- Android Studio (последняя стабильная)
- JDK 17
- Android SDK (minSdk 24, target/compile 36)
- Gradle wrapper из проекта (`./gradlew`)

## Быстрый старт

1. Клонируйте приватный репозиторий.
2. Скопируйте файл с секретами:
   - `cp secrets.properties.example secrets.properties`
3. Заполните `secrets.properties` своими значениями.
4. Положите `google-services.json` в `app/src/google-services.json`.
5. Откройте проект в Android Studio и дождитесь синхронизации Gradle.
6. Запустите debug-сборку.

## Настройка секретов

Файл: `secrets.properties` (не коммитить)

Пример структуры:

```properties
github.token=your_github_token_here
github.owner=your-github-user-or-org
github.repo=your-repo
github.branch=main

kinopoisk.apiKey=your_kinopoisk_api_key

RELEASE_STORE_FILE=/absolute/path/to/your-release.keystore
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

### GitHub token

Нужен для синхронизации изображений с GitHub API.

Рекомендуется создать fine-grained PAT с минимально нужными правами для конкретного репозитория:
- `Contents: Read and write`
- при необходимости удаления/обновления файлов - также write на contents

### Firebase

1. Создайте проект в Firebase Console.
2. Добавьте Android-приложение с пакетом `ru.bl3xand.pancake`.
3. Скачайте `google-services.json`.
4. Положите его в `app/src/google-services.json`.

В репозитории есть шаблон: `app/src/google-services.json.example`.

### Kinopoisk API

1. Получите API key у провайдера Kinopoisk API.
2. Запишите ключ в `secrets.properties` в `kinopoisk.apiKey`.

## Сборка

Debug:

```bash
./gradlew assembleDebug
```

Release (с заполненными signing-параметрами):

```bash
./gradlew assembleRelease
```

## Безопасность: что делать, если секреты уже попали в коммиты

Важно: сначала **сразу ротируйте/отзовите** все утекшие ключи (GitHub token, Firebase, Kinopoisk, keystore passwords).

Дальше можно переписать историю и удалить секреты из старых коммитов:

### Вариант 1: `git filter-repo` (рекомендуется)

1. Установите `git-filter-repo`.
2. Удалите чувствительные файлы из истории:

```bash
git filter-repo --path app/src/google-services.json --invert-paths
```

3. Замените токены по маскам через `--replace-text` (шаблон: `security/replace-secrets.txt.example`):

```bash
git filter-repo --replace-text security/replace-secrets.txt.example
```

4. Форс-пуш:

```bash
git push --force --all
git push --force --tags
```

5. Попросите всех участников сделать reclone, чтобы не вернуть старую историю.

### Вариант 2: BFG Repo-Cleaner

Удобен для массового удаления секретов/бинарников, но `git filter-repo` гибче.

## Лицензия

В проект добавлена лицензия `Apache-2.0` + `NOTICE`.

Это позволяет использовать и копировать код при условии сохранения уведомлений об авторстве и лицензии.
Если хотите акцент именно на ссылке на автора, укажите ее в `NOTICE` и поддерживайте этот файл в актуальном состоянии.


