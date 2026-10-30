# Spec Delta

## Purpose

Define which security headers every HTTP response of MORPHEUS carries, on the local server and on the remote facade,
whatever code path wrote the response.

## ADDED Requirements

### Requirement: Every response of the local server SHALL carry the same security headers

A response of the local server - success, application error, unknown route, refused method or refused origin - MUST
carry `Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
`Referrer-Policy: no-referrer` and `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`, and MUST NOT
carry `Access-Control-Allow-Origin` or `Strict-Transport-Security`.

#### Scenario: A success and a refusal are protected alike

- **WHEN** a started local server answers a successful request and a request with a refused origin
- **THEN** both responses carry the five headers with the same values

#### Scenario: An error is protected like a success

- **WHEN** the local server answers 400, 404, 405 and 409
- **THEN** each response carries the five headers

### Requirement: Every response of the remote facade SHALL add transport security

A response of the remote facade MUST carry the local set, `X-Request-Id` and
`Strict-Transport-Security: max-age=31536000`, and MUST NOT carry `Access-Control-Allow-Origin`.

#### Scenario: A proxied response carries HSTS

- **WHEN** the remote facade relays an upstream response
- **THEN** the response carries `Strict-Transport-Security: max-age=31536000` and the local set

#### Scenario: A refusal of the facade carries it too

- **WHEN** the remote facade answers 401, 403 or 429 by itself
- **THEN** the response carries the same headers

### Requirement: The header set SHALL be declared once per server

The local set MUST be declared in one place used by every local writer, and a response written without it MUST fail a
test that reads real responses.

#### Scenario: The origin refusal uses the shared set

- **WHEN** the sources are read after this change
- **THEN** the loopback guard sets no security header by itself

#### Scenario: A new response path is caught

- **WHEN** a code path writes a response without the shared writer
- **THEN** the behavioural test fails on the missing header for the route that uses it
