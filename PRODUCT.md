# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Kotlin 2.0+, Jetpack Compose + Material 3, MVVM (ViewModel + StateFlow), Navigation Compose, DataStore, Firebase Auth + Cloud Firestore, Credential Manager, Jetpack WindowManager + Material 3 Adaptive, OkHttp, AlarmManager + NotificationCompat. Built only in GitHub Actions (Gradle 8.9, AGP 8.7.x, JDK 17, compileSdk 35); no local Android Studio. Distributed as a signed APK through GitHub Releases, not Google Play.

## Users

One person tracking their own health on their own phone (single profile). They live with both type 2 diabetes–style glucose monitoring and hypertension, so they record glucose and blood pressure several times a week or day and take scheduled medication. They are technically comfortable, but the moments of use are mundane and hurried: sitting at a table after five minutes of rest with a cuff on one arm, before a meal, or answering a medication reminder from the lock screen.

## Product Purpose

Kora makes recording a reading or a dose take seconds, and makes the history legible enough to bring to a doctor's appointment. Success: a blood-pressure reading is captured in under 20 seconds, a dose is confirmed in one tap from the notification, and the person can see at a glance whether recent readings sit inside the ranges they and their clinician configured.

## Positioning

A personal tracker built around the Galaxy Z Fold's three physical postures: the cover screen for one-thumb logging, tabletop (Flex) mode as a measurement station with a resting timer above the hinge and the numeric entry below it, and the unfolded screen as an analysis desk with medication agenda and charts side by side. It is a tracking tool, never a diagnostic device.

## Operating Context

- Blood pressure ritual: sit, rest five minutes, cuff on the left or right arm, read the monitor, type three numbers (systolic, diastolic, pulse). The Fold half-opened on the table is the natural stand.
- Glucose ritual: glucometer reading tagged with a context (Ayunas, Pre-comida, Post-comida 2 h, Antes de dormir).
- Medication: daily schedules, reminders via notification with "Tomada" and "Posponer 15 min", stock running down.
- Doctor visits: reviewing 7/30/90-day trends.
- Locale: Spanish (Mexico), es-MX number/date formats, mg/dL and mmHg units.

## Capabilities and Constraints

- Modules: glucose, blood pressure (AHA-style reference categories, highest category wins), medications with stock and dose log.
- Auth: email/password, password reset, Google via Credential Manager. Firestore per-user paths with offline persistence.
- Must build and run with no Firebase configured: an explicit, clearly labelled demo mode with fictitious data and no claim of sync.
- Self-updates from GitHub Releases; updates require the same applicationId and signing certificate.
- Clinical messages are prudent: classifications are orientative, ranges are user-configurable, no dosing advice, urgent-care guidance for very high readings with symptoms.
- No health data in logs or public error messages.

## Brand Commitments

- Name: Kora. Tagline concept: "claridad para cuidar lo importante".
- Original identity; no copying existing health brands.
- Native Android only; never a WebView or hybrid app.

## Evidence on Hand

No real readings, users, testimonials or clinical endorsements exist. Demo data must be visibly fictitious. Never invent clinical claims, certifications or accuracy figures.

## Product Principles

1. Recording beats reviewing: the fastest path is always to the next entry.
2. Every status says why: a colour is always paired with a word, a number and the reading that caused it.
3. Honest about absence: missing data, missing permissions and demo mode are stated, never papered over.
4. The posture is the mode: each fold state gets a layout designed for what the hands are doing in it.
5. Calm by default, firm when it matters: quiet surfaces, unmistakable alerts.

## Accessibility & Inclusion

48 dp minimum targets, large-font (sp) safe layouts, TalkBack labels on every value and chart (textual alternative for charts), status never by colour alone, light and dark themes, respect for the system "remove animations" setting.
