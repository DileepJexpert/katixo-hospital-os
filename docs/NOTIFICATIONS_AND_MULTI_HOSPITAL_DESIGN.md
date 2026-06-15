# Notifications & Multi-Hospital Doctor — Design

_Researched & drafted 2026-06-15. Decision doc — no code yet. Covers: patient
SMS/WhatsApp, doctor walk-in alerts, the multi-hospital doctor, and app-vs-web._

## The key reframe

The walk-in scenario you described — "doctor works at several hospitals; a patient
arrives without appointment; doctor should be alerted; patient gets SMS/WhatsApp" —
is fundamentally a **notification** problem, **not** a unified-login or native-app
problem. Both the patient *and* the doctor can be reached on **any phone via
SMS/WhatsApp**, server-side, with **no app required**. A native app only adds richer
in-hand UX (push, offline) later. So we design notifications first; app is optional.

## A. Patient SMS + WhatsApp (server-side)

### India constraints that shape the design
- **SMS needs DLT (TRAI):** every transactional SMS must use a **DLT-registered
  header (PE/sender ID)** and an **approved DLT template ID** in the payload, or
  telcos block it. DLT registration is the *hospital's* operational step; our code
  must let each tenant store its header + per-message-type template IDs and pass them.
- **WhatsApp needs pre-approved templates** (Meta). Utility templates (appointment,
  report-ready, bill) are cheap (~₹0.11/msg); **keep clinical detail out of the body**
  (privacy) — link to a secure view instead. Consent must be captured (we already have
  `patient.privacy_consent` / `data_sharing_consent` flags — gate sends on these).

### Recommended architecture
- **`notification/` module** with a single `NotificationService` fan-out — one place
  that, given an event, decides channels (SMS / WhatsApp / in-app / future FCM),
  renders the template, checks consent, sends, and **logs every attempt**
  (`notification_log`: type, channel, recipient, status SENT/FAILED/SKIPPED, provider id).
- **Provider-agnostic interfaces:** `SmsProvider` + `WhatsAppProvider`, each with
  pluggable impls. Default **MSG91** for SMS (strong DLT support) and **Meta WhatsApp
  Cloud API** for WhatsApp, plus a **generic BSP/CUSTOM** impl (Gupshup/Interakt/AiSensy)
  so a hospital can swap providers. (Mirror the proven katasticho design — META +
  CUSTOM — but built fresh here; never call katasticho.)
- **Per-tenant config in `hospital_policy` / settings:** provider, API key (write-only),
  sender header, template IDs, enable flags. **Fire-and-forget + async** (after-commit)
  so a slow provider never blocks checkout/registration. Failures are recorded, not thrown.
- **Triggers (events):** appointment booked, **walk-in registered**, lab report released,
  bill finalized, payment received. Each maps to a template.

## B. Doctor walk-in alert (in-app, live)

- **In-app live update:** use **SSE (Server-Sent Events)**, not WebSocket — the need is
  one-way server→doctor push, and SSE is simpler, runs over plain HTTP, and auto-reconnects.
  The doctor's queue screen subscribes to `/api/v1/notifications/stream`; when a walk-in is
  registered to them, the row appears instantly (no 10s poll). Backed by the same
  `NotificationService` (it writes a notification row + emits to the doctor's SSE channel).
- **Notification inbox:** persist notifications so the doctor sees a bell/badge + history,
  not just transient toasts.
- **When the doctor is away from the screen:** route the same alert to **SMS/WhatsApp to
  the doctor** (doctor is just another recipient) — works on any phone, **no app needed**.
  FCM push is a later nicety once a native app exists.

## C. The multi-hospital doctor (the hard part — done the light way)

Constraint: we are **schema-per-tenant**. A doctor at 3 hospitals = 3 `staff_user`
rows in 3 schemas; login is single-tenant. Three options:

| Option | What it gives | Cost |
|---|---|---|
| **B. Platform doctor registry (recommended first)** | A `platform.doctor_directory` keyed by **mobile** maps one real doctor → their staff records across hospitals (+ optional FCM tokens). A walk-in at *any* hospital fans a notification to that doctor's phone ("Walk-in at City Hospital: patient ABC"). | Low — solves the actual ask (cross-hospital *alert*) without cross-tenant sessions |
| A. Unified identity | One login; pick/switch active hospital | Medium — auth + JWT membership list |
| C. Unified workspace | One screen showing queues across all hospitals | High — cross-schema fan-out reads |

**Recommendation:** do **Option B** — the doctor's *phone number* is the cross-hospital
key, so the platform registry + notification fan-out delivers "notified across all my
hospitals" cheaply. Add A/C later only if a single unified screen is wanted.

## D. App or web?

- **Now:** one Flutter codebase, shipped as **responsive web** — works on desktop/tablet/
  phone browsers. Fine for desks (front-desk, billing, lab, pharmacy, doctor-at-desk).
- **PWA:** installable + web push (iOS web-push unreliable) — a cheap middle step.
- **Native Android/iOS:** same Flutter code + platform setup + **FCM** for true push.
  Worth it **only** when in-hand push UX is a priority. Note: because SMS/WhatsApp already
  reach doctors on any phone, **the native app is not required** for the walk-in use case.

## Recommended build sequence

1. **Notification core + patient SMS/WhatsApp** (`notification/` + MSG91 + WhatsApp Cloud/
   BSP, consent-gated, DLT-aware, per-tenant config, `notification_log`). Triggers:
   appointment, walk-in, report-ready, bill. _Fully buildable & verifiable here._
2. **Doctor alerts:** add doctor as a recipient on walk-in (SMS/WhatsApp now) + **SSE**
   live in-app queue/inbox.
3. **Platform doctor registry** (cross-hospital alert by mobile + optional FCM tokens).
4. **Native app + FCM** and/or unified cross-hospital workspace — only if wanted.

## Open decisions for you
- **SMS provider:** MSG91 (recommended) / Fast2SMS / other? (we keep it pluggable regardless)
- **WhatsApp:** Meta Cloud API direct (lowest cost, more setup) vs a BSP like Interakt/
  Gupshup/AiSensy (managed, ~10–30% markup)?
- **Do you want the native app now**, or web/PWA + SMS/WhatsApp for launch?

## Sources
- TRAI/DLT: messagecentral.com, webengage DLT docs, messagebot.in DLT guide
- WhatsApp BSP/Cloud + India pricing: aisensy.com, interakt.shop, codingclave WhatsApp pricing, richautomate (healthcare)
- SMS APIs India: smscountry best-SMS-APIs
- Real-time: Spring SSE/WebSocket (Globant/Medium), FCM channel comparison
