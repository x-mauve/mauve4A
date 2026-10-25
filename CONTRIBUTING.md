# Contributing

## Branches and remotes

* `master` is the main mauve branch.
* `origin` is this repository: `git@github.com:x-mauve/mauve4A.git`.
* `upstream` is the original project: `git@github.com:exteraless/exteraless`, branch `main`.

After cloning, add the upstream remote:

```bash
git remote add upstream git@github.com:exteraless/exteraless
```

## Syncing with upstream

1. Start with a clean working tree and an up-to-date `master`:

    ```bash
    git switch master
    git pull origin master
    git fetch upstream
    ```

2. See what's new:

    ```bash
    git log --oneline master..upstream/main
    ```

3. Merge the changes on a separate branch:

    ```bash
    git switch -c sync/upstream-$(date +%F)
    git merge upstream/main
    ```

    Use merge, not rebase: the `master` history is already published, and a
    merge keeps it intact without rewriting.

4. Resolve conflicts. They usually show up where mauve differs from upstream,
   and in those places you should keep the mauve version:

    * the `org.xchunk.mauve` package name in `gradle.properties`, `AndroidManifest.xml`,
      `res/xml/*.xml`, and `google-services.json`;
    * the `mauve` name in the string resources in `res/values*/strings*.xml`;
    * the `@mauveApp` channel, the repository links, and `REPO` in `GitHubUpdater`;
    * the badge server address and bot in `TMessagesProj/build.gradle`;
    * the icons in `res/drawable/exteraless_icon*.xml`.

    Rename any new strings where upstream uses `exteraless` to `mauve`. You can
    find them like this:

    ```bash
    grep -rn ">[^<]*exteraless" TMessagesProj/src/main/res/values*/strings*.xml
    grep -rn "com\.exteraless\.app\|exteraless/exteraless" TMessagesProj gradle.properties
    ```

5. Update the submodules and check that the build works:

    ```bash
    git submodule update --init --recursive --depth=1
    ./gradlew :TMessagesProj:assembleDebug
    ```

6. Merge the branch into `master` and push:

    ```bash
    git switch master
    git merge --ff-only sync/upstream-$(date +%F)
    git push origin master
    git branch -d sync/upstream-$(date +%F)
    ```

## Commits

* One commit, one logical change.
* Write commit messages and code comments in American English.
* Don't commit `local.properties` or signing keys.
