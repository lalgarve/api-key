"""Reads the table printed by `list` (specs/004-list-api-keys/contracts/cli-commands.md)."""

import re

NO_KEYS_MESSAGE = "No API keys found for the given filters."
COLUMN_SEPARATOR = re.compile(r"\s{2,}")


def parse_list_output(stdout):
    """Returns one dict per row, keyed by column header; an empty list when nothing matched."""
    lines = [line for line in stdout.splitlines() if line.strip()]
    if lines == [NO_KEYS_MESSAGE]:
        return []
    if not lines:
        raise ValueError("list printed nothing on stdout")
    header = COLUMN_SEPARATOR.split(lines[0].strip())
    if header[0] != "ID":
        raise ValueError(f"unexpected list header: {lines[0]!r}")
    rows = []
    for line in lines[1:]:
        cells = COLUMN_SEPARATOR.split(line.strip())
        if len(cells) != len(header):
            raise ValueError(f"list row does not match the header: {line!r}")
        rows.append(dict(zip(header, cells)))
    return rows
