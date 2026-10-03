# Contract: specs/002-revoke-api-key/contracts/cli-commands.md.
Feature: revoke command (black box)

  Scenario: revoke a key immediately
    Given client "acme" has an active key labeled "k1"
    When I run "revoke --id {id:k1}"
    Then the exit code is 0
    And stdout is "API key {id:k1} for client '{acme}' has been revoked."
    And stderr is empty
    And the key labeled "k1" has STATUS "revoked"

  Scenario: revoke a key with a grace period
    Given client "acme" has an active key labeled "k1"
    When I run "revoke --id {id:k1} --in-days 14"
    Then the exit code is 0
    And stdout starts with "API key {id:k1} for client '{acme}' will be revoked in 14 days (on "
    And the key labeled "k1" has a REVOKED_AT in the future

  Scenario: --id is required
    When I run "revoke"
    Then the exit code is 1
    And stderr is "Error: --id is required."
    And stdout is empty

  Scenario: --id must be a number
    When I run "revoke --id abc"
    Then the exit code is 1
    And stderr is "Error: --id must be a number."

  Scenario Outline: --in-days must be a positive integer
    Given client "acme" has an active key labeled "k1"
    When I run "revoke --id {id:k1} --in-days <value>"
    Then the exit code is 1
    And stderr is "Error: --in-days must be a positive integer."
    And the key labeled "k1" has REVOKED_AT "-"

    Examples:
      | value |
      | 0     |
      | -5    |
      | soon  |

  Scenario: --id does not match any key
    When I run "revoke --id 9223372036854775807"
    Then the exit code is 2
    And stderr is "Error: no API key found with id 9223372036854775807."

  Scenario: key is already revoked
    Given client "acme" has a key labeled "k1" that is already revoked
    When I run "revoke --id {id:k1}"
    Then the exit code is 3
    And stderr is "Error: API key {id:k1} is already revoked."

  Scenario: --in-days would schedule the revocation past the key's own expiration
    Given client "acme" has an active key labeled "k1" expiring in 10 days
    When I run "revoke --id {id:k1} --in-days 20"
    Then the exit code is 1
    And stderr starts with "Error: --in-days would schedule the revocation after the key already expires (expires at "
    And the key labeled "k1" has REVOKED_AT "-"

  @pending
  Scenario: key has already expired
    Given client "acme" has a key labeled "k1" that already expired 10 days ago
    When I run "revoke --id {id:k1}"
    Then the exit code is 5
    And stderr starts with "Error: API key {id:k1} already expired on "
