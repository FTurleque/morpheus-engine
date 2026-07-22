from __future__ import annotations

import argparse
import os
import random
import sqlite3
import statistics
import tempfile
import time


def benchmark(nodes: int, fanout: int, queries: int, seed: int) -> dict:
    random.seed(seed)
    edges: list[tuple[str, str, str]] = []
    for index in range(nodes):
        for offset in range(1, fanout + 1):
            if index + offset < nodes:
                edges.append((f"n{index}", "DEPENDS_ON", f"n{index + offset}"))

    adjacency: dict[str, list[str]] = {}
    for source, _, target in edges:
        adjacency.setdefault(source, []).append(target)

    starts = [f"n{random.randint(0, nodes - 10)}" for _ in range(queries)]

    memory_times: list[float] = []
    for start in starts:
        started = time.perf_counter()
        frontier = {start}
        visited = {start}
        for _ in range(3):
            next_frontier: set[str] = set()
            for node in frontier:
                for target in adjacency.get(node, []):
                    if target not in visited:
                        visited.add(target)
                        next_frontier.add(target)
            frontier = next_frontier
        memory_times.append(time.perf_counter() - started)

    with tempfile.TemporaryDirectory() as directory:
        db_path = os.path.join(directory, "traceability.sqlite")
        connection = sqlite3.connect(db_path)
        connection.execute("PRAGMA journal_mode=WAL")
        connection.execute(
            "CREATE TABLE links(source TEXT NOT NULL, relation TEXT NOT NULL, target TEXT NOT NULL)"
        )

        started = time.perf_counter()
        connection.executemany("INSERT INTO links VALUES(?,?,?)", edges)
        connection.execute("CREATE INDEX idx_links_source ON links(source)")
        connection.commit()
        load_seconds = time.perf_counter() - started

        query = """
        WITH RECURSIVE walk(node, depth) AS (
          SELECT ?, 0
          UNION ALL
          SELECT l.target, walk.depth + 1
          FROM walk JOIN links l ON l.source = walk.node
          WHERE walk.depth < 3
        )
        SELECT COUNT(DISTINCT node) FROM walk WHERE depth > 0
        """

        sqlite_times: list[float] = []
        for start in starts:
            query_started = time.perf_counter()
            connection.execute(query, (start,)).fetchone()
            sqlite_times.append(time.perf_counter() - query_started)

        connection.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        connection.close()
        db_size = os.path.getsize(db_path)

    return {
        "nodes": nodes,
        "edges": len(edges),
        "queries": queries,
        "seed": seed,
        "memory_p50_ms": statistics.median(memory_times) * 1000,
        "memory_max_ms": max(memory_times) * 1000,
        "sqlite_load_ms": load_seconds * 1000,
        "sqlite_p50_ms": statistics.median(sqlite_times) * 1000,
        "sqlite_p95_ms": statistics.quantiles(sqlite_times, n=20)[18] * 1000,
        "sqlite_max_ms": max(sqlite_times) * 1000,
        "database_bytes": db_size,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--nodes", type=int, default=20_000)
    parser.add_argument("--fanout", type=int, default=3)
    parser.add_argument("--queries", type=int, default=200)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    print(benchmark(args.nodes, args.fanout, args.queries, args.seed))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
