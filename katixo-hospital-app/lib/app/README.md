# Katixo Hospital App
Flutter Web responsive app (also runs on tablet/mobile browsers and large displays).

## Run locally
```bash
flutter pub get
flutter run -d chrome
```

## Design System — central control

Everything visual is driven from ONE place:

| File | Controls |
|------|----------|
| `core/theme/design_tokens.dart` | Colors, brand palettes, status colors, spacing (8px grid), radii, type scale, elevations, motion, component metrics. **Change here → reflects everywhere.** |
| `core/theme/app_theme.dart` | Builds light/dark `ThemeData` from tokens. Styles every Material component (buttons, inputs, cards, tables, chips, dialogs, nav). Screens never style locally. |
| `core/theme/theme_controller.dart` | Runtime switching: light/dark/system + brand palette. Persisted via SharedPreferences. |

Theme switch UI: `core/widgets/theme_switcher.dart` (in the app bar — dark toggle + palette picker).

## Responsive system

| File | Purpose |
|------|---------|
| `core/responsive/breakpoints.dart` | `FormFactor`: mobile <600, tablet <1024, desktop <1440, large ≥1440. Context helpers: `context.isMobile`, `context.gutter`, `context.gridColumns`, `context.responsive(...)`. |
| `core/responsive/responsive_builder.dart` | `ResponsiveBuilder` (widget per breakpoint) + `PageContainer` (gutter + max-width clamp on big screens). |

Navigation adapts automatically (`core/widgets/app_shell.dart`):
- mobile → bottom navigation bar
- tablet → compact navigation rail
- desktop/large → extended rail with labels

## Shared widgets
- `app_shell.dart` — adaptive scaffold every screen lives in
- `status_chip.dart` — `StatusChip.auto('IN_QUEUE')` maps backend statuses to semantic colors
- `kpi_tile.dart` — compact dashboard metric tile
- `theme_switcher.dart` — app bar theme controls

## Module Structure (by role)
Each module = one role's screens.
- front_desk/ — Registration, OPD visit, token, admission
- doctor/ — Worklist, consultation, prescription
- nurse/ — Ward dashboard, indent, vitals
- pharmacy/ — Prescription queue, dispense, OTC
- lab/ — Orders, samples, results, reports
- radiology/ — Orders, reports
- billing/ — Collection, refund, packages, final bill
- ipd/ — Admission, bed board, transfers
- ot/ — OT scheduling, checklists
- tpa/ — Preauth, documents, claims
- owner/ — Dashboard, reports
- settings/ — Hospital setup, masters, users, policies

## Core (to be added next)
- auth/ — JWT, login, current user
- api/ — HTTP client, error handling, retry
- permissions/ — Role-based UI rendering
- i18n/ — Translation layer
- offline/ — Local storage, sync engine
