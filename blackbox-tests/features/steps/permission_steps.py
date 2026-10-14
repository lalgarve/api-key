"""Steps for permissions.feature (specs/013-key-permissions): the catalog reaches the CLI
through environment variables, the same way an operator would configure it."""

from behave import given, then, use_step_matcher

use_step_matcher("re")

CATALOG_PREFIX = "API_KEY_PERMISSIONS_"


def set_catalog(context, entries):
    clear_catalog(context)
    for index, (name, is_default) in enumerate(entries):
        context.state.env_overrides[f"{CATALOG_PREFIX}{index}_NAME"] = name
        context.state.env_overrides[f"{CATALOG_PREFIX}{index}_DEFAULT"] = "true" if is_default else "false"


def clear_catalog(context):
    for name in [n for n in context.state.env_overrides if n.startswith(CATALOG_PREFIX)]:
        del context.state.env_overrides[name]


@given(r"the permission catalog is:")
def catalog_is(context):
    set_catalog(context, [(row["permission"], row["default"] == "yes") for row in context.table])


@given(r"the permission catalog has no default permission")
def catalog_without_default(context):
    set_catalog(context, [("email:send", False)])


@given(r"no permission catalog is configured")
def no_catalog(context):
    clear_catalog(context)


@given(r'client "(?P<name>[^"]+)" has an active key labeled "(?P<label>[^"]+)" '
       r'with the permissions "(?P<permissions>[^"]+)"')
def active_key_with_permissions(context, name, label, permissions):
    context.state.generate(name, label, ["--permissions", permissions])


@then(r'the last line of stdout is "(?P<text>[^"]*)"')
def last_line_is(context, text):
    result = context.state.last
    assert result is not None, "no command was run in this scenario"
    lines = result.stdout.splitlines()
    assert lines and lines[-1] == text, f"expected the last line of stdout to be {text!r}\n{result}"


@then(r'the key labeled "(?P<label>[^"]+)" has PERMISSIONS "(?P<value>[^"]+)"')
def key_has_permissions(context, label, value):
    row = context.state.row_for(label)
    assert row.get("PERMISSIONS") == value, f"expected PERMISSIONS {value!r} for {label!r}, list shows {row}"
