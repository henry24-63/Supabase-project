package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SupabaseAccount
import com.example.data.SupabaseManager
import com.example.ui.AccountViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("OTT Hub", appName)
  }

  @Test
  fun `test url protocol normalization and dashboard url resolution`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vm = AccountViewModel(SupabaseManager(context))

    // 1. Dashboard URL resolution
    val dashUrl = "https://supabase.com/dashboard/project/sjdlejiwqmkyfkvmodgd"
    assertEquals("https://sjdlejiwqmkyfkvmodgd.supabase.co", vm.cleanUrlProtocol(dashUrl))

    // 2. Trailing slashes and quotes removal
    val rawWithSlash = "\"https://xyz.supabase.co///\""
    assertEquals("https://xyz.supabase.co", vm.cleanUrlProtocol(rawWithSlash))

    // 3. Missing protocol prepends https
    val noProto = "myproject.supabase.co"
    assertEquals("https://myproject.supabase.co", vm.cleanUrlProtocol(noProto))

    // 4. Cleartext http preserved
    val httpUrl = "http://myproject.supabase.co"
    assertEquals("http://myproject.supabase.co", vm.cleanUrlProtocol(httpUrl))
  }

  @Test
  fun `test api key sanitization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vm = AccountViewModel(SupabaseManager(context))

    // Quotes and whitespace
    val rawKey = "  \"sb_publishable_1234567890\"  "
    assertEquals("sb_publishable_1234567890", vm.cleanApiKey(rawKey))

    // Prefix stripping
    val prefixedKey = "apikey: sb_publishable_1234567890"
    assertEquals("sb_publishable_1234567890", vm.cleanApiKey(prefixedKey))

    val bearerKey = "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"
    assertEquals("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9", vm.cleanApiKey(bearerKey))
  }

  @Test
  fun `test access code verification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val vm = AccountViewModel(SupabaseManager(context))

    assertTrue(vm.verifyPassword("Adnan@24??"))
  }

  @Test
  fun `test account persistence across repository re-initialization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager1 = SupabaseManager(context)
    val vm1 = AccountViewModel(manager1)

    // Save a test account
    val testAccount = SupabaseAccount(
      id = "acc_test_persistence_123",
      name = "OTT Main Supabase",
      projectUrl = "https://sjdlejiwqmkyfkvmodgd.supabase.co",
      apiKey = "sb_publishable_test_key_999",
      databasePassword = "test_password_123",
      routingTag = "ott-main"
    )
    manager1.addAccount(testAccount)
    vm1.refreshAccounts()
    assertTrue(vm1.accounts.value.any { it.id == "acc_test_persistence_123" })

    // Simulate app update / reboot: Create brand new manager & ViewModel
    val manager2 = SupabaseManager(context)
    val vm2 = AccountViewModel(manager2)

    val reloadedAccounts = vm2.accounts.value
    assertTrue("Account must persist across re-initialization", reloadedAccounts.any { it.id == "acc_test_persistence_123" })
    val saved = reloadedAccounts.first { it.id == "acc_test_persistence_123" }
    assertEquals("OTT Main Supabase", saved.name)
    assertEquals("https://sjdlejiwqmkyfkvmodgd.supabase.co", saved.projectUrl)
    assertEquals("ott-main", saved.effectiveTag)
  }

  @Test
  fun `test account deletion permanently removes account`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = SupabaseManager(context)
    val vm = AccountViewModel(manager)

    val tempAccount = SupabaseAccount(
      id = "delete_me_999",
      name = "Temporary DB",
      projectUrl = "https://temporary.supabase.co",
      apiKey = "sb_publishable_temp_999",
      databasePassword = "",
      routingTag = "temp-db"
    )
    manager.addAccount(tempAccount)
    vm.refreshAccounts()
    assertTrue(vm.accounts.value.any { it.id == "delete_me_999" })

    // Delete account
    vm.removeAccount("delete_me_999")
    assertFalse("Deleted account must be removed from ViewModel", vm.accounts.value.any { it.id == "delete_me_999" })

    // Re-instantiate manager to verify it does not resurrect
    val freshManager = SupabaseManager(context)
    val freshVm = AccountViewModel(freshManager)
    assertFalse("Deleted account must NOT resurrect after re-initialization", freshVm.accounts.value.any { it.id == "delete_me_999" })
  }

  @Test
  fun `test master key generation with connected accounts selection`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = SupabaseManager(context)
    val vm = AccountViewModel(manager)

    // Add two test accounts with routing tags
    val acc1 = SupabaseAccount("id_1", "Production OTT", "https://prod.supabase.co", "key1", "", "prod-ott")
    val acc2 = SupabaseAccount("id_2", "Analytics DB", "https://analytics.supabase.co", "key2", "", "analytics-db")
    manager.addAccount(acc1)
    manager.addAccount(acc2)
    vm.refreshAccounts()

    // 1. Generate master key with ALL accounts (universal)
    var universalKey: com.example.data.MasterKey? = null
    vm.createMasterKey("Universal Router Key", emptyList()) {
      universalKey = it
    }
    assertTrue("Universal key must be created", universalKey != null)
    assertTrue("Should be all accounts connected", universalKey!!.isAllAccountsConnected)
    assertTrue(universalKey!!.getConnectedSummary(vm.accounts.value).contains("All Accounts"))

    // 2. Generate master key with specific accounts
    var targetedKey: com.example.data.MasterKey? = null
    vm.createMasterKey("Targeted Client Key", listOf("id_1")) {
      targetedKey = it
    }
    assertTrue("Targeted key must be created", targetedKey != null)
    assertEquals(false, targetedKey!!.isAllAccountsConnected)
    assertEquals(listOf("id_1"), targetedKey!!.connectedAccountIds)
    assertEquals(listOf("Production OTT"), targetedKey!!.connectedAccountNames)
    assertTrue(targetedKey!!.getConnectedSummary(vm.accounts.value).contains("Production OTT"))

    // 3. Test Full Integration Copy text generation
    val integrationGuide = vm.buildFullMasterKeyIntegrationText(targetedKey!!, vm.accounts.value)
    assertTrue("Guide must contain key token", integrationGuide.contains(targetedKey!!.key))
    assertTrue("Guide must contain proxy URL", integrationGuide.contains("workers.dev"))
    assertTrue("Guide must mention direct string tag", integrationGuide.contains("x-target-account"))

    // Verify persistence in StateFlow
    val allKeys = vm.masterKeys.value
    assertTrue(allKeys.any { it.key == targetedKey!!.key })

    // Test toggle status
    vm.toggleMasterKey(targetedKey!!.key)
    val toggledKey = vm.masterKeys.value.first { it.key == targetedKey!!.key }
    assertEquals("REVOKED", toggledKey.status)

    // Test delete key
    vm.deleteMasterKey(targetedKey!!.key)
    assertTrue(vm.masterKeys.value.none { it.key == targetedKey!!.key })
  }
}
