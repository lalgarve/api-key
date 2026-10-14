# Contract: specs/013-key-permissions/contracts/cli-commands.md and contracts/validation-api.md.
Feature: Per-key permissions

  Rule: instance with a permission catalog

    Background:
      Given the permission catalog is:
        | permission             | default |
        | email:send             | yes     |
        | disposable-email:admin | no      |

    Scenario: generate without --permissions grants the default permissions
      When the operator runs "generate --client jogo-acoes"
      Then the exit code is 0
      And the last line of stdout is "Permissions: email:send."
      And the new key has exactly the permissions "email:send"

    Scenario: generate with --permissions grants only the listed permissions
      When the operator runs "generate --client email-admin --permissions disposable-email:admin"
      Then the exit code is 0
      And the last line of stdout is "Permissions: disposable-email:admin."
      And the new key has exactly the permissions "disposable-email:admin"

    Scenario: generate with several permissions
      When the operator runs "generate --client email-admin --permissions email:send,disposable-email:admin"
      Then the exit code is 0
      And the new key has exactly the permissions "disposable-email:admin, email:send"

    Scenario: a repeated permission is granted once
      When the operator runs "generate --client jogo-acoes --permissions email:send,email:send"
      Then the exit code is 0
      And the new key has exactly the permissions "email:send"

    Scenario: generate with a permission that is not in the catalog
      When the operator runs "generate --client jogo-acoes --permissions email:delete"
      Then the exit code is 1
      And stderr is "Error: unknown permission 'email:delete'. Known permissions: disposable-email:admin, email:send."
      And stdout is empty
      And no key is saved

    Scenario: generate with an empty --permissions
      When the operator runs "generate --client jogo-acoes --permissions ,"
      Then the exit code is 1
      And stderr is "Error: --permissions must list at least one permission."
      And stdout is empty
      And no key is saved

    Scenario: validate shows the permissions of a valid key
      Given client "email-admin" has an active key with the permissions "disposable-email:admin"
      When the operator runs "validate" with that key on stdin
      Then the exit code is 0
      And stdout says the key is valid for client "email-admin" with the permissions "disposable-email:admin"

    Scenario: list shows a permissions column
      Given client "jogo-acoes" has an active key with the permissions "email:send"
      When the operator runs "list --client jogo-acoes"
      Then the exit code is 0
      And the last column of the header is "PERMISSIONS"
      And the row of that key ends with "email:send"

    Scenario: a key issued before 1.1.0 gets the default permissions
      Given client "jogo-acoes" has an active key issued before 1.1.0
      When the protected service validates that key
      Then the result is valid with the permissions "email:send"

  Rule: instance without a permission catalog (behaviour before this feature)

    Background:
      Given no permission catalog is configured

    Scenario: generate behaves exactly as in 1.0.x
      When the operator runs "generate --client jogo-acoes"
      Then the exit code is 0
      And stdout says the key was generated for client "jogo-acoes"
      And stdout does not contain "Permissions:"
      And the new key has no permissions

    Scenario: --permissions is rejected
      When the operator runs "generate --client jogo-acoes --permissions email:send"
      Then the exit code is 1
      And stderr is "Error: --permissions requires a permission catalog. None is configured for this instance."
      And stdout is empty
      And no key is saved

    Scenario: list behaves exactly as before this feature
      Given client "jogo-acoes" has an active key
      When the operator runs "list --client jogo-acoes"
      Then the last column of the header is "STATUS"

    Scenario: validate behaves exactly as in 010
      Given client "jogo-acoes" has an active key
      When the operator runs "validate" with that key on stdin
      Then stdout says the key is valid for client "jogo-acoes"

    Scenario: the validator returns no permissions
      Given client "jogo-acoes" has an active key
      When the protected service validates that key
      Then the result is valid with no permissions
