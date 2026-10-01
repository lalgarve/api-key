# Executable counterpart of specs/004-list-api-keys/spec.md's "Cenários" section -- that
# spec.md now points here instead of duplicating this text inline.
Feature: List issued API keys

  Scenario: list with no filters shows only active keys
    Given client "jogo-acoes" has an active key labeled "active1"
    And client "jogo-acoes" has a key labeled "expired1" that already expired 10 days ago
    And client "jogo-acoes" has a key labeled "revoked1" that is already revoked
    When the operator runs "list --client jogo-acoes"
    Then the exit code is 0
    And the output lists the key labeled "active1"
    And the output does not list the key labeled "expired1"
    And the output does not list the key labeled "revoked1"

  Scenario: list all keys
    Given client "jogo-acoes" has an active key labeled "active1"
    And client "jogo-acoes" has a key labeled "expired1" that already expired 10 days ago
    And client "jogo-acoes" has a key labeled "revoked1" that is already revoked
    When the operator runs "list --client jogo-acoes --status all"
    Then the exit code is 0
    And the output lists the key labeled "active1"
    And the output lists the key labeled "expired1"
    And the output lists the key labeled "revoked1"

  Scenario: list only revoked keys
    Given client "jogo-acoes" has an active key labeled "active1"
    And client "jogo-acoes" has a key labeled "revoked1" that is already revoked
    When the operator runs "list --client jogo-acoes --status revoked"
    Then the exit code is 0
    And the output lists the key labeled "revoked1"
    And the output does not list the key labeled "active1"

  Scenario: list only expired keys
    Given client "jogo-acoes" has an active key labeled "active1"
    And client "jogo-acoes" has a key labeled "expired1" that already expired 10 days ago
    When the operator runs "list --client jogo-acoes --status expired"
    Then the exit code is 0
    And the output lists the key labeled "expired1"
    And the output does not list the key labeled "active1"

  Scenario: a key that is both expired and revoked shows as revoked
    Given client "jogo-acoes" has a key labeled "both1" that is both expired and revoked
    When the operator runs "list --client jogo-acoes --status all"
    Then the exit code is 0
    And the output lists the key labeled "both1" with status "revoked"

  Scenario: list filtered by client
    Given client "jogo-acoes" has an active key labeled "ours"
    And client "billing" has an active key labeled "theirs"
    When the operator runs "list --client jogo-acoes"
    Then the exit code is 0
    And the output lists the key labeled "ours"
    And the output does not list the key labeled "theirs"

  Scenario: list keys scheduled to be revoked soon
    Given client "jogo-acoes" has an active key labeled "soon" already scheduled to be revoked in 10 days
    And client "jogo-acoes" has an active key labeled "later" already scheduled to be revoked in 90 days
    When the operator runs "list --client jogo-acoes --revoking-within-days 30"
    Then the exit code is 0
    And the output lists the key labeled "soon"
    And the output does not list the key labeled "later"

  Scenario: --revoking-within-days combined with --status revoked is invalid
    When the operator runs "list --status revoked --revoking-within-days 30"
    Then the exit code is 1
    And stderr is "Error: --revoking-within-days can only be used with --status active."

  Scenario: --revoking-within-days combined with --status expired is invalid
    When the operator runs "list --status expired --revoking-within-days 30"
    Then the exit code is 1
    And stderr is "Error: --revoking-within-days can only be used with --status active."

  Scenario: no keys match the filters
    When the operator runs "list --client no-such-client-at-all"
    Then the exit code is 0
    And the output says no keys were found

  Scenario Outline: --revoking-within-days is invalid
    When the operator runs "list --revoking-within-days <value>"
    Then the exit code is 1
    And stderr is "Error: --revoking-within-days must be a positive integer."

    Examples:
      | value |
      | 0     |
      | -5    |
      | soon  |
