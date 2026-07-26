# Additional structured Markdown observations

This file intentionally overlaps with the OpenSpec fixture to prove M18 conflict handling.

```morpheus specification
key=auth-session
title=Authentication Session Specification
description=Structured Markdown view of authentication sessions.
```

```morpheus specification
key=auth-security
title=Authentication Security Specification
description=Alternative ownership scope used to prove explicit composition conflicts.
```

```morpheus requirement
key=auth-session/session-expiration
specification=auth-security
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

```morpheus change
key=auth-session/session-expiration
title=Rework session expiration
intent=Represent an intentionally ambiguous cross-type observation for the M18 identity conflict contract.
scope=session expiration policy
out_of_scope=authentication mechanism
risks=ambiguous ownership
```
