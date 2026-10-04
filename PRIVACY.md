# Privacy Policy — Plan-B

_Last updated: 4 October 2026 (12 Mehr 1405) · Applies to Plan-B with Plan-B Pro (`com.behnamjalali.planb`)_

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
| Internet (`INTERNET`) | Used only by the optional AI assistant, which is off until you turn it on and add your own provider key. Nothing else in the app uses the network. |
| Pay through Cafe Bazaar (`com.farsitel.bazaar.permission.PAY_THROUGH_BAZAAR`) | Lets the installed Cafe Bazaar app handle Plan-B Pro purchases. |
| Keep awake, network state, foreground service (`WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`) | Added by Android's WorkManager library, which draws the Plan-B Pro home-screen widgets. Granted at install without a prompt; Plan-B sends nothing over the network for widgets. |

With Plan-B Pro and a paired Wear OS watch, today's open tasks and habits are sent directly to
your watch over Google's Wearable Data Layer (Bluetooth or your own Wi-Fi); this needs Google
Play services and nothing goes to a server of ours.

Plan-B does not access contacts, location, camera, microphone, accounts or shared storage.

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

_آخرین به‌روزرسانی: ۱۲ مهر ۱۴۰۵ · Plan-B همراه با Plan-B Pro_

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
| اینترنت | فقط برای دستیار هوش مصنوعی اختیاری که تا وقتی خودتان روشنش نکنید و کلید شخصی وارد نکنید خاموش است. هیچ بخش دیگری از برنامه از شبکه استفاده نمی‌کند. |
| پرداخت از طریق کافه‌بازار | برنامهٔ کافه‌بازار نصب‌شده روی دستگاه، خرید Plan-B Pro را انجام می‌دهد. |

Plan-B به مخاطبین، موقعیت مکانی، دوربین، میکروفون، حساب‌ها یا حافظهٔ مشترک دسترسی ندارد.

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
