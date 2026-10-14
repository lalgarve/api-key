# Contract: specs/013-key-permissions/contracts/cli-commands.md.
# Every other feature runs without a catalog and is unchanged: that is the proof that an
# instance without permissions behaves exactly as before 1.1.0.
Feature: per-key permissions (black box)

  Background:
    Given the permission catalog is:
      | permission             | default |
      | email:send             | yes     |
      | disposable-email:admin | no      |

  Scenario: generate without --permissions grants the default permissions
    When I run "generate --client {acme}"
    Then the exit code is 0
    And exactly one key is printed on stdout
    And the last line of stdout is "Permissions: email:send."

  Scenario: generate with --permissions grants only the listed permissions
    When I run "generate --client {acme} --permissions disposable-email:admin,email:send"
    Then the exit code is 0
    And the last line of stdout is "Permissions: disposable-email:admin, email:send."

  Scenario: generate with a permission that is not in the catalog
    When I run "generate --client {acme} --permissions email:delete"
    Then the exit code is 1
    And stderr is "Error: unknown permission 'email:delete'. Known permissions: disposable-email:admin, email:send."
    And stdout is empty

  Scenario: list shows each key's permissions in the last column
    Given client "acme" has an active key labeled "k1"
    And client "acme" has an active key labeled "k2" with the permissions "disposable-email:admin"
    When I run "list --client {acme}"
    Then the exit code is 0
    And the key labeled "k1" has PERMISSIONS "email:send"
    And the key labeled "k2" has PERMISSIONS "disposable-email:admin"

  Scenario: validate shows the permissions of a valid key
    Given client "acme" has an active key labeled "k1" with the permissions "disposable-email:admin"
    When I run "validate" with the key labeled "k1" on stdin
    Then the exit code is 0
    And stdout is:
      """
      API key is valid for client '{acme}'.
      Permissions: disposable-email:admin.
      """

  Scenario: an invalid catalog stops every command with exit 2
    Given the permission catalog has no default permission
    When I run "list --client {acme}"
    Then the exit code is 2
    And stderr is "Error: api-key.permissions must mark at least one permission as default."
    And stdout is empty

  Scenario: --permissions without a catalog is rejected
    Given no permission catalog is configured
    When I run "generate --client {acme} --permissions email:send"
    Then the exit code is 1
    And stderr is "Error: --permissions requires a permission catalog. None is configured for this instance."
