# Загрузка в GitHub и получение APK с телефона Android

Исходный код приложения хранится в `InvestmentsPremiumGlass-SOURCE.zip`. **Не загружайте архив подписи и его содержимое в публичный репозиторий.**

1. Распакуйте `InvestmentsPremiumGlass-SOURCE.zip` на телефоне. Создайте отдельный GitHub-репозиторий (например `InvestmentsPremiumGlass`). Загрузите **содержимое** папки проекта в корень репозитория: `app/`, `.github/workflows/android.yml`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, документацию и т.д. Скрытая папка `.github` должна сохраниться. Удобнее загружать файлы через GitHub mobile web / Git-клиент.
2. В репозитории откройте **Settings → Secrets and variables → Actions → New repository secret**.
3. Из **ЛИЧНОГО** архива подписи `InvestmentsPremiumGlass-SIGNING-PRIVATE.zip` создайте четыре секрета, копируя значения из `GITHUB_SECRETS.txt`:
   - `INVESTMENTS_KEYSTORE_BASE64`
   - `INVESTMENTS_STORE_PASSWORD`
   - `INVESTMENTS_KEY_ALIAS`
   - `INVESTMENTS_KEY_PASSWORD`
4. Перейдите на вкладку **Actions** и запустите workflow **Android - test, build, signed release** через `Run workflow` (или дождитесь запуска после push).
5. Сначала workflow запускает `testDebugUnitTest`, `lintDebug`, `assembleDebug`. При успешных тестах и четырёх секретах собирается `assembleRelease`.
6. В успешно завершившейся сборке внизу скачайте Artifacts:
   - `Investments-debug` — отладочный APK;
   - `Investments-release-signed` — APK, подписанный вашим сохранённым ключом (для установки релиза и последующих обновлений).
7. **Сохраните приватный архив подписи в защищённом месте.** Потеря ключа означает невозможность выпускать совместимые обновления с тем же packageName поверх установленной версии.

### Важные замечания

- Исходники и ключ **разделены**. Файлы `.jks`, пароли и `.b64` должны храниться только отдельно; `.gitignore` исключает ключи, но секреты нельзя добавлять даже вручную.
- `walletfree` не был доступен для чтения исходников при подготовке этой версии; фактические цветовые токены, компоненты и подпись walletfree надо проверить отдельно.
- Приложение работает на Android 8.0+ (API 26+).
- Подключение к walletfree пока не работает без адаптации исходников walletfree и совместимого доверенного механизма подписи. См. `docs/SYNC_CONTRACT_RU.md`.
- Реального APK в архиве исходников **нет**: требуются Android SDK, Maven-зависимости и GitHub Actions. GitHub соберёт его из проекта.
