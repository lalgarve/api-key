# Executable counterpart of specs/008-validate-api-key/spec.md's "Cenários" section. Every
# scenario runs with the protected service's clock fixed (see CucumberSpringConfiguration), so
# "N days ago" is exact.
Feature: Validate an API key presented to the protected service

  Scenario: validate with no key presented
    When the service validates no key
    Then the key is rejected as "MISSING"

  Scenario: validate a blank key
    When the service validates the key "   "
    Then the key is rejected as "MISSING"

  Scenario Outline: validate a key with the wrong format
    When the service validates the key "<key>"
    Then the key is rejected as "MALFORMED"

    Examples:
      | key                                           |
      | not-a-key                                     |
      | dak_tooShort                                  |
      | sk_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA |

  Scenario: validate a key that was never issued
    Given a well-formed key labeled "ghost" that was never issued
    When the service validates the key labeled "ghost"
    Then the key is rejected as "NOT_FOUND"

  Scenario: validate a revoked key
    Given client "jogo-acoes" has a key labeled "k1" that was revoked 1 day ago
    When the service validates the key labeled "k1"
    Then the key is rejected as "REVOKED"

  Scenario: validate a key whose revocation is scheduled but not reached yet
    Given client "jogo-acoes" has a key labeled "k1" whose revocation is scheduled in 7 days
    When the service validates the key labeled "k1"
    Then the key is accepted for client "jogo-acoes"

  Scenario: validate an expired key
    Given client "jogo-acoes" has a key labeled "k1" that expired 1 day ago
    When the service validates the key labeled "k1"
    Then the key is rejected as "EXPIRED"

  Scenario: validate a key that is both expired and revoked
    Given client "jogo-acoes" has a key labeled "k1" that expired 10 days ago and was revoked 1 day ago
    When the service validates the key labeled "k1"
    Then the key is rejected as "REVOKED"

  Scenario: validate a currently active key
    Given client "jogo-acoes" has an active key labeled "k1"
    When the service validates the key labeled "k1"
    Then the key is accepted for client "jogo-acoes"

  Scenario: validate an active key that never expires
    Given client "billing" has a key labeled "k1" that never expires
    When the service validates the key labeled "k1"
    Then the key is accepted for client "billing"
