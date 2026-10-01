# Executable counterpart of specs/001-generate-api-key/spec.md's "Cenários" section -- that
# spec.md now points here instead of duplicating this text inline.
Feature: Generate an API key

  Scenario: generate a new key for a named client
    When the operator runs "generate --client jogo-acoes"
    Then the exit code is 0
    And stdout says the key was generated for client "jogo-acoes"
    And a dak_-prefixed key is printed to stdout exactly once
    And the client "jogo-acoes" has exactly 1 key stored
    And the stored key has no plaintext value anywhere

  Scenario: client name is required
    When the operator runs "generate"
    Then the exit code is 1
    And stderr is "Error: --client is required."
    And stdout is empty

  Scenario: client name is blank
    When the operator runs 'generate --client ""'
    Then the exit code is 1
    And stderr is "Error: --client must not be blank."
    And stdout is empty

  Scenario: HMAC pepper is not configured
    Given the HMAC pepper is not configured
    When the operator runs "generate --client jogo-acoes"
    Then the exit code is 2
    And stderr is "Error: HMAC pepper is not configured. Set the API_KEY_HMAC_PEPPER environment variable."
    And stdout is empty
    And the client "jogo-acoes" has exactly 0 keys stored

  Scenario: generate a key with a validity period
    When the operator runs "generate --client jogo-acoes --validity-days 90"
    Then the exit code is 0
    And the only key for client "jogo-acoes" expires in about 90 days

  Scenario: generate a key without a validity period
    When the operator runs "generate --client jogo-acoes"
    Then the exit code is 0
    And the only key for client "jogo-acoes" has no expiration

  Scenario Outline: validity in days must be a positive integer
    When the operator runs "generate --client jogo-acoes --validity-days <value>"
    Then the exit code is 1
    And stderr is "Error: --validity-days must be a positive integer."
    And the client "jogo-acoes" has exactly 0 keys stored

    Examples:
      | value |
      | 0     |
      | -5    |
      | soon  |
