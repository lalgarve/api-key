# Executable counterpart of specs/002-revoke-api-key/spec.md's "Cenários" section -- that
# spec.md now points here instead of duplicating this text inline.
Feature: Revoke an API key

  Scenario: revoke a key immediately
    Given client "jogo-acoes" has an active key labeled "3"
    When the operator runs "revoke --id 3"
    Then the exit code is 0
    And the key labeled "3" is revoked

  Scenario: revoke a key with a grace period
    Given client "jogo-acoes" has an active key labeled "3"
    When the operator runs "revoke --id 3 --in-days 14"
    Then the exit code is 0
    And the key labeled "3" is not revoked
    And the key labeled "3" is scheduled to be revoked in about 14 days

  Scenario: --id is required
    When the operator runs "revoke"
    Then the exit code is 1
    And stderr is "Error: --id is required."

  Scenario: --id does not match any key
    Given no key exists with id "999999"
    When the operator runs "revoke --id 999999"
    Then the exit code is 2
    And stderr is "Error: no API key found with id 999999."

  Scenario Outline: --in-days is invalid
    Given client "jogo-acoes" has an active key labeled "3"
    When the operator runs "revoke --id 3 --in-days <value>"
    Then the exit code is 1
    And stderr is "Error: --in-days must be a positive integer."
    And the key labeled "3" is not revoked

    Examples:
      | value |
      | 0     |
      | -5    |
      | soon  |

  Scenario: key is already revoked
    Given client "jogo-acoes" has a key labeled "3" that is already revoked
    When the operator runs "revoke --id 3"
    Then the exit code is 3
    And stderr contains "is already revoked"

  Scenario: key already has a future revocation scheduled
    Given client "jogo-acoes" has an active key labeled "3" already scheduled to be revoked in 30 days
    When the operator runs "revoke --id 3 --in-days 5"
    Then the exit code is 0
    And the key labeled "3" is scheduled to be revoked in about 5 days

  Scenario: key has already expired
    Given client "jogo-acoes" has a key labeled "3" that already expired 10 days ago
    When the operator runs "revoke --id 3"
    Then the exit code is 5
    And stderr contains "already expired"

  Scenario: --in-days would schedule the revocation past the key's own expiration
    Given client "jogo-acoes" has an active key labeled "3" expiring in 10 days
    When the operator runs "revoke --id 3 --in-days 20"
    Then the exit code is 1
    And stderr contains "would schedule the revocation after the key already expires"
    And the key labeled "3" is not revoked
