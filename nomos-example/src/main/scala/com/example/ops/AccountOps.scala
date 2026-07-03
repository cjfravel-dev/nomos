package com.example.ops

import com.example.models.account.Account

/**
 * Derived behavior for a generated type belongs in ordinary, explicitly-named Scala — not
 * injected into the generated class. This extension object adds `label` to the generated
 * `Account`; callers opt in with `import com.example.ops.AccountOps._`.
 */
object AccountOps {
  implicit class AccountExtensions(private val account: Account) extends AnyVal {
    def label: String = account.accountId + "/" + account.active
  }
}
