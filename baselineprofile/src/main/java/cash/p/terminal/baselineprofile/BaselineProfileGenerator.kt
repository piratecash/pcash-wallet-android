package cash.p.terminal.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the app's Baseline Profile against the DISPOSABLE cash.p.terminal.baseline
 * sandbox. The journey creates its own throwaway wallets, so no real user data is ever
 * involved (the generation framework uninstalls the target app afterward — safe here,
 * never point it at a package that holds real wallets).
 *
 * BaselineProfileRule runs this block several times until the profile stabilises, and it
 * does NOT wipe app data between iterations. So the journey is written to be adaptive and
 * side-effect-free across runs: wallets are created only on the first (fresh) iteration —
 * detected by the onboarding UI — and reused afterwards. Later iterations skip creation
 * instantly (no wasted waits, no wallet pile-up) and just re-exercise the steady-state
 * cold-start-with-wallets and wallet switching.
 *
 * One run covers the whole cold-start surface, in this order:
 *  1. cold start with NO wallets -> onboarding
 *  2. create wallet #1, then wallet #2
 *  3. open the coin search / manage-coins list
 *  4. all bottom-nav tabs
 *  5. cold restart WITH two wallets -> the has-wallet startup path
 *  6. switch between the two wallets
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val targetPackage: String
        get() = requireNotNull(
            InstrumentationRegistry.getArguments().getString("androidx.benchmark.targetPackageName")
        ) { "androidx.benchmark.targetPackageName instrumentation argument is missing" }

    @Test
    fun generate() = rule.collect(
        packageName = targetPackage,
        // The app's network-driven async init (coin sync, prices) keeps the profile from
        // fully stabilising, so it would otherwise run all 15 iterations. Cap at 5: the
        // code paths are captured well before then; further iterations only churn on data.
        maxIterations = 5,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        onboardAndCreateTwoWalletsIfFresh()
        openCoinManagerSearch()
        exploreBottomTabs()

        // Cold restart — now with the two wallets present.
        killProcess()
        startActivityAndWait()
        device.waitForIdle()
        scrollBalance()

        switchBetweenWallets()
    }
}

private const val WAIT = 8_000L
private const val PROBE = 2_000L

/**
 * On a fresh install: pass the intro, create wallet #1 (via the empty-asset screen) and
 * wallet #2 (via the wallet switcher). If wallets already exist (a repeat iteration),
 * every probe fails fast and the whole block is skipped.
 */
private fun MacrobenchmarkScope.onboardAndCreateTwoWalletsIfFresh() {
    // Intro slides: one "Next" button, tapped 3 times; absent on repeat iterations.
    if (device.exists(By.text("Next"), PROBE)) {
        repeat(3) { device.tap(By.text("Next"), WAIT) }
    }

    // Empty-asset screen is shown only when there are no wallets yet.
    if (!device.exists(By.res("onboarding_create_wallet"), PROBE)) return

    // Wallet #1: from the empty-asset screen.
    device.tap(By.res("onboarding_create_wallet"), WAIT)
    acceptTerms()
    device.tap(By.text("CREATE"), WAIT)
    skipBackup()
    device.wait(Until.hasObject(By.desc("Balance")), WAIT)

    // Wallet #2: from the wallet switcher (terms already accepted this time).
    if (openWalletSwitcher()) {
        device.tap(By.text("New Wallet"), WAIT)
        device.tap(By.text("CREATE"), WAIT)
        skipBackup()
        device.wait(Until.hasObject(By.desc("Balance")), WAIT)
    }
}

private fun MacrobenchmarkScope.acceptTerms() {
    device.wait(Until.hasObject(By.res("terms_item")), WAIT)
    // Each row toggles on tap; the captured bounds stay valid across the checkbox recomposition.
    device.findObjects(By.res("terms_item")).forEach { it.click() }
    device.waitForIdle()
    device.tap(By.text("I Agree"), WAIT)
}

private fun MacrobenchmarkScope.skipBackup() {
    device.tap(By.text("Later"), WAIT)
    // Let the backup sheet fully dismiss before the next navigation, otherwise the
    // subsequent switcher navigation races the sheet dismissal and gets dropped.
    device.wait(Until.gone(By.text("Later")), WAIT)
    device.waitForIdle()
}

/**
 * Opens the wallet switcher (Manage Wallets) and confirms it is up. Retries once,
 * because a switcher tap fired right after another navigation can be swallowed.
 */
private fun MacrobenchmarkScope.openWalletSwitcher(): Boolean {
    repeat(2) {
        device.tap(By.res("wallet_switcher"), WAIT)
        if (device.wait(Until.hasObject(By.text("New Wallet")), PROBE)) return true
    }
    return false
}

private fun MacrobenchmarkScope.openCoinManagerSearch() {
    // Top search icon on the balance screen opens the (heavy) manage-coins list.
    if (device.tap(By.desc("Display Options"), WAIT)) {
        device.wait(Until.hasObject(By.desc("search")), WAIT)
        scrollBalance()
        device.pressBack()
        device.waitForIdle()
    }
}

private fun MacrobenchmarkScope.exploreBottomTabs() {
    listOf("Transactions", "Markets", "Settings", "Balance").forEach { tab ->
        device.tap(By.desc(tab), WAIT)
    }
}

private fun MacrobenchmarkScope.switchBetweenWallets() {
    if (openWalletSwitcher()) device.tap(By.text("Wallet 1"), WAIT)
    if (openWalletSwitcher()) device.tap(By.text("Wallet 2"), WAIT)
}

private fun MacrobenchmarkScope.scrollBalance() {
    // Re-query the scrollable before each fling: the balance list recomposes as coins
    // sync, so a cached handle goes stale mid-scroll (StaleObjectException).
    repeat(2) {
        (device.findObject(By.scrollable(true)) ?: return).fling(Direction.DOWN)
        device.waitForIdle()
        (device.findObject(By.scrollable(true)) ?: return).fling(Direction.UP)
        device.waitForIdle()
    }
}

private fun UiDevice.exists(selector: BySelector, timeout: Long): Boolean =
    wait(Until.hasObject(selector), timeout)

private fun UiDevice.tap(selector: BySelector, timeout: Long): Boolean {
    val obj = wait(Until.findObject(selector), timeout) ?: return false
    obj.click()
    waitForIdle()
    return true
}
