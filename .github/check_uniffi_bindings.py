#!/usr/bin/env python3
"""Compare generated UniFFI Kotlin bindings without depending on ktlint formatting."""

from __future__ import annotations

import argparse
from pathlib import Path


def kotlin_tokens(text: str) -> list[str]:
    tokens: list[str] = []
    index = 0
    length = len(text)
    while index < length:
        char = text[index]
        if char.isspace():
            index += 1
            continue
        if text.startswith("//", index):
            newline = text.find("\n", index + 2)
            index = length if newline < 0 else newline + 1
            continue
        if text.startswith("/*", index):
            index = skip_block_comment(text, index)
            continue
        if text.startswith('"""', index):
            end = text.find('"""', index + 3)
            if end < 0:
                raise ValueError("Unterminated Kotlin triple-quoted string")
            tokens.append(text[index : end + 3])
            index = end + 3
            continue
        if char in {'"', "'"}:
            token, index = read_quoted(text, index, char)
            tokens.append(token)
            continue
        if char.isalnum() or char == "_":
            end = index + 1
            while end < length and (text[end].isalnum() or text[end] == "_"):
                end += 1
            tokens.append(text[index:end])
            index = end
            continue
        tokens.append(char)
        index += 1
    return tokens


def skip_block_comment(text: str, start: int) -> int:
    depth = 1
    index = start + 2
    while index < len(text) and depth:
        if text.startswith("/*", index):
            depth += 1
            index += 2
        elif text.startswith("*/", index):
            depth -= 1
            index += 2
        else:
            index += 1
    if depth:
        raise ValueError("Unterminated Kotlin block comment")
    return index


def read_quoted(text: str, start: int, quote: str) -> tuple[str, int]:
    index = start + 1
    while index < len(text):
        if text[index] == "\\":
            index += 2
            continue
        if text[index] == quote:
            return text[start : index + 1], index + 1
        index += 1
    raise ValueError(f"Unterminated Kotlin {quote} literal")


def compare(tracked: Path, generated: Path) -> int:
    tracked_tokens = kotlin_tokens(tracked.read_text(encoding="utf-8"))
    generated_tokens = kotlin_tokens(generated.read_text(encoding="utf-8"))
    if tracked_tokens == generated_tokens:
        print("UniFFI Kotlin bindings are up to date.")
        return 0

    mismatch = next(
        (
            index
            for index, pair in enumerate(zip(tracked_tokens, generated_tokens))
            if pair[0] != pair[1]
        ),
        min(len(tracked_tokens), len(generated_tokens)),
    )
    start = max(0, mismatch - 8)
    end = mismatch + 9
    print("UniFFI Kotlin bindings are stale. Regenerate and commit the binding.")
    print(f"First structural mismatch at token {mismatch}.")
    print("tracked :", " ".join(tracked_tokens[start:end]))
    print("generated:", " ".join(generated_tokens[start:end]))
    return 1


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("tracked", type=Path)
    parser.add_argument("generated", type=Path)
    args = parser.parse_args()
    return compare(args.tracked, args.generated)


if __name__ == "__main__":
    raise SystemExit(main())
