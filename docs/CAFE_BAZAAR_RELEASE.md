# Cafe Bazaar Release

Plan-B is published on [Cafe Bazaar](https://cafebazaar.ir) as `com.behnamjalali.planb`.

## What Cafe Bazaar needs

| Item | File | Notes |
|---|---|---|
| App Bundle | `release/cafebazaar/Plan-B-v<version>-release.aab` | Signed with the release key |
| Signed bundle digest | `release/cafebazaar/Plan-B-v<version>-bazaar.bin` | Required by Bazaar for App Bundles; produced with the **official** [bundle-signer](https://github.com/cafebazaar/bundle-signer) and the same release key, so Bazaar can generate device APKs without ever receiving your key |
| Universal APK (alternative) | `release/cafebazaar/Plan-B-v<version>-release.apk` | Use when uploading an APK instead of a bundle. Prefer the bundle: the rich-notes engines (Tesseract OCR, ML Kit handwriting) are native code, so the universal APK carries both ARM variants (about 17 MB) while a bundle lets each phone download only its own (about 7 MB less). Release builds contain ARM code only (`armeabi-v7a`, `arm64-v8a`); x86 emulators use debug builds. |
| Checksums | `release/cafebazaar/SHA256SUMS.txt` | For your records and for verifying downloads |
| R8 mapping | `release/cafebazaar/Plan-B-v<version>-mapping.txt` | Keep privately to read crash stack traces |
| Listing texts | `store/cafebazaar/listing_fa.md`, `listing_en.md` | Name, short and full descriptions, keywords, category, rating, privacy URL |
| What's new | `store/cafebazaar/whats_new_fa.txt`, `whats_new_en.txt` | Change notes for the release |
| Icon | `store/cafebazaar/graphics/icon-512.png` | 512×512, rendered from the launcher icon |
| Cover | `store/cafebazaar/graphics/cover_fa.png`, `cover_en.png` | 1024×500 |
| Screenshots | `store/cafebazaar/graphics/screenshots/{fa,en}/` | Eight real screenshots per language, in listing order |
| Privacy policy | https://github.com/BehnamJalaliCo/Plan-B/blob/main/PRIVACY.md | Persian and English |

Permissions to declare in the listing (the complete list is `tools/allowed-permissions.txt`;
the app has no ads, analytics or tracking):

| Permission | English | فارسی |
|---|---|---|
| `POST_NOTIFICATIONS` | Reminders for tasks, events, habits and focus | یادآور کارها، رویدادها، عادت‌ها و تمرکز |
| `SCHEDULE_EXACT_ALARM` | Reminders at the exact minute | یادآور در همان دقیقهٔ تعیین‌شده |
| `RECEIVE_BOOT_COMPLETED` | Restore reminders and scheduled backups after a restart | بازگرداندن یادآورها و پشتیبان‌گیری زمان‌بندی‌شده پس از راه‌اندازی دوباره |
| `INTERNET` | The optional AI assistant (off until the user adds their own key) and the one-time handwriting model download (Plan-B Pro, after consent) | دستیار هوش مصنوعی اختیاری (تا وارد کردن کلید شخصی خاموش است) و دریافت یک‌بارهٔ مدل دست‌خط (Plan-B Pro، پس از موافقت) |
| `PAY_THROUGH_BAZAAR` | Plan-B Pro purchases through Cafe Bazaar | خرید Plan-B Pro از طریق کافه‌بازار |
| `USE_BIOMETRIC`, `USE_FINGERPRINT` | Plan-B Pro App lock and locked notes (the device's own fingerprint, face or screen lock) | قفل برنامه و یادداشت‌های قفل‌شدهٔ Plan-B Pro (اثر انگشت، چهره یا قفل صفحهٔ خود گوشی) |
| `WAKE_LOCK` | Let short background jobs finish (Plan-B Pro automatic backups, emptying the 30-day trash, home-screen widget updates) | تمام شدن کارهای کوتاه پس‌زمینه (پشتیبان‌گیری خودکار Plan-B Pro، خالی شدن سطل زبالهٔ ۳۰ روزه، به‌روزرسانی ابزارک‌ها) |
| `READ_CALENDAR`, `WRITE_CALENDAR` | Plan-B Pro sync with device calendars (Google Calendar), only when you turn on calendar sync | همگام‌سازی Plan-B Pro با تقویم‌های گوشی (تقویم گوگل)، فقط وقتی همگام‌سازی تقویم را روشن کنید |
| `RECORD_AUDIO` | Only when you record a voice note (Plan-B Pro) | فقط وقتی یادداشت صوتی ضبط می‌کنید (Plan-B Pro) |
| `ACCESS_NETWORK_STATE` | Wait for a connection or Wi-Fi before the one-time handwriting model download (Plan-B Pro, only after you agree) | انتظار برای اتصال یا وای‌فای پیش از دریافت یک‌بارهٔ مدل دست‌خط (Plan-B Pro، فقط پس از موافقت شما) |

| Permission | English | فارسی |
|---|---|---|
| `POST_NOTIFICATIONS` | Show the reminders you set | نمایش یادآورهایی که تنظیم می‌کنید |
| `SCHEDULE_EXACT_ALARM` | Deliver reminders at the exact minute | ارسال یادآورها سر دقیقه |
| `RECEIVE_BOOT_COMPLETED` | Restore reminders after a restart | بازگرداندن یادآورها پس از روشن شدن دوباره |
| `INTERNET` | Optional AI assistant only, with your own key | فقط دستیار هوش مصنوعی اختیاری، با کلید خودتان |
| `PAY_THROUGH_BAZAAR` | Buy Plan-B Pro through Cafe Bazaar | خرید Plan-B Pro از کافه‌بازار |
| `READ_CALENDAR`, `WRITE_CALENDAR` | Only when you turn on calendar sync (Plan-B Pro) | فقط وقتی همگام‌سازی تقویم را روشن کنید (Plan-B Pro) |
| `RECORD_AUDIO` | Only when you record a voice note | فقط وقتی یادداشت صوتی ضبط می‌کنید |
| `ACCESS_NETWORK_STATE` | Handwriting model download, only after you agree (Plan-B Pro) | دریافت مدل دست‌خط، فقط پس از موافقت شما (Plan-B Pro) |

The last three are normal permissions (granted at install, never asked) and appear because the
Plan-B Pro widgets use Jetpack Glance. The Wear OS companion is a separate app and is not part of
the Cafe Bazaar upload.

## In-app products (Plan-B Pro)

Create both products in Pishkhan → your app → **In-app products** before the release that
sells Pro (details in [PRO.md](PRO.md)):

| Product id | Type | Price |
|---|---|---|
| `planb_pro_monthly` | Subscription, monthly | 399,000 toman |
| `planb_pro_lifetime` | In-app product (managed, never consumed) | 1,999,000 toman |

Copy the app's **RSA public key** from Pishkhan (in-app billing / Poolakey section) into the
GitHub secret `PLANB_BAZAAR_RSA_KEY`. Without it the release still builds, but its Pro screen
says purchases are not available in this version.

## Producing the files

Either run the **Release** workflow in GitHub Actions (recommended; see
[RELEASE.md](../RELEASE.md)) and download the `plan-b-v<version>-cafebazaar` artifact, or run
locally with the signing environment variables set:

```bash
tools/package_cafebazaar.sh
```

The script:

1. builds `:app:assembleRelease` and `:app:bundleRelease` (R8, signed);
2. verifies the APK signature with `apksigner` and prints the public certificate digest;
3. downloads `bundlesigner-0.1.13.jar` from the official GitHub release and checks its SHA-256
   (`1a28844a…4ed3eb`) before running it;
4. runs `bundlesigner genbin --bundle app-release.aab --v2-signing-enabled true
   --v3-signing-enabled false --ks … --ks-pass env:… --key-pass env:…` (passwords are passed by
   environment-variable reference, never on the command line);
5. copies the APK, AAB, `.bin` and mapping into `release/cafebazaar/` with versioned names and
   writes `SHA256SUMS.txt`.

Store graphics are regenerated from the shipped artwork and the Roborazzi screenshots with
`python3 tools/generate_store_graphics.py` (Pillow with libraqm for Persian shaping).

## Uploading

### Manually (Pishkhan web panel)

1. Sign in to [Pishkhan](https://pishkhan.cafebazaar.ir) → your app → **Releases** → new
   release.
2. Upload the AAB together with its `.bin` (or the universal APK).
3. Paste the Persian and English what's-new texts.
4. Update the listing texts, icon, cover and screenshots from `store/cafebazaar/` if needed.
5. Submit for review.

### With the Pishkhan API (optional)

1. In Pishkhan, create an API secret for the app and store it as the GitHub secret
   `CAFEBAZAAR_PISHKHAN_API_SECRET` (never commit it).
2. Run the **Release** workflow with **publish_to_cafebazaar** checked. It calls
   `tools/publish_cafebazaar.sh`, which uploads the signed APK to
   `POST https://api.pishkhan.cafebazaar.ir/v1/apps/releases/upload/` and then commits the
   release with the change notes via `…/releases/commit/`, authenticating with the
   `CAFEBAZAAR-PISHKHAN-API-SECRET` header.
3. The release is left for review unless **cafebazaar_auto_publish** is also checked.

Normal pushes never upload or publish anything. The API endpoints and header were checked
against the live service (they answer `403 Access is Denied` without a valid secret); the
upload has not been exercised with a real secret yet, so watch the first automated run and
fall back to the manual upload if Cafe Bazaar changes the API.

## Before every submission

- [ ] `versionCode` increased and `versionName` updated.
- [ ] CI on `main` is green (tests, screenshots, lint, device tests).
- [ ] Release workflow succeeded; `sha256sum -c SHA256SUMS.txt` passes.
- [ ] APK installs and starts on a real device; language switch, a reminder and backup/restore
      work.
- [ ] Listing, what's new and screenshots are up to date in both languages.
- [ ] `PLANB_BAZAAR_RSA_KEY` is set and both Pro products exist in Pishkhan; a test purchase
      and "Restore purchases" work with the release build (see [PRO.md](PRO.md)).
- [ ] The release workflow's permission check passed (only allowlisted permissions).
