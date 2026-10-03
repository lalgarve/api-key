# Contract: specs/001-generate-api-key/contracts/cli-commands.md, extended by
# specs/003-auto-revoke-on-rotation/contracts/cli-commands.md.
Feature: generate command (black box)

  Scenario: generate a key that does not expire
    When I run "generate --client {acme}"
    Then the exit code is 0
    And stdout starts with "API key generated for client '{acme}' (does not expire)."
    And stdout contains "This is the only time the plaintext key is shown — store it now:"
    And exactly one key is printed on stdout
    And stderr is empty

  Scenario: generate a key with a validity period
    When I run "generate --client {acme} --validity-days 90"
    Then the exit code is 0
    And stdout starts with "API key generated for client '{acme}' (expires in 90 days)."
    And exactly one key is printed on stdout
    And stderr is empty

  Scenario: client name is required
    When I run "generate"
    Then the exit code is 1
    And stderr is "Error: --client is required."
    And stdout is empty

  Scenario: client name is blank
    When I run 'generate --client "  "'
    Then the exit code is 1
    And stderr is "Error: --client must not be blank."
    And stdout is empty

  Scenario Outline: validity in days must be a positive integer
    When I run "generate --client {acme} --validity-days <value>"
    Then the exit code is 1
    And stderr is "Error: --validity-days must be a positive integer."
    And stdout is empty
    And client "acme" has exactly 0 keys

    Examples:
      | value |
      | 0     |
      | -5    |
      | soon  |

  Scenario: HMAC pepper is not configured
    Given the environment variable "API_KEY_HMAC_PEPPER" is not set
    When I run "generate --client {acme}"
    Then the exit code is 2
    And stderr is "Error: HMAC pepper is not configured. Set the API_KEY_HMAC_PEPPER environment variable."
    And stdout is empty

  Scenario: generate without --revoke-old-in-days leaves the old key alone
    Given client "acme" has an active key labeled "old"
    When I run "generate --client {acme}"
    Then the exit code is 0
    And the key labeled "old" has REVOKED_AT "-"

  Scenario: rotation with a grace period schedules the old key's revocation
    Given client "acme" has an active key labeled "old"
    When I run "generate --client {acme} --revoke-old-in-days 7"
    Then the exit code is 0
    And stdout contains "1 existing key for client '{acme}' scheduled for revocation in 7 days (on "
    And the key labeled "old" has a REVOKED_AT in the future

  Scenario: rotation with an immediate cutover revokes the old key now
    Given client "acme" has an active key labeled "old"
    When I run "generate --client {acme} --revoke-old-in-days 0"
    Then the exit code is 0
    And stdout contains "1 existing key for client '{acme}' has been revoked."
    And the key labeled "old" has STATUS "revoked"

  Scenario Outline: --revoke-old-in-days must be zero or a positive integer
    When I run "generate --client {acme} --revoke-old-in-days <value>"
    Then the exit code is 1
    And stderr is "Error: --revoke-old-in-days must be zero or a positive integer."
    And client "acme" has exactly 0 keys

    Examples:
      | value |
      | -1    |
      | soon  |
