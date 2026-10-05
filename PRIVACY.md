# Privacy Policy — Plan-B

_Last updated: 5 October 2026 (13 Mehr 1405) · Applies to Plan-B with Plan-B Pro (`com.behnamjalali.planb`)_

[فارسی](#سیاست-حریم-خصوصی--plan-b)

## Summary

Plan-B is an offline planner. **Everything you enter stays on your device.** The app has no
accounts, no servers, no advertising, no analytics, no crash reporting and no tracking.
**Nothing is sent anywhere** unless you turn on the optional AI assistant with your own
provider key; then only the text you choose to send goes directly from your device to the
provider you chose. Plan-B has no server in between and never sees that text or your key.

## What the app stores

Tasks, projects, notes and notebooks, habits and check-ins, goals, calendar events, focus
sessions, templates and your settings. They are stored in the app's private storage on your
device (a local database and a preferences file) and are readable only by Plan-B.

## Permissions

| Permission | Why |
|---|---|
| Notifications (`POST_NOTIFICATIONS`) | To show the reminders you set for tasks, events, habits and focus sessions. Optional; asked only when you add a reminder. |
| Alarms & reminders (`SCHEDULE_EXACT_ALARM`) | To deliver reminders at the exact minute. Optional; without it reminders may be slightly delayed. |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | To restore your reminders after the device restarts. |
| Internet (`INTERNET`) | Used by the optional AI assistant, which is off until you turn it on and add your own provider key, and for the one-time handwriting model download of Plan-B Pro notes (below), only after you agree. Nothing else in the app uses the network. |
| Network state (`ACCESS_NETWORK_STATE`) | Granted at install, never asked. Lets the handwriting model download wait for a connection (or for Wi-Fi, if you choose "Only over Wi-Fi"). |
| Microphone (`RECORD_AUDIO`) | Plan-B Pro voice notes. Asked only when you record a voice note, after an explanation (and, on Android 12 and older, when you dictate a transcript). The recording is stored in the note on your device. |
| Pay through Cafe Bazaar (`com.farsitel.bazaar.permission.PAY_THROUGH_BAZAAR`) | Lets the installed Cafe Bazaar app handle Plan-B Pro purchases. |
| Biometrics (`USE_BIOMETRIC`, `USE_FINGERPRINT`) | Plan-B Pro App lock and locked notes: your device's own fingerprint, face or screen lock confirms it's you. Plan-B never sees or stores biometric data. |
| Keep awake (`WAKE_LOCK`) | Lets short background jobs finish: Plan-B Pro automatic backups and emptying the 30-day trash. |
| Calendars (`READ_CALENDAR`, `WRITE_CALENDAR`) | Plan-B Pro sync with your device calendars (for example Google Calendar). Asked only when you turn on **Settings → Holidays and device calendars → Sync with device calendars**. Plan-B reads the events of the calendars you choose to show them in its calendar (they are not copied into Plan-B unless you copy one), and writes, changes or deletes only the events it created itself in the one calendar you choose. Plan-B sends nothing to any server; your calendar app syncs its own calendars as it always does. |

With Plan-B Pro and a paired Wear OS watch, today's open tasks and habits are sent directly to
your watch over Google's Wearable Data Layer (Bluetooth or your own Wi-Fi); this needs Google
Play services and nothing goes to a server of ours.

Plan-B does not access contacts, location, accounts or shared storage, and has no camera
permission. Your calendars are read only after you turn on calendar sync (above); the choice of
calendars stays on this device and is not part of backups. The microphone is used only while you
record a voice note or dictate a transcript.

## Photos, files, scans, drawings and voice notes (Plan-B Pro)

- Photos come from the system Photo Picker (Plan-B sees only the photos you pick, and needs no
  storage permission) or from your camera app, which takes the photo and hands it to Plan-B.
  Files come from the system file picker. Plan-B copies what you add into its private storage,
  attached to the note; photos are scaled down to 2560 pixels. Nothing is uploaded.
- When you open or share an attachment, only that one file is handed to the app you choose.
- **Text in images** is recognized on your device: Latin text by Google's ML Kit inside Google
  Play services (when your phone has them), Persian text by Tesseract inside Plan-B. Document
  scanning uses Google's ML Kit document scanner inside Google Play services when available,
  otherwise your camera app. Images are not sent anywhere by Plan-B.
- **Handwriting to text** uses Google's ML Kit on your device. The first time, it needs a language
  model (about 20 MB, Persian or English) from Google's servers: Plan-B asks first, and you can
  limit the download to Wi-Fi. Only the model is downloaded; your drawings never leave the phone.
  ML Kit's anonymous usage logging is switched off in Plan-B.
- **Transcripts** of voice notes come from the speech service installed on your phone (for
  example Google's), preferring on-device recognition. Whether that service processes audio on
  its own servers depends on the service and its settings; Plan-B itself sends nothing.
- Recognized text and transcripts are searchable inside Plan-B, except for locked notes, which
  are never indexed. Attachment files are part of your backups (as files in the ZIP) and are
  not encrypted, also for locked notes.

## Plan-B Pro purchases

All existing features stay free. Plan-B Pro (a monthly subscription or a one-time lifetime
purchase) is bought through the Cafe Bazaar app: payment happens entirely in Cafe Bazaar, under
its terms, and Plan-B never sees payment details. Plan-B asks the Cafe Bazaar app on your
device whether you own Pro, verifies the answer on the device, and remembers it locally so Pro
also works offline. Your notes and plans are never shared with Cafe Bazaar.

## Optional AI assistant

The assistant is off by default. If you turn it on, you choose a provider (for example an
Iranian OpenAI-compatible gateway, DeepSeek, OpenRouter, OpenAI, Anthropic or Google), enter
your own API key and confirm what will be sent. Then:

- Only the text you choose to send in a request goes, directly from your device over HTTPS, to
  that provider. The provider's own privacy policy applies to it.
- Your key is stored encrypted on the device with a key that cannot leave it; it is never
  included in backups or exports and never logged.
- You can turn the assistant off or remove the key at any time.

## Backups, exports and imports

Backups and exports are created only when you ask for them and are saved to a location you
choose with the system file picker. Imports read only the file you pick. These files are not
encrypted by Plan-B; keep them somewhere you trust. Android cloud backup and device-to-device
transfer of the app's data are disabled so your private notes never leave the device without
your action.

With Plan-B Pro you can turn on **automatic backups**: the same backup file is written on a
schedule into a folder you pick (for example a Google Drive folder, in which case the file is
uploaded by that provider's app, not by Plan-B). Only the newest 21 automatic backups are kept;
other files in the folder are never touched. **Locked notes** are encrypted with a passphrase
only you know (PBKDF2-HMAC-SHA256 and AES-256-GCM); backups contain them encrypted, and nobody,
including the developer, can recover a forgotten passphrase.

**Save to Plan-B** (Plan-B Pro web clipper) appears in Android's share sheet. It receives only
the text, page or link you share with it, saves it as a note on your device and never downloads
the page or contacts any server. Note version history and the journal stay on your device like
all other notes (and are part of your backups); locked notes keep no version history.

## Notifications on the lock screen

Reminder notifications are marked private: on a locked screen Android shows a generic text
instead of the task or note title (depending on your system settings).

## Deleting data

You can delete individual items, or everything at once from **Settings → Backup & data →
Delete all data**. Uninstalling the app removes all of its data from the device.

## Children

Plan-B does not collect personal information from anyone, including children.

## Changes

If this policy changes, the new version will ship with the app update and be published in the
project repository.

## Contact

Plan-B is developed by Behnam Jalali. Questions about privacy, feedback or problem reports:
**behnamjalali88@gmail.com** (Settings → About → Send feedback), or open an issue at
https://github.com/BehnamJalaliCo/Plan-B.

---

# سیاست حریم خصوصی — Plan-B

_آخرین به‌روزرسانی: ۱۳ مهر ۱۴۰۵ · Plan-B همراه با Plan-B Pro_

## خلاصه

Plan-B یک برنامه‌ریز آفلاین است. **هر چیزی که وارد می‌کنید فقط روی دستگاه خودتان می‌ماند.**
برنامه حساب کاربری، سرور، تبلیغات، ابزار تحلیل یا ردیابی ندارد. **هیچ چیز به جایی فرستاده
نمی‌شود**، مگر اینکه دستیار هوش مصنوعی اختیاری را با کلید شخصی خودتان روشن کنید؛ در آن صورت
فقط متنی که خودتان برای ارسال انتخاب می‌کنید، مستقیم از دستگاه شما به سرویس‌دهنده‌ای که
برگزیده‌اید می‌رود. Plan-B هیچ سروری در میانه ندارد و آن متن یا کلید شما را نمی‌بیند.

## چه چیزهایی ذخیره می‌شود

کارها، پروژه‌ها، یادداشت‌ها و دفترچه‌ها، عادت‌ها، هدف‌ها، رویدادهای تقویم، جلسه‌های تمرکز،
الگوها و تنظیمات شما. این داده‌ها در فضای خصوصی برنامه روی دستگاه نگهداری می‌شوند و فقط
خود Plan-B به آن‌ها دسترسی دارد.

## مجوزها

| مجوز | دلیل |
|---|---|
| اعلان‌ها | نمایش یادآورهایی که خودتان تنظیم می‌کنید. اختیاری است و فقط هنگام افزودن یادآور درخواست می‌شود. |
| هشدارها و یادآورها (زمان دقیق) | ارسال یادآور در همان دقیقهٔ تعیین‌شده. اختیاری است؛ بدون آن ممکن است یادآور کمی دیرتر برسد. |
| اجرا پس از روشن شدن دستگاه | بازگرداندن یادآورها پس از راه‌اندازی دوبارهٔ دستگاه. |
| اینترنت | برای دستیار هوش مصنوعی اختیاری که تا وقتی خودتان روشنش نکنید و کلید شخصی وارد نکنید خاموش است، و برای دریافت یک‌بارهٔ مدل دست‌خط در یادداشت‌های Plan-B Pro (پایین‌تر)، فقط پس از موافقت شما. هیچ بخش دیگری از برنامه از شبکه استفاده نمی‌کند. |
| وضعیت شبکه | هنگام نصب داده می‌شود و هرگز پرسیده نمی‌شود. دریافت مدل دست‌خط منتظر اتصال (یا اگر «فقط با وای‌فای» را انتخاب کنید، منتظر وای‌فای) می‌ماند. |
| میکروفون | یادداشت‌های صوتی Plan-B Pro. فقط وقتی یادداشت صوتی ضبط می‌کنید و پس از توضیح درخواست می‌شود (و در اندروید ۱۲ و قدیمی‌تر، وقتی متن گفتار را با صحبت کردن می‌سازید). صدای ضبط‌شده در همان یادداشت روی گوشی شما می‌ماند. |
| پرداخت از طریق کافه‌بازار | برنامهٔ کافه‌بازار نصب‌شده روی دستگاه، خرید Plan-B Pro را انجام می‌دهد. |
| بیومتریک (اثر انگشت) | قفل برنامه و یادداشت‌های قفل‌شدهٔ Plan-B Pro: اثر انگشت، چهره یا قفل صفحهٔ خود گوشی هویت شما را تأیید می‌کند. Plan-B هیچ دادهٔ بیومتریکی نمی‌بیند و ذخیره نمی‌کند. |
| بیدار نگه داشتن دستگاه | تمام شدن کارهای کوتاه پس‌زمینه: پشتیبان‌گیری خودکار Plan-B Pro و خالی شدن سطل زبالهٔ ۳۰ روزه. |
| تقویم‌ها (خواندن و نوشتن) | همگام‌سازی Plan-B Pro با تقویم‌های گوشی (برای نمونه تقویم گوگل). فقط وقتی درخواست می‌شود که در **تنظیمات ← مناسبت‌ها و تقویم‌های گوشی ← همگام‌سازی با تقویم‌های گوشی** آن را روشن کنید. Plan-B رویدادهای تقویم‌هایی را که انتخاب می‌کنید می‌خواند تا در تقویم خودش نشان دهد (مگر اینکه خودتان رویدادی را کپی کنید، چیزی در Plan-B ذخیره نمی‌شود) و فقط رویدادهایی را که خودش ساخته، در همان یک تقویمی که انتخاب می‌کنید، می‌نویسد، تغییر می‌دهد یا پاک می‌کند. Plan-B چیزی به هیچ سروری نمی‌فرستد؛ برنامهٔ تقویم شما مثل همیشه تقویم‌هایش را همگام می‌کند. |

Plan-B به مخاطبین، موقعیت مکانی، حساب‌ها یا حافظهٔ مشترک دسترسی ندارد و مجوز دوربین هم ندارد. تقویم‌های شما
فقط پس از روشن کردن همگام‌سازی تقویم (بالا) خوانده می‌شوند؛ انتخاب تقویم‌ها روی همین گوشی می‌ماند و در پشتیبان‌ها نیست.
میکروفون فقط هنگام ضبط یادداشت صوتی یا گفتن متن گفتار استفاده می‌شود.

## عکس، فایل، اسکن، نقاشی و یادداشت صوتی (Plan-B Pro)

- عکس‌ها از انتخابگر عکس خود اندروید (Plan-B فقط عکس‌هایی را که انتخاب می‌کنید می‌بیند و به مجوز حافظه نیاز
  ندارد) یا از برنامهٔ دوربین شما می‌آیند. فایل‌ها از انتخابگر فایل خود اندروید می‌آیند. Plan-B آنچه را
  اضافه می‌کنید در فضای خصوصی خودش و کنار یادداشت نگه می‌دارد؛ عکس‌ها تا ۲۵۶۰ پیکسل کوچک می‌شوند. چیزی
  بارگذاری نمی‌شود.
- وقتی پیوستی را باز یا هم‌رسانی می‌کنید، فقط همان یک فایل به برنامه‌ای که انتخاب می‌کنید داده می‌شود.
- **خواندن متن تصویر** روی خود گوشی انجام می‌شود: متن لاتین با ML Kit گوگل درون سرویس‌های Google Play (اگر
  گوشی‌تان آن را دارد) و متن فارسی با Tesseract درون خود Plan-B. اسکن سند در صورت وجود سرویس‌های Google Play با
  اسکنر ML Kit و در غیر این صورت با برنامهٔ دوربین شما انجام می‌شود. Plan-B تصویری به جایی نمی‌فرستد.
- **تبدیل دست‌خط به متن** با ML Kit گوگل روی گوشی انجام می‌شود. بار اول به یک مدل زبان (حدود ۲۰ مگابایت،
  فارسی یا انگلیسی) از سرورهای گوگل نیاز دارد: Plan-B اول می‌پرسد و می‌توانید دریافت را به وای‌فای محدود کنید.
  فقط مدل دریافت می‌شود و نقاشی‌های شما از گوشی بیرون نمی‌روند. گزارش‌گیری ناشناس ML Kit در Plan-B خاموش است.
- **متن گفتار** یادداشت‌های صوتی را سرویس گفتار نصب‌شده روی گوشی (مثلاً سرویس گوگل) می‌سازد و Plan-B تشخیص
  روی دستگاه را ترجیح می‌دهد. اینکه آن سرویس صدا را روی سرورهای خودش پردازش کند به خود سرویس و تنظیماتش
  بستگی دارد؛ Plan-B خودش چیزی نمی‌فرستد.
- متن تصویرها و متن گفتار در جست‌وجوی Plan-B پیدا می‌شوند، به جز یادداشت‌های قفل‌شده که هرگز نمایه نمی‌شوند.
  فایل‌های پیوست بخشی از پشتیبان شما هستند (به‌صورت فایل در ZIP) و رمزگذاری نمی‌شوند، حتی برای یادداشت‌های قفل‌شده.

## خرید Plan-B Pro

همهٔ امکانات فعلی رایگان می‌مانند. Plan-B Pro (اشتراک ماهانه یا خرید یک‌بارهٔ مادام‌العمر) از
طریق برنامهٔ کافه‌بازار خریده می‌شود: پرداخت کاملاً در کافه‌بازار و طبق شرایط آن انجام می‌شود
و Plan-B هیچ اطلاعات پرداختی نمی‌بیند. Plan-B از برنامهٔ کافه‌بازار روی دستگاه می‌پرسد که آیا
نسخهٔ پرو را دارید، پاسخ را روی همان دستگاه تأیید می‌کند و آن را روی دستگاه به خاطر می‌سپارد
تا نسخهٔ پرو بدون اینترنت هم کار کند. یادداشت‌ها و برنامه‌های شما هرگز با کافه‌بازار به
اشتراک گذاشته نمی‌شوند.

## دستیار هوش مصنوعی (اختیاری)

دستیار به‌طور پیش‌فرض خاموش است. اگر روشنش کنید، سرویس‌دهنده را انتخاب می‌کنید (برای نمونه یک
درگاه ایرانی سازگار با OpenAI، DeepSeek، OpenRouter، OpenAI، Anthropic یا گوگل)، کلید API
خودتان را وارد می‌کنید و آنچه فرستاده می‌شود را تأیید می‌کنید. پس از آن:

- فقط متنی که خودتان در هر درخواست برای ارسال انتخاب می‌کنید، مستقیم از دستگاه شما و با HTTPS
  به همان سرویس‌دهنده می‌رود و سیاست حریم خصوصی آن سرویس‌دهنده دربارهٔ آن اعمال می‌شود.
- کلید شما به‌صورت رمزشده و با کلیدی که از دستگاه خارج نمی‌شود نگهداری می‌شود؛ هرگز در
  پشتیبان یا خروجی قرار نمی‌گیرد و هرگز در گزارش‌ها ثبت نمی‌شود.
- هر زمان بخواهید می‌توانید دستیار را خاموش یا کلید را حذف کنید.

## پشتیبان‌گیری، خروجی و ورودی

پشتیبان و خروجی فقط وقتی ساخته می‌شوند که خودتان بخواهید و در محلی که با انتخابگر فایل
سیستم انتخاب می‌کنید ذخیره می‌شوند. ورودی فقط همان فایلی را می‌خواند که انتخاب کرده‌اید.
این فایل‌ها توسط Plan-B رمزگذاری نمی‌شوند؛ آن‌ها را در جای امن نگه دارید. پشتیبان‌گیری
ابری اندروید و انتقال داده بین دستگاه‌ها برای این برنامه غیرفعال است.

با Plan-B Pro می‌توانید **پشتیبان‌گیری خودکار** را روشن کنید: همان فایل پشتیبان طبق زمان‌بندی
در پوشه‌ای که انتخاب می‌کنید (مثلاً پوشه‌ای در گوگل‌درایو، که در این صورت برنامهٔ همان سرویس
فایل را بارگذاری می‌کند، نه Plan-B) ذخیره می‌شود. فقط ۲۱ پشتیبان خودکار تازه‌تر نگه داشته
می‌شوند و به فایل‌های دیگر پوشه دست زده نمی‌شود. **یادداشت‌های قفل‌شده** با رمزی که فقط شما
می‌دانید رمزنگاری می‌شوند؛ پشتیبان‌ها آن‌ها را رمزنگاری‌شده نگه می‌دارند و هیچ‌کس، حتی
سازنده، نمی‌تواند رمز فراموش‌شده را بازیابی کند.

گزینهٔ **ذخیره در Plan-B** (ذخیره‌ساز وب در Plan-B Pro) در فهرست هم‌رسانی اندروید دیده می‌شود. فقط
متن، صفحه یا پیوندی را که خودتان با آن هم‌رسانی می‌کنید دریافت می‌کند، آن را به‌صورت یادداشت روی
دستگاهتان ذخیره می‌کند و هرگز صفحه را دانلود نمی‌کند یا به سروری وصل نمی‌شود. تاریخچهٔ نسخه‌های
یادداشت و دفتر روزانه هم مثل بقیهٔ یادداشت‌ها روی دستگاه می‌مانند (و در پشتیبان‌ها هستند)؛
یادداشت‌های قفل‌شده تاریخچهٔ نسخه ندارند.

## اعلان‌ها روی صفحهٔ قفل

اعلان‌های یادآور خصوصی علامت‌گذاری شده‌اند و روی صفحهٔ قفل (بسته به تنظیمات سیستم) به جای
عنوان کار یا یادداشت، متن عمومی نمایش داده می‌شود.

## حذف داده‌ها

می‌توانید موارد را تک‌به‌تک حذف کنید یا از **تنظیمات ← پشتیبان و داده‌ها ← حذف همهٔ داده‌ها**
همه چیز را پاک کنید. با حذف برنامه نیز تمام داده‌های آن از دستگاه پاک می‌شود.

## تماس

Plan-B را بهنام جلالی طراحی و ساخته است. برای پرسش دربارهٔ حریم خصوصی، نظر، پیشنهاد یا
گزارش مشکل به **behnamjalali88@gmail.com** ایمیل بزنید (تنظیمات ← درباره ← ارسال نظر) یا در
https://github.com/BehnamJalaliCo/Plan-B یک Issue ثبت کنید.
