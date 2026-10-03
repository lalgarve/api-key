"""behave hooks: find the jar once, then give every scenario its own client-name suffix."""

import sys

from blackbox.cli import JarNotFound, find_jar
from blackbox.scenario import ScenarioState


def before_all(context):
    try:
        context.jar = find_jar()
    except JarNotFound as error:
        abort(str(error))
    # A startup failure (database down, broken jar) exits with 1, the same code as a usage
    # error, so prove the CLI runs at all before any scenario reads an exit code.
    sanity = ScenarioState(context.jar).run(["list", "--client", "blackbox-sanity-check"])
    if sanity.exit_code != 0:
        abort(f"the CLI does not run.\n{sanity}")


def abort(reason):
    # behave captures output during hooks; write past the capture so the reason is visible.
    print(f"Black-box suite aborted: {reason}", file=sys.__stderr__)
    raise SystemExit(1)


def before_scenario(context, scenario):
    context.state = ScenarioState(context.jar)
