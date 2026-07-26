# Additional structured Markdown observations

This file intentionally overlaps with the OpenSpec fixture to prove M18 conflict handling.

```morpheus specification
key=auth-session
title=Authentication Session Specification
description=Structured Markdown view of authentication sessions.
```

```morpheus requirement
key=auth-session/session-expiration
specification=auth-session
title=Session expiration
statement=The system SHALL expire an authenticated session after 45 minutes of inactivity.
```

```morpheus requirement
key=auth-session/audit-session-revocation
specification=auth-session
title=Audit session revocation
statement=The system SHALL record explicit session revocation events.
```

```morpheus change
key=add-remember-me
title=Add remember-me sessions
intent=Allow trusted devices to retain an authenticated session while preserving explicit revocation.
scope=trusted device opt-in;extended duration;explicit revocation
out_of_scope=multi-device synchronization
risks=session persistence on shared devices
```
