"""When steps: run one CLI command line, with {name} and {id:label} placeholders expanded."""

from behave import when, use_step_matcher

use_step_matcher("re")

# Well-formed (ApiKeyFormat) but never printed by generate.
NEVER_ISSUED_KEY = "dak_" + "A" * 43


@when(r'I run "(?P<command_line>[^"]*)"')
def run(context, command_line):
    context.state.run_command_line(command_line)


@when(r"I run '(?P<command_line>[^']*)'")
def run_single_quoted(context, command_line):
    context.state.run_command_line(command_line)


@when(r'I run "(?P<command_line>[^"]*)" with the key labeled "(?P<label>[^"]+)" on stdin')
def run_with_key(context, command_line, label):
    context.state.run_command_line(command_line, stdin=context.state.keys[label] + "\n")


@when(r'I run "(?P<command_line>[^"]*)" with "(?P<text>[^"]*)" on stdin')
def run_with_text(context, command_line, text):
    context.state.run_command_line(command_line, stdin=text + "\n")


@when(r'I run "(?P<command_line>[^"]*)" with empty stdin')
def run_with_empty_stdin(context, command_line):
    context.state.run_command_line(command_line, stdin="")


@when(r'I run "(?P<command_line>[^"]*)" with a well-formed key that was never issued on stdin')
def run_with_never_issued_key(context, command_line):
    context.state.run_command_line(command_line, stdin=NEVER_ISSUED_KEY + "\n")
