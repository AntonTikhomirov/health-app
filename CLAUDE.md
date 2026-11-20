# Здоровяк — правила для Claude

## Сначала граф, потом код

В `graphify-out/` лежит граф знаний проекта (код + спеки из `docs/research/`).
Прежде чем искать или читать исходники:

1. Установи CLI, если его нет: `pip install -q graphifyy` (или `uv tool install graphifyy`).
2. Для вопроса о коде сначала `graphify query "<вопрос>"`; связи — `graphify path "<A>" "<B>"`;
   конкретная сущность — `graphify explain "<имя>"`; раздел спеки — `graphify section "<заголовок>"`.
3. Сырые файлы открывай только в местах, которые граф уже указал.
   `graphify-out/GRAPH_REPORT.md` — для общего обзора архитектуры.
4. Изменил код — запусти `graphify update .` и закоммить `graphify-out/` вместе с изменениями.
   Большой дифф `graph.json` в PR — это нормально.

Подробности и команда обновления после релиза: `graphify-out/README.md`.
