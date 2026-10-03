# Contract: specs/004-list-api-keys/contracts/cli-commands.md.
Feature: list command (black box)

  Scenario: list with no status shows only active keys
    Given client "acme" has an active key labeled "active1"
    And client "acme" has a key labeled "revoked1" that is already revoked
    When I run "list --client {acme}"
    Then the exit code is 0
    And the output lists the key labeled "active1" with STATUS "active"
    And the output does not list the key labeled "revoked1"
    And stderr is empty

  Scenario: list all keys
    Given client "acme" has an active key labeled "active1"
    And client "acme" has a key labeled "revoked1" that is already revoked
    When I run "list --client {acme} --status all"
    Then the exit code is 0
    And the output lists the key labeled "active1" with STATUS "active"
    And the output lists the key labeled "revoked1" with STATUS "revoked"

  Scenario: list only revoked keys
    Given client "acme" has an active key labeled "active1"
    And client "acme" has a key labeled "revoked1" that is already revoked
    When I run "list --client {acme} --status revoked"
    Then the exit code is 0
    And the output lists the key labeled "revoked1"
    And the output does not list the key labeled "active1"

  @pending
  Scenario: list only expired keys
    Given client "acme" has an active key labeled "active1"
    And client "acme" has a key labeled "expired1" that already expired 10 days ago
    When I run "list --client {acme} --status expired"
    Then the exit code is 0
    And the output lists the key labeled "expired1" with STATUS "expired"
    And the output does not list the key labeled "active1"

  @pending
  Scenario: a key that is both expired and revoked shows as revoked
    Given client "acme" has a key labeled "both" that is both expired and revoked
    When I run "list --client {acme} --status all"
    Then the output lists the key labeled "both" with STATUS "revoked"

  Scenario: a scheduled revocation keeps the key active
    Given client "acme" has an active key labeled "soon" scheduled to be revoked in 5 days
    When I run "list --client {acme}"
    Then the output lists the key labeled "soon" with STATUS "active"

  Scenario: list keys scheduled to be revoked soon
    Given client "acme" has an active key labeled "soon" scheduled to be revoked in 5 days
    And client "acme" has an active key labeled "later" scheduled to be revoked in 60 days
    And client "acme" has an active key labeled "never"
    When I run "list --client {acme} --revoking-within-days 30"
    Then the exit code is 0
    And the output lists the key labeled "soon"
    And the output does not list the key labeled "later"
    And the output does not list the key labeled "never"

  Scenario: no keys match the filters
    When I run "list --client {acme}"
    Then the exit code is 0
    And stdout is "No API keys found for the given filters."

  Scenario: unknown status
    When I run "list --status archived"
    Then the exit code is 1
    And stderr is "Error: --status must be one of: active, expired, revoked, all."
    And stdout is empty

  Scenario Outline: --revoking-within-days must be a positive integer
    When I run "list --revoking-within-days <value>"
    Then the exit code is 1
    And stderr is "Error: --revoking-within-days must be a positive integer."

    Examples:
      | value |
      | 0     |
      | -5    |
      | soon  |

  Scenario Outline: --revoking-within-days only works with --status active
    When I run "list --status <status> --revoking-within-days 30"
    Then the exit code is 1
    And stderr is "Error: --revoking-within-days can only be used with --status active."

    Examples:
      | status  |
      | revoked |
      | expired |
      | all     |
