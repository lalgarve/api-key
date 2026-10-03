import pytest

from blackbox.list_table import parse_list_output

TABLE = """\
ID  CLIENT    CREATED_AT                   EXPIRES_AT                   REVOKED_AT  STATUS
1   bb-probe  2026-10-03T18:10:17.142071Z  2026-10-08T18:10:17.142071Z  -           active
12  bb-probe  2026-10-03T18:11:00Z         -                            2026-10-04T00:00:00Z  active
"""


def test_parses_rows_by_header():
    rows = parse_list_output(TABLE)
    assert [row["ID"] for row in rows] == ["1", "12"]
    assert rows[0]["REVOKED_AT"] == "-"
    assert rows[1]["EXPIRES_AT"] == "-"
    assert rows[1]["STATUS"] == "active"


def test_no_results_is_an_empty_list():
    assert parse_list_output("No API keys found for the given filters.\n") == []


@pytest.mark.parametrize("stdout", ["", "something else\n", "ID  CLIENT\n1\n"])
def test_rejects_output_that_is_not_the_list_contract(stdout):
    with pytest.raises(ValueError):
        parse_list_output(stdout)
