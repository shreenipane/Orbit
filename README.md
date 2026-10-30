# Orbit: a VTOP companion for Android

A native Android (Kotlin + Jetpack Compose) app for VIT Vellore students, pulled from VTOP and cached on the phone:

* **Today:** the class happening now and the next one, the next exam, deadlines due soon, and courses below 75%
* **Timetable** by day, with back-to-back lab slots merged into one block
* **Attendance:** how many classes you can skip or need to attend, plus an attend/miss planner
* **Marks** by course or by exam (CAT-1 across all courses)
* **Exams:** how many classes are left in each course before CAT 1, CAT 2 and FAT, read from VTOP's academic calendar (B.Tech entries only, General (Flexible) class group by default; holidays skipped, Saturday day orders followed), plus schedule, venue and seat
* **Holidays:** the next break from the academic calendar (name, dates, days off, weekdays missed, how far away) and every break left this semester
* **In-app sign-in:** Orbit runs VTOP's login steps itself and shows only VTOP's captcha (and OTP when asked). With a saved login it's a fingerprint plus six characters. The VTOP website is only a fallback for days VTOP uses Google reCAPTCHA. A 15-minute keep-alive stops the session from timing out.
* **DA upload:** upload PDF/Word/Excel (up to 4 MB) to a digital assignment from the app, including VTOP's e-mail OTP step.
* **Deadlines:** VTOP digital assignments with days left, past-due and submitted lists, and alerts the evening before and on the day
* **CGPA planner:** the SGPA you need for a target CGPA, and a "what if I get…" grade picker
* **Reminders and assignments**, with notifications when they're due
* **Friends:** scan each other's QR code (or paste a share code) to swap timetables, then see when you're all free. No server is involved, so each of you scans once; after you scan a friend, Orbit shows your QR for them to scan back.
* **Class changes:** mark a class cancelled or add an extra class; classes-left counts, alerts, the widget and the calendar export follow them
* **Marks extras:** the FAT score you need for each target total
* **Results:** published grades per semester; tap a course for its grade ranges, class mean and class size
* **CGPA:** a calculator (target CGPA, what-if grades) and a grade history tab showing which categories (discipline core, electives, open electives, NGCR…) you still need, with every past course and its grade ranges
* **Calendar:** VTOP's academic calendar as a month grid; tap a day to see whether it's a class day, holiday or exam and which classes run
* **FFCS planner:** type slot combos (A1+TA1, L31+L32) to check clashes and credits before registration
* **Biometric log:** your punches at the academic blocks, day by day
* **Hostel leave:** opens VTOP's own leave form inside the app, already signed in
* **Past papers:** CodeChef-VIT's question-paper archive for each of your courses, read inside the app, saved to your phone or shared
* **Study plan:** plan what to revise on which day and tick it off
* **Home-screen widget** with the next class; **calendar export** (.ics); **backup and restore** of everything kept only on the phone; **weekly attendance report** on Sunday evening; **fee reminders**; light, dark or system theme
* **Alerts:** before each class, the evening before an exam and an hour before it, and for new marks or absences after a background sync
* **App lock** with fingerprint, face or screen lock, plus a display name of your choice
* **Fingerprint login locker:** after one password sign-in, Orbit can save your VTOP login encrypted with AES-256-GCM. The key lives in Android Keystore, can't be exported, and unlocks only after a strong-biometric check. It persists until you sign out (Android wipes it if you enrol a new fingerprint). Signing in then means a fingerprint plus typing the captcha. SHA-256 isn't used for this, because it's a one-way hash and a stored password has to be readable again.

It's a clean-room rebuild of the feature set that iOS VTOP companions like VITals offer. No code, assets or branding come from those apps.

## Look

Pastel neo-brutalism: a soft teal page, pastel cards with black outlines and hard offset shadows, Pangolin for headings and Poppins for text. The palette follows FFCS-inator by CodeChef-VIT (MIT); see `NOTICE`.

## Get the APK

Every push to `main` runs `.github/workflows/build-apk.yml`, which runs the unit tests and builds a debug APK with Google's official Android SDK. Download it from either place:

* **Releases** (easiest on a phone): the newest `Orbit build N` prerelease → `orbit-N.apk`
* **Actions** → latest run → Artifacts → `orbit-apk`

Debug builds are signed with a fresh key on each CI run, so uninstall the old build before installing a new one. If you want in-place updates, add a fixed signing key as a repository secret.

## Run it from Android Studio

1. Open this folder in Android Studio (Ladybug or newer) and let Gradle sync.
2. Run the `app` configuration on a device or emulator (Android 8.0+, API 26).
3. Pick your campus, tap **Sign in with VTOP**, and log in on VTOP's own page (captcha and OTP included).

To run the unit tests: `gradle test`, or right-click `app/src/test` in Android Studio → Run (31 tests: parsers including the academic calendar and DAs, classes-left counting, attendance and CGPA maths, exam times, share codes, free time, sync diff)

## How it works

```
LoginScreen (WebView on VTOP) ──cookies──▶ CookieManager ◀──── WebViewCookieJar ── OkHttp
                                                                       │
VtopClient: /vtop/content → _csrf + authorizedIDX                      │
            POST processViewTimeTable / processViewStudentAttendance / │
                 doStudentMarkView / StudentGradeHistory  ─────────────┘
                     │ HTML
                     ▼
            VtopParsers (jsoup, pure JVM, unit-tested on recorded pages)
                     ▼
            Repository → snapshot.json (offline cache) → AppViewModel → Compose screens
```

* **Sign-in in a WebView.** VTOP's captcha, OTP step and login quirks all work unchanged, and the app never sees or stores your password. The session lives in WebView cookies. When VTOP expires it, the app keeps showing saved data with a "Sign in" banner.
* **Data over OkHttp.** VTOP's data endpoints are plain form POSTs that take `_csrf`, `authorizedID` and an `x` GMT timestamp. Page-shell endpoints (semester list, grade history) take `verifyMenu` and `nocache` instead. An expired session comes back as HTTP 404.
* **Parsers locate columns by their header text** where they can, not by fixed position, so small layout changes don't break them.

| Package | What's in it |
|---|---|
| `core/model` | Serializable data classes |
| `core/parse` | `VtopParsers`: semesters, timetable grid + registered courses, attendance, marks, grade history |
| `core/calc` | `AttendanceCalc` (75% rule), `GradeCalc` (10-point scale, SGPA/CGPA, required SGPA), `Schedule` (now/next) |
| `core` | `ShareCode` (friend codes), `calc/ExamTime`, `calc/FreeTime`, `calc/SyncDiff`, `calc/ExamPlanner` (classes left before each exam), `parse/CalendarParsers` (academic calendar, DAs) |
| `data` | `Campus`, `WebViewCookieJar`, `VtopClient`, `Repository` (snapshot, reminders, friends, settings), `CredentialVault` (fingerprint locker), `Notifier` |
| `work` | WorkManager jobs: 6-hourly sync, alert planning, class/exam/reminder alerts |
| `ui` | Today, Timetable, Attendance, Marks, More (Exams, CGPA, Reminders, Friends, Settings), lock screen |

## Status and known gaps

* **Tested:** the parsers and maths, against real VTOP pages recorded at VIT-AP (same VTOP build as Vellore) plus Vellore-style course codes such as `BCSE302L`.
* **Not yet tested:** the UI and network layers haven't been compiled or run against live Vellore VTOP. Check these first:
  * The login detector in `LoginScreen.isLoggedInUrl` assumes VTOP lands on `/vtop/content` after login. If you stay on the WebView after logging in, check the URL VTOP actually lands on and adjust.
  * If you get a TLS error ("Trust anchor … not found"), see `res/xml/network_security_config.xml`.
  * If a screen comes back empty, save that endpoint's raw HTML into `app/src/test/resources/fixtures/`, write a test, and fix the parser against it.
* **Background sync limits:** VTOP sessions are short-lived, and Orbit deliberately never stores your password. Background sync therefore only works while your last sign-in is still valid. After that you get one "sign in to keep syncing" notification and saved data stays on screen.
* **Alert timing:** WorkManager can delay alerts by a few minutes when the phone is idle, in exchange for needing no exact-alarm permission.
* **Exam parser:** tested against a hand-built page that follows VTOP's documented layout, not yet against a live Vellore exam schedule.
* **New in 0.7, untested against live VTOP:** the Campus pages (announcements, proctor, HOD/Dean, faculty search, fees, biometric log) and semester results. Their parsers were built from VTOP's documented layouts; if one shows nothing, send the debug report from Settings.
* **Not included:** course page view, mess menu and hostel outing requests (outing requests are left out on purpose: a wrong guess at the format could file a real leave request).
* **Assumptions:** attendance uses the 75% threshold. Lab weightage follows however VTOP counts classes for that course.

## Credits

QR codes use [ZXing](https://github.com/zxing/zxing) and [zxing-android-embedded](https://github.com/journeyapps/zxing-android-embedded) (Apache License 2.0).

Endpoint shapes and the recorded test pages in `app/src/test/resources/fixtures/` (except `marks_synthetic.html`) come from [vitap-vtop-client](https://github.com/Udhay-Adithya/vitap-vtop-client) by Udhay Adithya (MIT License). See `NOTICE`.
