# Участие в разработке

## Ветки и ремоуты

* `master` — основная ветка mauve.
* `origin` — этот репозиторий: `git@github.com:x-mauve/mauve4A.git`.
* `upstream` — исходный проект: `git@github.com:exteraless/exteraless`, ветка `main`.

После клонирования добавьте апстрим:

```bash
git remote add upstream git@github.com:exteraless/exteraless
```

## Синхронизация с апстримом

1. Начните с чистого рабочего дерева и свежего `master`:

    ```bash
    git switch master
    git pull origin master
    git fetch upstream
    ```

2. Посмотрите, что пришло:

    ```bash
    git log --oneline master..upstream/main
    ```

3. Слейте изменения в отдельной ветке:

    ```bash
    git switch -c sync/upstream-$(date +%F)
    git merge upstream/main
    ```

    Используйте merge, а не rebase: история `master` уже опубликована, и merge
    сохраняет её без переписывания.

4. Разрешите конфликты. Чаще всего они возникают там, где mauve отличается от
   апстрима, и в этих местах нужно оставить версию mauve:

    * имя пакета `org.xchunk.mauve` в `gradle.properties`, `AndroidManifest.xml`,
      `res/xml/*.xml` и `google-services.json`;
    * название `mauve` в строковых ресурсах `res/values*/strings*.xml`;
    * канал `@mauveApp`, ссылки на репозиторий и `REPO` в `GitHubUpdater`;
    * адрес и бот сервера бейджей в `TMessagesProj/build.gradle`;
    * иконки в `res/drawable/exteraless_icon*.xml`.

    Новые строки, в которых апстрим пишет `exteraless`, переименуйте в `mauve`.
    Найти их можно так:

    ```bash
    grep -rn ">[^<]*exteraless" TMessagesProj/src/main/res/values*/strings*.xml
    grep -rn "com\.exteraless\.app\|exteraless/exteraless" TMessagesProj gradle.properties
    ```

5. Обновите подмодули и проверьте сборку:

    ```bash
    git submodule update --init --recursive --depth=1
    ./gradlew :TMessagesProj:assembleDebug
    ```

6. Влейте ветку в `master` и отправьте:

    ```bash
    git switch master
    git merge --ff-only sync/upstream-$(date +%F)
    git push origin master
    git branch -d sync/upstream-$(date +%F)
    ```

## Коммиты

* Один коммит — одно логическое изменение.
* Сообщение коммита и комментарии в коде пишите на английском (американский вариант).
* Не коммитьте `local.properties` и ключи подписи.
