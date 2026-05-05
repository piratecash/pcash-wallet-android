package cash.p.terminal.core.di

import android.app.Application
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import cash.p.terminal.feature.miniapp.ui.connect.ConnectMiniAppViewModel
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusProvider
import cash.p.terminal.modules.blockchainstatus.BlockchainStatusViewModel
import cash.p.terminal.modules.balance.token.addresspoisoning.AddressPoisoningViewModel
import cash.p.terminal.modules.configuredtoken.ConfiguredTokenInfoViewModel
import cash.p.terminal.modules.multiswap.SwapSelectCoinViewModel
import cash.p.terminal.wallet.Account
import io.horizontalsystems.core.entities.BlockchainType
import cash.p.terminal.modules.createaccount.passphraseterms.PassphraseTermsViewModel
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesModule
import cash.p.terminal.modules.manageaccount.safetyrules.SafetyRulesViewModel
import cash.p.terminal.modules.multiswap.TimerService
import cash.p.terminal.modules.pin.hiddenwallet.HiddenWalletPinPolicy
import cash.p.terminal.modules.settings.advancedsecurity.AdvancedSecurityViewModel
import cash.p.terminal.modules.settings.advancedsecurity.securereset.SecureResetTermsViewModel
import cash.p.terminal.modules.settings.advancedsecurity.terms.DeleteContactsTermsViewModel
import cash.p.terminal.modules.settings.advancedsecurity.terms.HiddenWalletTermsViewModel
import cash.p.terminal.core.notifications.polling.TransactionPollingManager
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedDialog
import cash.p.terminal.modules.walletconnect.AccountTypeNotSupportedViewModel
import cash.p.terminal.wallet.Token
import io.horizontalsystems.core.IPinComponent
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.mockk.mockk
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.annotation.Provided
import org.koin.core.definition.BeanDefinition
import org.koin.core.module.Module
import org.koin.core.module.flatten
import org.koin.ext.getFullName
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.verify.ParameterTypeInjection
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility

class KoinGraphTest : KoinTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun verifyKoinGraph() {
        val testOverrides = module {
            single<Context> { mockk(relaxed = true) }
            single<Application> { mockk(relaxed = true) }
            single<HttpClientEngine> { OkHttp.create() }
        }

        val fullModule = module {
            includes(appModule, testOverrides)
        }

        val extraTypes =
            listOf(Application::class, Context::class, HttpClientEngine::class, TimerService::class)
        val injections = injectedParameters(
            definition<AccountTypeNotSupportedViewModel>(AccountTypeNotSupportedDialog.Input::class),
            definition<HiddenWalletPinPolicy>(IPinComponent::class),
            definition<AdvancedSecurityViewModel>(IPinComponent::class),
            definition<SecureResetTermsViewModel>(Array<String>::class),
            definition<DeleteContactsTermsViewModel>(Array<String>::class),
            definition<HiddenWalletTermsViewModel>(Array<String>::class),
            definition<PassphraseTermsViewModel>(Array<String>::class),
            definition<ConfiguredTokenInfoViewModel>(Token::class),
            definition<SafetyRulesViewModel>(SafetyRulesModule.SafetyRulesMode::class, List::class),
            definition<ConnectMiniAppViewModel>(SavedStateHandle::class),
            definition<BlockchainStatusViewModel>(BlockchainStatusProvider::class),
            definition<SwapSelectCoinViewModel>(Token::class, Account::class),
            definition<AddressPoisoningViewModel>(String::class, Boolean::class, BlockchainType::class),
            definition<TransactionPollingManager>(List::class),
        )

        fullModule.verify(
            extraTypes = extraTypes,
            injections = injections
        )
        fullModule.verifyOptionalConstructorParametersAreResolvable(extraTypes, injections)
    }

    @OptIn(KoinExperimentalAPI::class, KoinInternalApi::class)
    private fun Module.verifyOptionalConstructorParametersAreResolvable(
        extraTypes: List<KClass<*>>,
        injections: List<ParameterTypeInjection>
    ) {
        val allModules = flatten(includedModules.toList()) + this
        val definitionIndex = allModules.flatMap { it.mappings.keys }.toSet()
        val extraTypeNames = extraTypes.map { it.getFullName() }.toSet()
        val injectionIndex = injections.associate {
            it.targetType.getFullName() to it.injectedTypes.map(KClass<*>::getFullName)
        }

        val missingParameters = allModules
            .flatMap { it.mappings.values }
            .toSet()
            .filter { it.beanDefinition.isConstructorReferenceDefinition() }
            .flatMap { factory ->
                factory.beanDefinition.missingOptionalParameters(definitionIndex, extraTypeNames, injectionIndex)
            }

        check(missingParameters.isEmpty()) {
            missingParameters.joinToString(
                separator = "\n",
                prefix = "Optional constructor parameters must be resolvable by Koin:\n"
            )
        }
    }

    private fun BeanDefinition<*>.missingOptionalParameters(
        definitionIndex: Set<String>,
        extraTypeNames: Set<String>,
        injectionIndex: Map<String, List<String>>
    ): List<String> {
        val originTypeName = primaryType.getFullName()
        return primaryType.constructors
            .filter { it.visibility == KVisibility.PUBLIC }
            .flatMap { constructor ->
                constructor.parameters
                    .filter(KParameter::isOptional)
                    .filterNot { parameter ->
                        parameter.isResolvableFor(originTypeName, definitionIndex, extraTypeNames, injectionIndex)
                    }
                    .map { parameter ->
                        val typeName = parameter.injectedType()?.qualifiedName ?: parameter.type.toString()
                        "${primaryType.qualifiedName}.${parameter.name}: $typeName"
                    }
            }
    }

    private fun BeanDefinition<*>.isConstructorReferenceDefinition(): Boolean {
        val definitionClassName = definition.javaClass.name
        // Explicit Koin lambdas can choose which constructor parameters to pass; constructor DSL cannot.
        return constructorReferenceMarkers.any(definitionClassName::contains)
    }

    private fun KParameter.isResolvableFor(
        originTypeName: String,
        definitionIndex: Set<String>,
        extraTypeNames: Set<String>,
        injectionIndex: Map<String, List<String>>
    ): Boolean {
        if (annotations.any { it.annotationClass == InjectedParam::class || it.annotationClass == Provided::class }) {
            return true
        }

        val typeName = injectedType()?.getFullName() ?: return false
        return definitionIndex.any { it.contains(typeName) } ||
            typeName in extraTypeNames ||
            typeName in injectionIndex[originTypeName].orEmpty()
    }

    private fun KParameter.injectedType(): KClass<*>? {
        val classifier = type.classifier as? KClass<*> ?: return null
        if (classifier.simpleName == "Lazy" || classifier.simpleName == "List") {
            return type.arguments.firstOrNull()?.type?.classifier as? KClass<*>
        }
        return classifier
    }

    companion object {
        private val constructorReferenceMarkers = listOf("singleOf", "factoryOf", "viewModelOf")
    }
}
