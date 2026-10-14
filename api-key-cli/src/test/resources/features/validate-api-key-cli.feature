# Contract: specs/010-validate-api-key-cli/contracts/cli-commands.md.
Feature: Validate an API key from the command line

  Scenario: validate a currently active key
    Given client "jogo-acoes" has an active key
    When the operator runs "validate" with that key on stdin
    Then the exit code is 0
    And stdout says the key is valid for client "jogo-acoes"

  Scenario: validate an active key that never expires
    Given client "jogo-acoes" has an active key that never expires
    When the operator runs "validate" with that key on stdin
    Then the exit code is 0

  Scenario: validate a key whose revocation is scheduled but not reached yet
    Given client "jogo-acoes" has a key scheduled to be revoked in 14 days
    When the operator runs "validate" with that key on stdin
    Then the exit code is 0

  Scenario: validate with no key on stdin
    When the operator runs "validate" with empty stdin
    Then the exit code is 1
    And stderr is "Error: no API key provided on stdin."

  Scenario: validate a key with the wrong format
    When the operator runs "validate" with "not-a-key" on stdin
    Then the exit code is 2
    And stderr is "Invalid: the key is malformed."

  Scenario: validate a key that was never issued
    When the operator runs "validate" with a well-formed key that was never issued on stdin
    Then the exit code is 3
    And stderr is "Invalid: no API key matches."

  Scenario: validate a revoked key
    Given client "jogo-acoes" has a revoked key
    When the operator runs "validate" with that key on stdin
    Then the exit code is 4
    And stderr is "Invalid: the key was revoked."

  Scenario: validate an expired key
    Given client "jogo-acoes" has an expired key
    When the operator runs "validate" with that key on stdin
    Then the exit code is 5
    And stderr is "Invalid: the key expired."

  Scenario: validate a key that is both expired and revoked
    Given client "jogo-acoes" has a key that is both expired and revoked
    When the operator runs "validate" with that key on stdin
    Then the exit code is 4
