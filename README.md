# MoiTochki - Приложение для работы с KMZ/KML/GPX файлами

[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-blue.svg)](https://kotlinlang.org/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)](https://android-arsenal.com/api?level=24)
[![Version](https://img.shields.io/badge/Version-2.0-blue.svg)]()

**MoiTochki** (Мои Точки) — это мощное Android-приложение для просмотра, создания и редактирования географических меток на карте с поддержкой форматов KMZ, KML и GPX.

![Скриншот](moitochkivactor.png)

## 📱 Возможности

### Основные функции
- ✅ **Открытие KMZ/KML файлов** — импорт меток из Google Earth и других GIS-приложений
- ✅ **Поддержка GPX** — импорт/экспорт путевых точек и треков
- ✅ **Расширенный парсинг KML** — поддержка Point, LineString, Polygon с стилями
- ✅ **Работа с офлайн картами** — OSM карты с кэшированием тайлов
- ✅ **Слои карт** — OpenStreetMap, Esri Satellite, Hybrid режим
- ✅ **Управление папками** — группировка меток по категориям
- ✅ **Поиск** — по адресам, координатам и названиям меток
- ✅ **Навигация** — построение маршрута до выбранной точки
- ✅ **Фото для меток** — прикрепление фотографий к точкам
- ✅ **Кластеризация** — оптимизация отображения 1000+ меток
- ✅ **Экспорт данных** — JSON, KMZ, GPX форматы

### Технические особенности
- 🏗 **Архитектура MVVM** с Room Database
- 🔍 **Валидация координат** — проверка широты (-90..90) и долготы (-180..180)
- ⚡ **Асинхронная обработка** — Coroutines для фоновых операций
- 🧪 **Unit тесты** — покрытие парсеров и бизнес-логики
- 📦 **ProGuard правила** — оптимизация размера APK

## 🏗 Архитектура

```
┌─────────────────────────────────────────────────────────┐
│                    UI Layer (View)                       │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ MainActivity│  │Dialogs       │  │Custom Views  │   │
│  └─────────────┘  └──────────────┘  └──────────────┘   │
└─────────────────────────────────────────────────────────┘
                          ↕
┌─────────────────────────────────────────────────────────┐
│                 ViewModel Layer                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  MapViewModel (LiveData, StateFlow)              │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                          ↕
┌─────────────────────────────────────────────────────────┐
│                  Repository Layer                        │
│  ┌──────────────────────────────────────────────────┐   │
│  │  MarkerRepository                                 │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                          ↕
┌─────────────────────────────────────────────────────────┐
│                   Data Layer                             │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────┐    │
│  │Room Database│  │KML/GPX Parser│  │File Manager  │    │
│  └─────────────┘  └─────────────┘  └──────────────┘    │
└─────────────────────────────────────────────────────────┘
```

### Компоненты

| Компонент | Описание |
|-----------|----------|
| `MainActivity` | Основная активность с картой OSM |
| `MapViewModel` | Управление состоянием UI и данными |
| `MarkerRepository` | Репозиторий для работы с метками |
| `MarkerDao` | DAO для Room database |
| `AppDatabase` | Room database (SQLite) |
| `KmlParser` | Базовый парсер KML (Point) |
| `KmlParserExtended` | Расширенный парсер (LineString, Polygon) |
| `GpxParser` | Парсер GPX треков и waypoints |
| `ClusterManager` | Кластеризация меток |

## 🚀 Сборка и установка

### Требования
- Android Studio Hedgehog (2023.1.1) или новее
- JDK 17
- Android SDK 24+
- Gradle 8.0+

### Шаги сборки

```bash
# Клонирование репозитория
git clone https://github.com/yourusername/moitochki.git
cd moitochki

# Сборка debug версии
./gradlew assembleDebug

# Сборка release версии
./gradlew assembleRelease

# Запуск тестов
./gradlew test

# Установка на устройство
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Генерация подписанного APK

```bash
# Создать keystore (если нет)
keytool -genkey -v -keystore moitochki.keystore -alias moitochki -keyalg RSA -keysize 2048 -validity 10000

# Собрать релизный APK
./gradlew assembleRelease -Pandroid.injected.signing.store.file=moitochki.keystore
```

## 📖 Использование

### Импорт KMZ/KML
1. Нажмите кнопку **📁 Папки** → **Импорт KMZ/KML**
2. Выберите файл в файловом менеджере
3. Метки будут добавлены в новую папку

### Экспорт в GPX
1. Откройте меню **⚙️ Настройки** → **Экспорт**
2. Выберите формат **GPX**
3. Сохраните файл

### Поиск мест
- Введите адрес или название в поисковую строку
- Или введите координаты в формате `55.751244, 37.618423`
- Результаты появятся в выпадающем списке

### Создание метки
- Долгое нажатие на карту → **Добавить метку**
- Заполните название и описание
- Прикрепите фото (опционально)

## ⚖️ Правовая информация

### Лицензии карт
Приложение использует следующие картографические данные:

| Источник | Лицензия | Требования |
|----------|----------|------------|
| OpenStreetMap | [ODbL](https://opendatacommons.org/licenses/odbl/) | Указание авторства © OpenStreetMap contributors |
| Esri Satellite | [Esri Terms](https://www.esri.com/en-us/legal/terms/full-master-agreement) | Некоммерческое использование |

### Конфиденциальность (GDPR)
✅ **Приложение соответствует GDPR:**
- Не собирает персональные данные пользователей
- Все данные хранятся локально на устройстве
- Не передаёт информацию третьим лицам
- Разрешения запрашиваются только для основных функций

### Лицензия приложения
MIT License — см. файл [LICENSE](LICENSE)

## 🛣 Дорожная карта

### Реализовано в v2.0
- ✅ Поддержка LineString и Polygon
- ✅ GPX импорт/экспорт
- ✅ Кластеризация меток
- ✅ Unit тесты
- ✅ Валидация координат
- ✅ Расширенная обработка ошибок

### В разработке (v2.1)
- 🔄 Тёмная тема (Dark Mode)
- 🔄 Синхронизация с облаком (Google Drive)
- 🔄 Офлайн режим с предзагрузкой карт
- 🔄 Локализация (EN, ES, DE)

### Планы (v3.0)
- 🔜 Dependency Injection (Hilt)
- 🔜 Jetpack Compose UI
- 🔜 Совместное редактирование
- 🔜 Экспорт в CSV/KML с стилями

## 🤝 Вклад в проект

Приветствуются PR с улучшениями! Пожалуйста:

1. Fork репозиторий
2. Создайте feature branch (`git checkout -b feature/amazing-feature`)
3. Commit изменения (`git commit -m 'Add amazing feature'`)
4. Push в branch (`git push origin feature/amazing-feature`)
5. Откройте Pull Request

### Стандарты кода
- Kotlin Code Style
- KDoc документация для публичных API
- Unit тесты для новой функциональности

## 📞 Контакты

- Email: support@moitochki.example.com
- Telegram: [@moitochki_app](https://t.me/moitochki_app)
- GitHub Issues: [Сообщить о проблеме](https://github.com/yourusername/moitochki/issues)

## 🙏 Благодарности

- [OSMDroid](https://github.com/osmdroid/osmdroid) — офлайн карты
- [Simple XML](http://simple.sourceforge.net/) — парсинг XML
- [Room Persistence Library](https://developer.android.com/training/data-storage/room) — база данных
- [OpenStreetMap](https://www.openstreetmap.org/) — картографические данные

---

**MoiTochki** © 2024. Сделано с ❤️ для путешественников и исследователей.
