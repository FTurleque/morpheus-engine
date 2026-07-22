from __future__ import annotations

import secrets
import time
import uuid


def generate_uuid7(
    *,
    now_ms: int | None = None,
    random_bits: int | None = None,
) -> uuid.UUID:
    """Experimental RFC-9562 UUIDv7 generator used only by M0 conformity tests."""
    timestamp = int(time.time() * 1000) if now_ms is None else now_ms
    if not 0 <= timestamp < (1 << 48):
        raise ValueError("timestamp must fit UUIDv7's 48-bit Unix millisecond field")

    randomness = secrets.randbits(74) if random_bits is None else random_bits
    if not 0 <= randomness < (1 << 74):
        raise ValueError("random_bits must fit 74 bits")

    rand_a = randomness >> 62
    rand_b = randomness & ((1 << 62) - 1)

    value = (
        (timestamp << 80)
        | (0x7 << 76)
        | (rand_a << 64)
        | (0b10 << 62)
        | rand_b
    )
    return uuid.UUID(int=value)


def extract_unix_ts_ms(value: uuid.UUID) -> int:
    """Conformity helper only. MORPHEUS domain consumers must treat IDs as opaque."""
    if value.version != 7:
        raise ValueError("not a UUIDv7")
    return (value.int >> 80) & ((1 << 48) - 1)


def canonical(value: uuid.UUID | str) -> str:
    return str(value if isinstance(value, uuid.UUID) else uuid.UUID(value)).lower()
