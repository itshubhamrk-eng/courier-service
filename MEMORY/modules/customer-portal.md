# Module: Customer Portal (e-commerce-business backend)

**Status:** phase 1 built 2026-10-21 (V128): customer authorization, portal access, profile,
and the quote/markup/booking schema. Rate shopping, markup service, booking, tracking and labels
are later phases — see "Phases".
**Package:** `com.courier.modules.customerportal` (new). Small extensions in `auth` (`users.customer_id`,
customer-portal accounts) and `customer` (`CustomerPortalLookup`).
**Frontend:** separate repository `occplsoftech/e-commerce-business` (Angular, same conventions as
`courier-web`, deployed separately).

## What it is

A web portal for a courier company's **business customers** (`Customer` with type `BUSINESS`):
log in, enter pickup/delivery/package details, compare courier options with genuine prices, book one,
and follow the shipment. `courier-service` stays the only backend and the authority for prices,
ownership and bookings.

## Findings that shaped it (2026-10-21)

- No external courier provider (Delhivery, Blue Dart, Ekart, Shiprocket) is integrated anywhere in the
  code, and no credentials exist. Each one is a new adapter, disabled until its account and API access
  are confirmed. Nothing is shown for a provider that is not configured except "rate not available".
- OCCPL's own network already has genuine customer prices: pricing zones + rate cards
  (`customerpricing`, booking via `FreightCalculationServiceImpl`). That is the first provider
  ("OCCPL network"), booked into the normal `Shipment` flow.
- `Role.CUSTOMER` existed but nothing used it. Many staff services read with `isAuthenticated()`
  (e.g. `CustomerServiceImpl.READERS`), so **a CUSTOMER token must never reach staff endpoints** —
  see "Scope gate".

## Decisions (agreed with the owner 2026-10-21)

| Topic | Decision |
|---|---|
| Login | Courier's own login (`/api/v1/auth/login`), a user with role `CUSTOMER` linked to one business customer by `users.customer_id`. Same refresh tokens, logout, password reset. Not platform SSO. |
| Providers | OCCPL network first; Shiprocket and Delhivery adapters behind the common interface, enabled per company only with credentials; Blue Dart and Ekart report "not configured" until a contract and API documentation exist. |
| Markup | Applies to **external providers only**; OCCPL network prices come from the customer's own rate card. Configurable percentage (initially 10 %), per company, optionally per provider/customer, with effective windows. |
| Repository | `occplsoftech/e-commerce-business`. |

## Customer authorization (phase 1)

- `users.customer_id` (V128) — set only for `CUSTOMER` accounts. Read by `auth.User.customerId`.
- **Scope gate** `shared.config.CustomerScopeFilter` (in the security chain after the JWT filter): a
  principal holding role `CUSTOMER` may call only `/api/v1/customer-portal/**` and `/api/v1/auth/**`;
  anything else is 403 before any controller runs. `/api/v1/customer-portal/**` itself requires role
  `CUSTOMER` (staff tokens get 403). Fail-closed: a token with `CUSTOMER` plus any other role is still
  gated.
- `CurrentCustomerResolver` resolves the acting customer on every portal request from the verified
  principal: user id -> `users.customer_id` (fresh from the database) -> an ACTIVE BUSINESS customer in
  the same company. Any gap is 403. Nothing in a request body or path ever names the customer.
- Provisioning: `POST /api/v1/customers/{id}/portal-access` (COMPANY_ADMIN) creates the `CUSTOMER`
  account for a business customer: PENDING, temporary password returned once, activation email.
  `GET` shows whether access exists; `DELETE` disables the account. One account per customer in phase 1.
- Emailed links (activation, password reset) for `CUSTOMER` accounts use
  `app.auth.customer-portal-base-url` (`CUSTOMER_PORTAL_BASE_URL`), falling back to `app-base-url`.

## Endpoints (phase 1)

| Method | Path | Who |
|---|---|---|
| GET | `/api/v1/customer-portal/profile` | CUSTOMER — own customer, company name, addresses |
| POST/GET/DELETE | `/api/v1/customers/{id}/portal-access` | COMPANY_ADMIN |
| GET | `/api/v1/customer-portal/dashboard` | CUSTOMER — counts by stage + 5 recent shipments (phase 2) |
| GET | `/api/v1/customer-portal/pincodes/{pincode}` | CUSTOMER — city/district + OCCPL network flags; 422 malformed, `known=false` unknown (phase 2) |

`POST /api/v1/auth/forgot-password` also accepts `companyCode` instead of `companyId` (phase 2): the portal
login form knows the company only by its code.

Dashboard stages: booked = BOOKED + READY_FOR_MANIFEST; in transit = MANIFEST_CREATED + DISPATCHED + IN_SCAN +
OUT_FOR_DELIVERY; delivered, returned, cancelled as is. A shipment belongs to the customer by
`shipments.business_customer_id` (`ShipmentPortalQueries`). `pendingBookings` is 0 until portal bookings exist
(phase 4: bookings not yet a shipment — submitting or outcome unknown).

## Schema (V128)

- `pricing_markup_rules` — percent (0-100, DECIMAL(7,4)), enabled flag, components it applies to
  (`FREIGHT` by default), scope (provider code / customer, NULL = all), effective window. Changes add a
  row and close the previous window; audit via `AuditService`.
- `rate_quotes` + `rate_quote_options` — one comparison: request snapshot (JSON), weights, and per
  provider service the frozen amounts (base freight, other charges, markup %, base and amount, tax,
  total), breakdown JSON, availability or the reason it is unavailable, validity.
- `portal_bookings` — idempotent per `(company, customer, idempotency_key)`; status
  PENDING -> SUBMITTING -> BOOKED | FAILED | OUTCOME_UNKNOWN; shipment id, provider reference, AWB,
  label. OUTCOME_UNKNOWN is never retried automatically.

## Pricing rules (for phase 3)

- `BigDecimal`, HALF_UP to 2 places at each line; markup = base x percent / 100 on the configured
  components only; tax computed after markup on the taxable base per provider contract, never twice;
  COD, fuel, remote-area fees follow the rule's `applies_to` list.
- Snapshot at quote time; a booking uses the snapshot, revalidating with the provider when the quote
  has expired or the provider requires it. A later markup change never alters an existing quote or booking.

## Phases

1. Architecture, customer authorization, data model — **done** (this file).
2. Portal repo: login, dashboard, shipment form, validation — **done** (backend: dashboard, pincode lookup, forgot-password by code).
3. Provider adapters (`CourierProviderAdapter`: serviceability, rates, book, track, label, cancel),
   OCCPL network adapter, Shiprocket/Delhivery adapters (mocked tests, sandbox when credentials exist),
   `/customer-portal/rates/compare`, markup administration.
4. Booking with idempotency and confirmation.
5. Shipment history, tracking, label download, profile/addresses.
6. Tests, security review, docs, environment and deployment.

## Known gaps after phase 1

- Customer accounts live in `users`, so staff **User Management** lists them too, and a company admin
  could add a staff role to one. The scope gate still keeps such an account inside the portal
  (fail-closed); hiding customer accounts from staff screens is a follow-up.
- Profile addresses carry geography ids, not names; name resolution comes with phase 5 (profile and
  address management).
- Not yet verified against MySQL or a running server: V128 and the security chain are covered by unit
  tests only (`CustomerScopeFilterTest` drives the filter, not the full chain).
