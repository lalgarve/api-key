# The suite checking itself: specs/012-blackbox-cli-tests/spec.md, "Cenários".
# {name} in a command or expected text becomes this scenario's own client name.
Feature: Black-box test harness for the CLI

  Scenario: the suite runs the packaged jar, not the classes
    When I run "list --client {acme} --status all"
    Then the command ran from the packaged jar in its own process
    And the exit code is 0

  Scenario: each scenario gets its own client names
    Given client "acme" has an active key labeled "k1"
    When I run "list --client {acme} --status all"
    Then the output lists 1 key
    And the client name used for "acme" is unique to this scenario

  @pending
  Scenario: state is prepared only through the CLI
    Given client "acme" has a key labeled "old" that already expired 10 days ago
    When I run "list --client {acme} --status expired"
    Then the output lists the key labeled "old" with STATUS "expired"

  Scenario: the plaintext key is captured from generate's stdout
    When I run "generate --client {acme}"
    Then the exit code is 0
    And exactly one key is printed on stdout
    And stderr is empty

  Scenario: a failing check reports what the CLI printed
    When I run "revoke"
    Then a check expecting exit code 0 fails with a report of the command line, exit code, stdout and stderr

  Scenario: a failure report never shows the plaintext key
    When I run "generate --client {acme}"
    Then the failure report of that run masks the printed key
