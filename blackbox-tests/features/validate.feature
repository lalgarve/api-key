# Contract: specs/010-validate-api-key-cli/contracts/cli-commands.md.
# Pending until 010 is implemented: the PR that implements it removes the tag.
@pending
Feature: validate command (black box)

  Scenario: validate a key generated a moment ago
    Given client "acme" has an active key labeled "k1"
    When I run "validate" with the key labeled "k1" on stdin
    Then the exit code is 0
    And stdout is "API key is valid for client '{acme}'."
    And stderr is empty

  Scenario: validate a key whose revocation is scheduled but not reached yet
    Given client "acme" has an active key labeled "k1" scheduled to be revoked in 14 days
    When I run "validate" with the key labeled "k1" on stdin
    Then the exit code is 0

  Scenario: validate with no key on stdin
    When I run "validate" with empty stdin
    Then the exit code is 1
    And stderr is "Error: no API key provided on stdin."
    And stdout is empty

  Scenario: validate a key with the wrong format
    When I run "validate" with "not-a-key" on stdin
    Then the exit code is 2
    And stderr is "Invalid: the key is malformed."
    And stdout is empty

  Scenario: validate a key that was never issued
    When I run "validate" with a well-formed key that was never issued on stdin
    Then the exit code is 3
    And stderr is "Invalid: no API key matches."

  Scenario: validate a revoked key without echoing it
    Given client "acme" has a key labeled "k1" that is already revoked
    When I run "validate" with the key labeled "k1" on stdin
    Then the exit code is 4
    And stderr is "Invalid: the key was revoked."
    And neither stdout nor stderr contains the key labeled "k1"

  Scenario: validate an expired key
    Given client "acme" has a key labeled "k1" that already expired 10 days ago
    When I run "validate" with the key labeled "k1" on stdin
    Then the exit code is 5
    And stderr is "Invalid: the key expired."

  Scenario: validate a key that is both expired and revoked
    Given client "acme" has a key labeled "k1" that is both expired and revoked
    When I run "validate" with the key labeled "k1" on stdin
    Then the exit code is 4

  Scenario: validate never reads the key from the command line
    Given client "acme" has an active key labeled "k1"
    When I run "validate --key whatever" with empty stdin
    Then the exit code is 1
    And stderr is "Error: no API key provided on stdin."
