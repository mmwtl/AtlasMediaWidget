# Монохромные знаки плееров

Исходники одноцветных значков плееров на плитках выбора источника (режим «Одноцветные иконки
плееров»). `tools/make_player_icons.py` переводит их в `app/src/main/res/drawable/ic_player_*.xml`;
после правки SVG перезапустите скрипт, а не редактируйте drawable. Пакеты приложений для каждого
значка заданы в `PlayerIconStyle.CURATED`. Плееры из Simple Icons (CC0) скрипт берёт оттуда.
Все файлы: `viewBox="0 0 24 24"`, прозрачный фон, заливка `currentColor`.
Встроенный SVG наследует CSS `color`; при отдельном открытии цвет по умолчанию чёрный.
Для Android следует импортировать геометрию в VectorDrawable и задавать tint.
Приложение не получает SVG-декодер или новые зависимости.

Предпросмотр: [preview.html](preview.html), размеры 64, 24 и 32 px.

## Источники и адаптация

Проверено 8 ноября 2026 года. Это адаптации для интерфейса, а не комплект
официальных монохромных бренд-материалов. Права на знаки остаются у владельцев.

| Файл | Источник | Изменения |
| --- | --- | --- |
| [yandex-music.svg](yandex-music.svg) | [Официальные материалы](https://yandex.ru/support/music/ru/content-requirements/visual), [монохромный SVG](https://yastatic.net/s3/doc-binary/src/mediaservices/yandex_music_icon_mono.svg) | Взята исходная геометрия звезды, убрана чёрная круглая подложка, нормализован размер. Официальный монохромный вариант содержит белую звезду на чёрной подложке; данный файл отличается удалением подложки и наследуемым цветом. |
| [aimp.svg](aimp.svg) | [Официальный SVG сайта AIMP](https://www.aimp.ru/v2/theme/images/header-logo.svg) | Извлечён знак A, убраны надпись и круги с градиентом, нормализован размер. |
| [poweramp.svg](poweramp.svg) | [Официальный сайт](https://powerampapp.com/), [логотип](https://powerampapp.com/logo.1c51fc5e.png) | Ручная геометрическая перерисовка: кольцо и скруглённый треугольник. Убрана цветная внешняя подложка. Это приближение, не официальный вектор. |
| [zvuk.svg](zvuk.svg) | [Trace Logos](https://trace-logos.ru/logos/media/zvuk/), [исходный SVG](https://trace-logos.ru/assets/logos/svgs/zvuk.svg) | Сохранена геометрия знака, убрана зелёная плашка, нормализован размер. Источник сторонний. |
| [murglar.svg](murglar.svg) | [Официальный сайт](https://murglar.app/), [иконка](https://murglar.app/docs/en/images/icon.png) | Облегчённая монохромная адаптация: круглый контур (1,3 px), диагональ (1 px), нота, три строки списка и стрелка без заливки диска. Расположение деталей сохранено по исходной иконке. Это адаптация, не официальный вектор. |
| [waves-of-sound.svg](waves-of-sound.svg) | APK «Волны Звука» 1.0.29, предоставленный пользователем | Геометрия `drawable/ic_launcher_foreground` из APK: кольцо, V и четыре столбика. Убраны фон и цвет; обводки переведены в залитые контуры, нормализован размер. |
| [yandex-radio.svg](yandex-radio.svg) | [Актуальный сайт](https://radio.yandex.ru/), [официальный SVG favicon](https://radio.yandex.ru/favicon.svg) | Сохранён знак актуального сервиса, убрана красная подложка. Круглая обрезка длинного луча выражена дугой в контуре, без clipPath. Нормализован размер. |
| [vk-music.svg](vk-music.svg) | [LogoTeka](https://logo-teka.com/vk-music/), [SVG](https://logo-teka.com/wp-content/uploads/2025/08/vk-music-sign-logo.svg) | Сохранена розовая волна; пять столбиков эквалайзера стали прозрачными вырезами. Синяя часть фона заменена тонким верхним контуром (0,75 px), чтобы сохранить общий силуэт иконки. Это знак VK Музыки, а не общий знак VK. Источник сторонний. |
| [kion-music.svg](kion-music.svg) | [Официальный бренд-гайд](https://design.kion.ru/), [SVG иконки](https://static.kion.ru/kds/kiondesignru/kionmusic/kion_music_app_icon_sq.svg) | Сохранены три исходных контура ноты, убран градиентный фон, нормализован размер. |
| [vlc.svg](vlc.svg) | [Simple Icons v15](https://cdn.jsdelivr.net/npm/simple-icons@v15/icons/vlcmediaplayer.svg), CC0 | Исходный монохромный знак, нормализован размер с полем 1 px. |
| [stellio.svg](stellio.svg) | [Официальный сайт](https://stellio.ru/ru), [PNG иконки](https://stellio.ru/img/store_data/icon_jfrost.png) | Цветной знак хамелеона векторизован в одну заливку. Белая подложка и внешнее кольцо удалены; сохранена внутренняя геометрия. |
| [musicolet.svg](musicolet.svg) | [Официальный сайт](https://krosbits.in/musicolet/), [PNG иконки](https://krosbits.in/musicolet/icons/android-icon-192x192.png) | Векторизован исходный треугольник с нотой как прозрачным вырезом. Убрана зелёная круглая подложка. |
| [foobar2000.svg](foobar2000.svg) | [Simple Icons v15](https://cdn.jsdelivr.net/npm/simple-icons@v15/icons/foobar2000.svg), CC0 | Исходный монохромный знак, нормализован размер с полем 1 px. |

Все SVG этой папки подключены через `tools/make_player_icons.py` и `PlayerIconStyle.CURATED`.
Локальные `vlc.svg` и `foobar2000.svg` заменяют одноимённые знаки Simple Icons; `vk-music.svg`
используется для приложения VK Музыка, общий знак VK — для приложения VK.

Растровые исходники Murglar, Stellio и Musicolet обработаны VTracer (binary/spline)
во временном окружении вне репозитория. Конечные SVG содержат только залитые
контуры; зависимости приложения не добавлялись.

### Идентификация «Волн Звука»

- Исходный файл: `WavesOfSound-1.0.29.apk` из «Загрузок» пользователя.
- Package: `ru.chan.music`; versionName: `1.0.29`; versionCode: `120`.
- SHA-256 APK: `944038228c7b1249ad4909ebecb0cedbd15a31b84e8f7ef2f3f7c334b617bc03`.
- Ресурс: `drawable/ic_launcher_foreground` (`res/Qr.xml`), выбранный через
  adaptive icon `mipmap/ic_launcher` (`res/BW.xml`). Геометрия прочитана `aapt dump xmltree`.
- APK и декомпилированные ресурсы в репозиторий не добавлялись.

## Проверка

SVG разобраны XML-парсером; не содержат растровых вставок, внешних ссылок,
скриптов, фильтров или градиентов. Предпросмотр проверен визуально в 24, 32 и 64 px.
