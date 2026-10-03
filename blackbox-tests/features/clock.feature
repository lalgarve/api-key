# Contract: specs/011-configurable-clock/contracts/cli-options.md.
# Pending until 011 is implemented: the PR that implements it removes the tag.
@pending
Feature: clock options (black box)

  Scenario: a simulated clock warns on stderr and keeps stdout clean
    Given client "acme" has an active key labeled "k1" expiring in 30 days
    When I run "list --client {acme} --status all --clock-offset-days 31"
    Then the exit code is 0
    And stderr starts with "Warning: simulated clock in use; now is "
    And the output lists the key labeled "k1" with STATUS "expired"

  Scenario: no clock option prints no warning
    When I run "list --client {acme}"
    Then stderr is empty

  Scenario: zero offset is the same as no offset
    When I run "list --client {acme} --clock-offset-days 0"
    Then the exit code is 0
    And stderr is empty

  Scenario: a negative offset sees a revoked key as still active
    Given client "acme" has a key labeled "k1" that is already revoked
    When I run "list --client {acme} --status all --clock-offset-days -1"
    Then the output lists the key labeled "k1" with STATUS "active"

  Scenario: generate with a start date records that date
    When I run "generate --client {acme} --validity-days 10 --clock-start 2026-01-01"
    Then the exit code is 0
    And stderr starts with "Warning: simulated clock in use; now is 2026-01-01T00:00:"

  Scenario: start date accepts a full UTC instant
    When I run "generate --client {acme} --clock-start 2026-01-01T15:30:00Z"
    Then the exit code is 0
    And stderr starts with "Warning: simulated clock in use; now is 2026-01-01T15:30:"

  Scenario: both clock options together are rejected
    When I run "list --clock-start 2026-01-01 --clock-offset-days 3"
    Then the exit code is 1
    And stderr is "Error: --clock-start and --clock-offset-days cannot be used together."
    And stdout is empty

  Scenario: an invalid start date is rejected
    When I run "list --clock-start 2026-13-01"
    Then the exit code is 1
    And stderr is "Error: --clock-start must be an ISO-8601 date (2026-01-31) or UTC instant (2026-01-31T10:00:00Z)."

  Scenario: a non-integer offset is rejected
    When I run "list --clock-offset-days abc"
    Then the exit code is 1
    And stderr is "Error: --clock-offset-days must be an integer (negative, zero or positive)."

  Scenario: clock options are refused in staging
    Given the environment variable "SPRING_PROFILES_ACTIVE" is "staging"
    When I run "list --clock-offset-days 31"
    Then the exit code is 1
    And stderr is "Error: clock options are not allowed in the staging or production environment."
