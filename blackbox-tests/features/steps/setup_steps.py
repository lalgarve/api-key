"""Given steps: every bit of state is built by running the CLI itself (spec FR4)."""

from datetime import datetime, timedelta, timezone

from behave import given, use_step_matcher

use_step_matcher("re")


def days_ago(days):
    return (datetime.now(timezone.utc) - timedelta(days=days)).strftime("%Y-%m-%dT%H:%M:%SZ")


def assert_status(context, label, expected):
    row = context.state.row_for(label)
    assert row["STATUS"] == expected, (
        f"setup expected key {label!r} to be {expected}, but list shows {row}")


@given(r'client "(?P<name>[^"]+)" has an active key labeled "(?P<label>[^"]+)"')
def active_key(context, name, label):
    context.state.generate(name, label)


@given(r'client "(?P<name>[^"]+)" has an active key labeled "(?P<label>[^"]+)" '
       r'expiring in (?P<days>\d+) days')
def active_key_expiring(context, name, label, days):
    context.state.generate(name, label, ["--validity-days", days])


@given(r'client "(?P<name>[^"]+)" has a key labeled "(?P<label>[^"]+)" that is already revoked')
def revoked_key(context, name, label):
    context.state.generate(name, label)
    context.state.run_setup(["revoke", "--id", str(context.state.key_id(label))])


@given(r'client "(?P<name>[^"]+)" has an active key labeled "(?P<label>[^"]+)" '
       r'scheduled to be revoked in (?P<days>\d+) days')
def scheduled_key(context, name, label, days):
    context.state.generate(name, label)
    context.state.run_setup(["revoke", "--id", str(context.state.key_id(label)), "--in-days", days])


@given(r'client "(?P<name>[^"]+)" has a key labeled "(?P<label>[^"]+)" '
       r'that already expired (?P<days>\d+) days ago')
def expired_key(context, name, label, days):
    # Generated "in the past" with --clock-start (specs/011-configurable-clock), valid for one day.
    context.state.generate(name, label,
                           ["--validity-days", "1", "--clock-start", days_ago(int(days) + 1)])
    assert_status(context, label, "expired")


@given(r'client "(?P<name>[^"]+)" has a key labeled "(?P<label>[^"]+)" '
       r'that is both expired and revoked')
def expired_and_revoked_key(context, name, label):
    expired_key(context, name, label, "10")
    # Revoking an expired key is refused (exit 5), so revoke it "in the past" too.
    context.state.run_setup(["revoke", "--id", str(context.state.key_id(label)),
                             "--clock-start", days_ago(10)])
    assert_status(context, label, "revoked")


@given(r'the environment variable "(?P<name>[^"]+)" is not set')
def variable_not_set(context, name):
    context.state.env_removed.add(name)


@given(r'the environment variable "(?P<name>[^"]+)" is "(?P<value>[^"]*)"')
def variable_set(context, name, value):
    context.state.env_overrides[name] = value
