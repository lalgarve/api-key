"""Then steps: only the exit code, stdout and stderr of the last command (spec FR3)."""

import re

from behave import then, use_step_matcher

from blackbox.cli import KEY_PATTERN, mask_keys
from blackbox.list_table import parse_list_output

use_step_matcher("re")

def last(context):
    result = context.state.last
    assert result is not None, "no command was run in this scenario"
    return result


def check(condition, message, result):
    assert condition, f"{message}\n{result}"


def expect_exit_code(result, expected):
    check(result.exit_code == expected, f"expected exit code {expected}", result)


def without_final_newline(text):
    return text[:-1] if text.endswith("\n") else text


def expected_text(context, text):
    return context.state.expand(context.text if context.text is not None else text)


@then(r"the exit code is (?P<code>\d+)")
def exit_code(context, code):
    expect_exit_code(last(context), int(code))


@then(r'(?P<stream>stdout|stderr) is "(?P<text>[^"]*)"')
def stream_is(context, stream, text):
    result = last(context)
    expected = expected_text(context, text)
    actual = without_final_newline(getattr(result, stream))
    check(actual == expected, f"expected {stream} to be exactly {mask_keys(expected)!r}", result)


@then(r"(?P<stream>stdout|stderr) is:")
def stream_is_block(context, stream):
    stream_is(context, stream, None)


@then(r"(?P<stream>stdout|stderr) is empty")
def stream_is_empty(context, stream):
    result = last(context)
    check(getattr(result, stream) == "", f"expected {stream} to be empty", result)


@then(r'(?P<stream>stdout|stderr) contains "(?P<text>[^"]*)"')
def stream_contains(context, stream, text):
    result = last(context)
    expected = expected_text(context, text)
    check(expected in getattr(result, stream), f"expected {stream} to contain {expected!r}", result)


@then(r'(?P<stream>stdout|stderr) starts with "(?P<text>[^"]*)"')
def stream_starts_with(context, stream, text):
    result = last(context)
    expected = expected_text(context, text)
    check(getattr(result, stream).startswith(expected),
          f"expected {stream} to start with {expected!r}", result)


@then(r"exactly one key is printed on stdout")
def one_key_printed(context):
    result = last(context)
    lines = [line for line in result.stdout.splitlines() if KEY_PATTERN.fullmatch(line)]
    check(len(lines) == 1 and len(KEY_PATTERN.findall(result.stdout)) == 1,
          "expected exactly one dak_ key, alone on its own line, on stdout", result)


@then(r'neither stdout nor stderr contains the key labeled "(?P<label>[^"]+)"')
def key_not_echoed(context, label):
    result = last(context)
    key = context.state.keys[label]
    check(key not in result.stdout and key not in result.stderr,
          f"the plaintext key labeled {label!r} was printed", result)


def listed_rows(context):
    result = last(context)
    try:
        return parse_list_output(result.stdout)
    except ValueError as error:
        raise AssertionError(f"{error}\n{result}") from error


@then(r'the output lists the key labeled "(?P<label>[^"]+)"(?: with STATUS "(?P<status>[^"]+)")?')
def lists_key(context, label, status=None):
    key_id = str(context.state.key_id(label))
    rows = [row for row in listed_rows(context) if row["ID"] == key_id]
    check(len(rows) == 1, f"expected the key labeled {label!r} (id {key_id}) to be listed", last(context))
    if status is not None:
        check(rows[0]["STATUS"] == status,
              f"expected the key labeled {label!r} to have STATUS {status!r}", last(context))


@then(r'the output does not list the key labeled "(?P<label>[^"]+)"')
def does_not_list_key(context, label):
    key_id = str(context.state.key_id(label))
    check(all(row["ID"] != key_id for row in listed_rows(context)),
          f"expected the key labeled {label!r} (id {key_id}) not to be listed", last(context))


@then(r"the output lists (?P<count>\d+) keys?")
def lists_count(context, count):
    rows = listed_rows(context)
    check(len(rows) == int(count), f"expected {count} listed keys, got {len(rows)}", last(context))


@then(r'the key labeled "(?P<label>[^"]+)" has STATUS "(?P<status>[^"]+)"')
def key_has_status(context, label, status):
    row = context.state.row_for(label)
    assert row["STATUS"] == status, f"expected STATUS {status!r} for {label!r}, list shows {row}"


@then(r'the key labeled "(?P<label>[^"]+)" has REVOKED_AT "(?P<value>[^"]+)"')
def key_revoked_at(context, label, value):
    row = context.state.row_for(label)
    assert row["REVOKED_AT"] == value, f"expected REVOKED_AT {value!r} for {label!r}, list shows {row}"


@then(r'the key labeled "(?P<label>[^"]+)" has a REVOKED_AT in the future')
def key_revoked_in_future(context, label):
    row = context.state.row_for(label)
    assert row["REVOKED_AT"] != "-" and row["STATUS"] == "active", (
        f"expected a scheduled revocation for {label!r}, list shows {row}")


@then(r'client "(?P<name>[^"]+)" has exactly (?P<count>\d+) keys?')
def client_key_count(context, name, count):
    rows = context.state.list_client(context.state.client(name))
    assert len(rows) == int(count), f"expected {count} keys for {name!r}, list shows {rows}"


@then(r'the client name used for "(?P<name>[^"]+)" is unique to this scenario')
def client_name_unique(context, name):
    client = context.state.client(name)
    assert re.fullmatch(re.escape(name) + r"-[0-9a-f]{8}", client), client
    check(client in last(context).stdout, f"expected the list to show client {client!r}", last(context))


@then(r"the command ran from the packaged jar in its own process")
def ran_packaged_jar(context):
    result = last(context)
    check(result.args[1] == "-jar" and result.args[2] == str(context.jar)
          and result.args[2].endswith(".jar"), "expected java -jar <packaged jar>", result)


@then(r"a check expecting exit code 0 fails with a report of the command line, "
      r"exit code, stdout and stderr")
def failure_report(context):
    result = last(context)
    try:
        expect_exit_code(result, 0)
    except AssertionError as error:
        report = str(error)
    else:
        raise AssertionError(f"the check did not fail\n{result}")
    for part in ("command: ", " -jar ", f"exit code: {result.exit_code}",
                 "--- stdout ---", "--- stderr ---", result.stderr.strip()):
        assert part in report, f"failure report is missing {part!r}:\n{report}"


@then(r"the failure report of that run masks the printed key")
def failure_report_masks(context):
    result = last(context)
    printed = KEY_PATTERN.findall(result.stdout)
    assert printed, f"expected a key on stdout to mask:\n{result}"
    report = str(result)
    assert all(key not in report for key in printed), "the failure report shows the plaintext key"
    assert "dak_****" in report, f"expected a masked key in the report:\n{report}"
