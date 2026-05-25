# Pancake

Pancake - это Android-органайзер для пары, семьи или друзей.
В одном пространстве можно объединить неограниченное количество участников и вести общие заметки, покупки, планы и список фильмов/сериалов.

## Почему Pancake

- Полностью бесплатная синхронизация
- Self-hosted подход: вы подключаете свои Firebase/GitHub и управляете данными сами
- Один общий Space для всех участников
- Полноценный Markdown-редактор
- Для тех, кто не знает Markdown: встроены кнопки для легкого форматирования (заголовки, списки, цитаты, код и т.д.)
- Чистый интерфейс без лишней сложности с поддержкой Dynamic color

Чтобы быстро поделиться своим пространством, в любом месте приложения сделайте жест тремя пальцами вниз.

## Внешний вид

<img width="3206" height="2805" alt="IMG (1)" src="https://github.com/user-attachments/assets/7489fabd-1f39-41c2-8e95-a4ca6a5124c4" />


## Локализация

В приложении доступны языки интерфейса:

- Русский
- English
- 简体中文 (Chinese, China)

## Какие проблемы решает

Во многих похожих приложениях часто не хватает гибкости для совместной работы и структурирования информации.
Pancake закрывает это за счет:

- Единого пространства для нескольких людей, а не разрозненных личных списков
- Удобной системы заметок с форматированием и быстрым редактированием
- Списков покупок, календаря и кино-трекера в одном приложении
- Контроля над инфраструктурой синхронизации (свои сервисы, свои ключи)

## Быстрый старт

1. Склонируйте репозиторий.
2. Скопируйте `secrets.properties.example` в `secrets.properties`.
3. Добавьте `google-services.json` в `app/src/google-services.json`.
4. Откройте проект в Android Studio и запустите.

Пример `secrets.properties`:

```properties
github.token=your_github_token_here
github.owner=your-github-user-or-org
github.repo=your-repo-name
github.branch=main

kinopoisk.apiKey=your_kinopoisk_api_key

RELEASE_STORE_FILE=/absolute/path/to/your-release.keystore
RELEASE_STORE_PASSWORD=your_store_password
RELEASE_KEY_ALIAS=your_key_alias
RELEASE_KEY_PASSWORD=your_key_password
```

## Подключение сервисов

### Firebase

Нужен для авторизации и хранения/синхронизации данных между участниками пространства.

- Создайте проект и Android-приложение с package name: `ru.bl3xand.pancake`
- Скачайте `google-services.json` и положите в `app/src/google-services.json`
- Сайт: https://console.firebase.google.com/

### GitHub

Нужен для синхронизации изображений (вложения в заметках).

- Создайте Personal Access Token (PAT)
- Заполните `github.token`, `github.owner`, `github.repo`, `github.branch` в `secrets.properties`
- Сайт: https://github.com/settings/tokens

### Kinopoisk API

Нужен для поиска фильмов/сериалов и автоподстановки данных в кино-разделе.

- Получите API-ключ
- Укажите `kinopoisk.apiKey` в `secrets.properties`
- Сайт: https://kinopoisk.dev/

## Безопасность данных

- Данные пространства шифруются на стороне приложения перед синхронизацией
- Поэтому владелец подключенных API-сервисов (Firebase/GitHub и т.д.) хранит данные в зашифрованном виде

## Лицензия

[`Apache-2.0`](LICENSE)
