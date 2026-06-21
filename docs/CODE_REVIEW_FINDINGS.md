# Katixo Hospital OS — Code Review Findings

_Reviewer pass: 2026-06-21. Scope: `katixo-hospital-service` (Spring Boot, Java 21).
Method: source read of the cloned `main` branch — accounting, multi-tenancy, security,
GST, outbox, policy engine, prescription safety. **Not** a build/test run (no Maven
Central available in the review environment), so correctness statements are from reading
the code, not a green build._

This document **augments** `docs/COMPETITIVE_GAP_ANALYSIS.md` and
`docs/IMPLEMENTATION_STATUS_AND_PLAN.md` (both accurate and current). It records what was
independently verified as sound, and a short list of concrete issues worth acting on.

---

## 1. Verdict

The core that the whole "HMS with a real ERP brain" thesis rests on — the double-entry
ledger, schema-per-tenant isolation, and authentication — is **correctly implemented**.
This is materially stronger engineering than the typical billing-first Indian HMS, and the
two showstopper-class properties (no cross-tenant data bleed; default-deny auth) hold up
under inspection. The findings below are refinements, one genuine clinical-safety caveat,
and documentation hygiene — none of them undermines the architecture.

---

## 2. Verified sound (no action needed)

| Area | File(s) | What was checked | Result |
|---|---|---|---|
| Double-entry posting | `accounting/JournalService` | ≥2 lines, no negative amounts, no line with both DR & CR, balanced DR=CR, every account validated against the tenant CoA, append-only with mirror reversal + bidirectional `reversalOfId` links, audit-logged | **Correct** |
| GST inclusive split | `inventory/GstCalculator` | `taxable = gross × 100/(100+rate)`, `tax = gross − taxable`, CGST/SGST split with SGST absorbing the rounding residue (legs always sum to tax), HALF_UP 2dp, IGST path for inter-state | **Correct** |
| Tenant isolation — context lifecycle | `auth/JwtAuthenticationFilter` | `TenantContext.set()` per request; `filterChain.doFilter()` wrapped in `try { … } finally { TenantContext.clear(); SecurityContextHolder.clearContext(); }` | **Safe — cleared on every path incl. exceptions; no thread-pool bleed** |
| Tenant isolation — schema routing | `tenant/SchemaMultiTenantConnectionProvider`, `TenantSchemaResolver` | `search_path` set to the validated tenant schema on checkout, **reset to `platform` on release** before returning to the pool; resolver returns `platform` (no business tables) when no tenant is bound, so tenant-less queries fail fast instead of leaking | **Safe** |
| Schema-name injection guard | `tenant/TenantSchemas` | `requireValid` enforces `^[a-z][a-z0-9_]{0,62}$` (no quotes/semicolons/spaces); tenant ids validated by `^[a-zA-Z0-9][a-zA-Z0-9_-]{0,49}$` before deriving `t_<id>` | **Injection-safe** for the `SET search_path` string-concat |
| Web security posture | `config/SecurityConfig` | `anyRequest().authenticated()` (default-deny), stateless JWT, `@EnableMethodSecurity` (so `@PreAuthorize` role gates are enforced), BCrypt, CORS allow-list (no blanket `*`), step-up MFA on the four high-risk actions | **Sound** |

---

## 3. Findings to act on

Severity key: **S2** = should fix before production / before selling into regulated buyers;
**S3** = improvement / hygiene; **S4** = trivial.

| # | Severity | Area | Finding | Status |
|---|---|---|---|---|
| F1 | **S2** | Clinical safety | `prescription/AllergyChecker` is a substring token match, not interaction checking | **Relabelled** (near-term); real drug-DB fix still open |
| F2 | **S3** | Accounting integrity | `JournalService` balance check uses a `±0.01` tolerance — not a hard zero | **Fixed** (hard zero) |
| F3 | **S3** | Security (prod) | Swagger UI / `v3/api-docs` are `permitAll` in the single security chain | **Already handled** — `application-prod.yml` disables springdoc |
| F4 | **S3** | Secrets handling | Confirm no third-party secret is ever stored in the `hospital_policy` table in plaintext | **Fixed** (CLAUDE.md convention; settings tables are masked) |
| F5 | **S4** | Code hygiene | Dead `var ctx = TenantContext.get();` assignment in `JournalService.post()` | **Fixed** |
| F6 | **S4** | Docs hygiene | `IMPLEMENTATION_STATUS_AND_PLAN.md` "18 backend test classes (68 tests)" line is stale | **Fixed** |

### F1 — Allergy "check" is name-matching, not CDSS (**S2**)
`AllergyChecker.findConflicts()` tokenises the patient's free-text allergy field and flags a
conflict only when an allergen token is a **case-insensitive substring of the medicine
name/code**. The Javadoc is honest about this ("until contraindication data is wired in"),
and it's gated by the `rx.allergy.check_enabled` policy. Two real consequences:

- **False negatives that matter clinically.** A recorded "penicillin" allergy will **not**
  match a prescribed "Amoxicillin" (or "Augmentin", "Co-amoxiclav", etc.) because there's
  no ingredient/class mapping. This is the dangerous direction — it can give staff false
  reassurance.
- **False positives.** Any incidental token overlap surfaces a spurious conflict.

This is a feature gap that reads as a clinical-safety gap, so handle it explicitly:

1. **Near-term:** in the UI and in any audit text, label this as an *allergy name-match
   prompt*, not "interaction/contraindication checking", so clinicians don't over-trust it.
2. **Real fix:** back it with a drug database keyed on generic ingredient + drug class
   (and, when ABDM/terminology work lands, map medicines to a coded vocabulary). Even a
   modest local ingredient→brand table closes the penicillin→amoxicillin class of miss.

### F2 — Journal balance tolerance is `±0.01`, not zero (**S3**)
`JournalService.TOLERANCE = 0.01` means a posting that is off by exactly one paisa still
saves. In practice your generated postings balance exactly (the GST residue is absorbed
into the SGST leg, COGS/revenue are computed to match), so this is defensive rather than
load-bearing — but for an append-only ledger it's worth tightening to a hard zero (or
keeping the tolerance only for externally-sourced entries, of which you currently have
none). A one-paisa silent pass is the kind of thing that's invisible until a year-end
trial balance is a few rupees off and nobody can find why.

### F3 — Swagger open in the production chain (**S3**)
`SecurityConfig` permits `/swagger-ui/**` and `/v3/api-docs/**` unauthenticated. Fine for
dev; in production either disable springdoc via profile (`application-prod.yml`) or require
auth for those paths. Exposing the full API surface and schemas to anonymous callers is
needless attack-surface for a system holding PHI.

### F4 — Verify secret storage path (**S3**)
The policy engine (`HospitalPolicyCode` + `PolicyService`) is the right place for
*configuration* (toggles, thresholds, rates), and the status doc says `notification_settings`
already stores provider keys **write-only/masked**. Action: make sure that masked-settings
pattern — not a plaintext `hospital_policy` row — is the **only** path for any secret
(SMS/WhatsApp keys today; ABDM `client_secret`, payment-gateway keys tomorrow). Add a brief
note/convention in `CLAUDE.md` so future modules don't accidentally put a secret in a policy
value.

### F5 — Dead assignment (**S4**) — FIXED
`JournalService.post()` opened with `var ctx = TenantContext.get();` that was never used
(the method uses the `branchId()`/`userId()`/`stamp()` helpers, which re-fetch). Removed.
(`reverse()`/`get()` legitimately use their `ctx`.)

### F6 — Stale test count in the status doc (**S4**) — FIXED
`IMPLEMENTATION_STATUS_AND_PLAN.md` said "18 backend test classes (68 tests)"; the tree now
has **38** backend test classes. Updated the headline number.

---

## 4. Two things to confirm at runtime (couldn't be exercised here)

- **Build/test green.** Run `mvn -pl katixo-hospital-service test` in an environment with
  Maven Central. The review was static.
- **Flutter visual pass.** The status doc already flags this — `flutter analyze`/`build web`
  pass, but click-through/screenshots need a real browser run.

---

## 5. What this means for the roadmap

Nothing here blocks the strategic priority. The isolation/auth/ledger foundation is solid,
so engineering effort is best spent on the **ABDM/ABHA + NHCX** gap (see
`ABDM_INTEGRATION_DESIGN.md`) and on **F1** (allergy → real drug data) as the main
clinical-credibility item. Everything else on this page is a half-day of cleanup.
