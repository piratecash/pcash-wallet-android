package cash.p.terminal.modules.main

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import cash.p.terminal.MainGraphDirections
import cash.p.terminal.R
import cash.p.terminal.core.App
import cash.p.terminal.core.BaseActivity
import cash.p.terminal.core.navigateWithTermsAccepted
import cash.p.terminal.modules.createaccount.CreateAccountFragment
import cash.p.terminal.modules.intro.IntroActivity
import cash.p.terminal.modules.keystore.KeyStoreActivity
import cash.p.terminal.modules.lockscreen.LockScreenActivity
import cash.p.terminal.modules.tonconnect.TonConnectNewFragment
import cash.p.terminal.navigation.slideFromBottom
import cash.p.terminal.navigation.slideFromBottomForResult
import cash.p.terminal.navigation.slideFromRightClearingBackStack
import com.reown.walletkit.client.Wallet
import io.horizontalsystems.core.hideKeyboard
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class MainActivity : BaseActivity() {

    val viewModel: MainActivityViewModel by inject()

    override fun onResume() {
        super.onResume()
        viewModel.startLockScreenMonitoring()
        validate()
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopLockScreenMonitoring()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // If the intent is a deep link, pop back to the start destination
        if (intent.data != null && intent.action == Intent.ACTION_VIEW) {
            val navHost =
                supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
            val navController = navHost.navController
            navController.popBackStack(navController.graph.startDestinationId, false)
        }
        viewModel.setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val navHost =
            supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        val navController = navHost.navController

        navController.setGraph(R.navigation.main_graph, intent.extras)

        // Clear navigation state on process death when PIN is set.
        // This prevents crashes from ViewModels accessing authenticated state
        if (savedInstanceState != null && App.pinComponent.isPinSet) {
            navController.popBackStack(navController.graph.startDestinationId, false)
        }

        navController.addOnDestinationChangedListener { _, _, _ ->
            currentFocus?.hideKeyboard(this)
        }

        viewModel.navigateToMainLiveData.observe(this) {
            if (it) {
                navController.popBackStack(navController.graph.startDestinationId, false)
                viewModel.onNavigatedToMain()
            }
        }

        viewModel.wcEvent.observe(this) { wcEvent ->
            if (wcEvent != null) {
                when (wcEvent) {
                    is Wallet.Model.SessionRequest -> {
                        navController.slideFromBottom(R.id.wcRequestFragment)
                    }

                    is Wallet.Model.SessionProposal -> {
                        navController.slideFromBottom(
                            MainGraphDirections.actionGlobalToWcSessionFragment(null)
                        )
                    }

                    else -> {}
                }

                viewModel.onWcEventHandled()
            }
        }

        lifecycleScope.launch {
            viewModel.tcSendRequest.collect { tcEvent ->
                if (tcEvent != null) {
                    navController.slideFromBottom(R.id.tcSendRequestFragment)
                }
            }
        }

        viewModel.tcDappRequest.observe(this) { request ->
            if (request != null) {
                navController.slideFromBottomForResult<TonConnectNewFragment.Result>(
                    R.id.tcNewFragment,
                    request.dAppRequest
                ) { result ->
                    if (request.closeAppOnResult) {
                        if (result.approved) {
                            //Need delay to get connected before closing activity
                            closeAfterDelay()
                        } else {
                            finish()
                        }
                    }
                }
                viewModel.onTcDappRequestHandled()
            }
        }

        // Handle deeplink on cold start (only on fresh launch, not on recreation)
        if (savedInstanceState == null && intent.data != null && intent.action == Intent.ACTION_VIEW) {
            viewModel.setIntent(intent)
        }
    }

    private fun closeAfterDelay() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 1000)
    }

    private fun validate() = try {
        viewModel.validate()
    } catch (e: MainScreenValidationError.NoSystemLock) {
        KeyStoreActivity.startForNoSystemLock(this)
        finish()
    } catch (e: MainScreenValidationError.KeyInvalidated) {
        KeyStoreActivity.startForInvalidKey(this)
        finish()
    } catch (e: MainScreenValidationError.UserAuthentication) {
        KeyStoreActivity.startForUserAuthentication(this)
        finish()
    } catch (e: MainScreenValidationError.Welcome) {
        IntroActivity.start(this)
        finish()
    } catch (e: MainScreenValidationError.Unlock) {
        LockScreenActivity.start(this)
    } catch (e: MainScreenValidationError.KeystoreRuntimeException) {
        Toast.makeText(App.instance, "Issue with Keystore", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun findNavController(): NavController {
        val navHost =
            supportFragmentManager.findFragmentById(R.id.fragmentContainerView) as NavHostFragment
        return navHost.navController
    }

    fun openCreateNewWallet() {
        viewModel.selectBalanceTabOnNextLaunch()
        // Set flag to select Balance tab when returning to main screen
        // Open create wallet screen after PIN is created, clearing back stack to main screen
        findNavController().navigateWithTermsAccepted {
            findNavController().slideFromRightClearingBackStack(
                resId = R.id.createAccountFragment,
                popUpToId = R.id.mainFragment,
                input = CreateAccountFragment.Input(
                    popOffOnSuccess = R.id.mainFragment,
                    popOffInclusive = false
                )
            )
        }
    }
}
