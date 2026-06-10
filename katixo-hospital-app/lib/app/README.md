# Katixo Hospital App
Flutter Web responsive app.

## Module Structure
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

## Core
- auth/ — JWT, login, current user
- api/ — HTTP client, error handling, retry
- theme/ — Design tokens, responsive breakpoints
- permissions/ — Role-based UI rendering
- i18n/ — Translation layer
- offline/ — Local storage, sync engine
