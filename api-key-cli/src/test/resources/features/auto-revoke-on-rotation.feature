# Executable counterpart of specs/003-auto-revoke-on-rotation/spec.md's "Cenários" section --
# that spec.md now points here instead of duplicating this text inline.
Feature: Auto-revoke old keys when rotating

  Scenario: generate for a client with no pre-existing active key
    When the operator runs "generate --client jogo-acoes"
    Then the exit code is 0
    And the client "jogo-acoes" has exactly 1 key stored

  Scenario: generate for a client with an existing active key, without --revoke-old-in-days
    Given client "jogo-acoes" has an active key labeled "3"
    When the operator runs "generate --client jogo-acoes"
    Then the exit code is 0
    And the key labeled "3" is not revoked
    And the client "jogo-acoes" has exactly 2 keys stored

  Scenario: generate with an explicit grace period
    Given client "jogo-acoes" has an active key labeled "3"
    When the operator runs "generate --client jogo-acoes --revoke-old-in-days 3"
    Then the exit code is 0
    And the key labeled "3" is scheduled to be revoked in about 3 days

  Scenario: generate with an immediate cutover
    Given client "jogo-acoes" has an active key labeled "3"
    When the operator runs "generate --client jogo-acoes --revoke-old-in-days 0"
    Then the exit code is 0
    And the key labeled "3" is revoked

  Scenario: generate for a client with multiple pre-existing active keys
    Given client "jogo-acoes" has an active key labeled "3"
    And client "jogo-acoes" has an active key labeled "5"
    When the operator runs "generate --client jogo-acoes --revoke-old-in-days 7"
    Then the exit code is 0
    And the key labeled "3" is scheduled to be revoked in about 7 days
    And the key labeled "5" is scheduled to be revoked in about 7 days

  Scenario: an old key already has an earlier scheduled revocation
    Given client "jogo-acoes" has an active key labeled "3" already scheduled to be revoked in 2 days
    When the operator runs "generate --client jogo-acoes --revoke-old-in-days 7"
    Then the exit code is 0
    And the key labeled "3" is scheduled to be revoked in about 2 days

  Scenario Outline: --revoke-old-in-days is invalid
    Given client "jogo-acoes" has an active key labeled "3"
    When the operator runs "generate --client jogo-acoes --revoke-old-in-days <value>"
    Then the exit code is 1
    And stderr is "Error: --revoke-old-in-days must be zero or a positive integer."
    And the key labeled "3" is not revoked
    And the client "jogo-acoes" has exactly 1 key stored

    Examples:
      | value |
      | -1    |
      | soon  |

  Scenario: persisting the old-key revocation fails
    Given client "jogo-acoes" has an active key labeled "3"
    And the database rejects the next update to the key labeled "3"
    When the operator runs "generate --client jogo-acoes --revoke-old-in-days 7"
    Then the exit code is 3
    And stdout is empty
    And the key labeled "3" is not revoked
    And the client "jogo-acoes" has exactly 1 key stored
