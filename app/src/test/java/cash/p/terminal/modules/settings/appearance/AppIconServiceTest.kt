package cash.p.terminal.modules.settings.appearance

import android.content.ComponentName
import android.content.pm.PackageManager
import cash.p.terminal.core.App
import cash.p.terminal.core.ILocalStorage
import cash.p.terminal.trezor.client.TrezorUsbAttachActivity
import cash.p.terminal.widgets.MarketWidgetReceiver
import io.horizontalsystems.core.CoreApp
import io.mockk.MockKMatcherScope
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertFailsWith

@RunWith(RobolectricTestRunner::class)
class AppIconServiceTest {

    private val localStorage = mockk<ILocalStorage>(relaxed = true)
    private val packageManager = mockk<PackageManager>(relaxed = true)
    private val app = mockk<CoreApp>(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(App)
        every { App.instance } returns app
        every { app.packageName } returns "cash.p.terminal.dev"
        every { app.packageManager } returns packageManager
        every { localStorage.appIcon } returns AppIcon.Main
    }

    @After
    fun tearDown() {
        unmockkObject(App)
    }

    @Test
    fun init_pendingLauncherAliasUpdate_keepsTrezorUsbHandlerDisabled() {
        every { localStorage.calculatorModeLauncherAliasUpdatePending } returns true

        AppIconService(localStorage)

        verify(exactly = 1) {
            packageManager.setComponentEnabledSetting(any(), any(), any())
        }
        verifyTrezorUsbHandler(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    @Test
    fun applyPendingLauncherAliasUpdate_pending_appliesAliasesAndClearsPending() {
        every { localStorage.calculatorModeLauncherAliasUpdatePending } returns true
        every { localStorage.appIcon } returns AppIcon.Pirate
        val service = AppIconService(localStorage)
        clearMocks(packageManager)

        service.applyPendingLauncherAliasUpdate()

        verify { localStorage.calculatorModeLauncherAliasUpdatePending = false }
        // Three enabled components in non-calculator mode: the selected launcher alias,
        // market widget receiver, and Trezor USB handler.
        verify(exactly = 3) {
            packageManager.setComponentEnabledSetting(
                any(),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        verify(exactly = AppIcon.entries.size - 1) {
            packageManager.setComponentEnabledSetting(
                any(),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
        verifyTrezorUsbHandler(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    @Test
    fun init_ordinaryMode_enablesTrezorUsbHandler() {
        AppIconService(localStorage)

        verifyTrezorUsbHandler(PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }

    @Test
    fun init_calculatorMode_disablesTrezorUsbHandler() {
        every { localStorage.appIcon } returns AppIcon.Calculator

        AppIconService(localStorage)

        verifyTrezorUsbHandler(PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
    }

    @Test
    fun setAppIcon_calculatorMode_disablesTrezorUsbHandlerBeforePersistingMode() {
        val service = AppIconService(localStorage)
        clearMocks(packageManager, localStorage, answers = false)

        service.setAppIcon(AppIcon.Calculator)

        verifyOrder {
            packageManager.setComponentEnabledSetting(
                trezorUsbHandler(),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            localStorage.isCalculatorModeEnabled = true
        }
    }

    @Test
    fun setAppIcon_ordinaryModeWithDeferredAliases_persistsPendingBeforeOrdinaryState() {
        val service = AppIconService(localStorage)
        clearMocks(packageManager, localStorage, answers = false)

        service.setAppIcon(AppIcon.Main, updateLauncherAliases = false)

        verifyOrder {
            packageManager.setComponentEnabledSetting(
                trezorUsbHandler(),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
            localStorage.calculatorModeLauncherAliasUpdatePending = true
            localStorage.appIcon = AppIcon.Main
            localStorage.isCalculatorModeEnabled = false
            localStorage.previousAppIconName = null
        }
    }

    @Test
    fun setAppIcon_ordinaryMode_enablesTrezorUsbHandlerAfterBranding() {
        val service = AppIconService(localStorage)
        clearMocks(packageManager)
        val launcherAliasSettings = AppIcon.entries.map { icon ->
            ComponentName(app, icon.launcherName) to if (icon == AppIcon.Main) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
        }
        val marketWidgetComponent = ComponentName(app, MarketWidgetReceiver::class.java)
        val trezorUsbHandlerComponent = ComponentName(app, TrezorUsbAttachActivity::class.java)

        service.setAppIcon(AppIcon.Main)

        verifyOrder {
            launcherAliasSettings.forEach { (component, state) ->
                packageManager.setComponentEnabledSetting(
                    component,
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }
            packageManager.setComponentEnabledSetting(
                marketWidgetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            packageManager.setComponentEnabledSetting(
                trezorUsbHandlerComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    @Test
    fun setAppIcon_calculatorMode_handlerDisableFails_doesNotPersistCalculatorMode() {
        val service = AppIconService(localStorage)
        clearMocks(packageManager, localStorage, answers = false)
        every {
            packageManager.setComponentEnabledSetting(
                trezorUsbHandler(),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        } throws IllegalStateException()

        assertFailsWith<IllegalStateException> {
            service.setAppIcon(AppIcon.Calculator)
        }

        verify(exactly = 0) { localStorage.isCalculatorModeEnabled = true }
    }

    private fun verifyTrezorUsbHandler(state: Int) {
        verify {
            packageManager.setComponentEnabledSetting(
                trezorUsbHandler(),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    private fun MockKMatcherScope.trezorUsbHandler() = match<ComponentName> {
        it.className == TrezorUsbAttachActivity::class.java.name
    }
}
