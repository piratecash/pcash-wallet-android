package io.horizontalsystems.bankwallet.modules.transactionInfo.options

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.navGraphViewModels
import io.horizontalsystems.bankwallet.R
import io.horizontalsystems.bankwallet.core.AppLogger
import io.horizontalsystems.bankwallet.core.BaseFragment
import io.horizontalsystems.bankwallet.core.ethereum.EthereumFeeViewModel
import io.horizontalsystems.bankwallet.core.setOnSingleClickListener
import io.horizontalsystems.bankwallet.modules.sendevmtransaction.SendEvmTransactionViewModel
import io.horizontalsystems.bankwallet.modules.transactionInfo.TransactionInfoOption
import io.horizontalsystems.bankwallet.modules.transactionInfo.TransactionInfoViewModel
import io.horizontalsystems.core.findNavController
import io.horizontalsystems.core.helpers.HudHelper
import io.horizontalsystems.snackbar.CustomSnackbar
import io.horizontalsystems.snackbar.SnackbarDuration
import kotlinx.android.synthetic.main.fragment_transaction_speedup_cancel.*

class TransactionSpeedUpCancelFragment : BaseFragment() {

    private val logger = AppLogger("tx-speedUp-cancel")
    private val transactionInfoViewModel by navGraphViewModels<TransactionInfoViewModel>(R.id.transactionInfoFragment)
    private val optionType by lazy { arguments?.getParcelable<TransactionInfoOption.Type>(OPTION_TYPE_KEY)!! }
    private val transactionHash by lazy { arguments?.getString(TRANSACTION_HASH_KEY)!! }

    private val vmFactory by lazy {
        TransactionInfoOptionsModule.Factory(
            optionType,
            transactionHash,
            transactionInfoViewModel.transactionWallet
        )
    }
    private val speedUpCancelViewModel by viewModels<TransactionSpeedUpCancelViewModel> { vmFactory }
    private val sendEvmTransactionViewModel by viewModels<SendEvmTransactionViewModel> { vmFactory }
    private val feeViewModel by viewModels<EthereumFeeViewModel> { vmFactory }

    private var snackbarInProcess: CustomSnackbar? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_transaction_speedup_cancel, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuClose -> {
                    findNavController().popBackStack(R.id.transactionInfoFragment, true)
                    true
                }
                else -> false
            }
        }

        toolbar.title = speedUpCancelViewModel.title
        sendButton.text = speedUpCancelViewModel.buttonTitle
        description.text = speedUpCancelViewModel.description

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        sendEvmTransactionViewModel.sendEnabledLiveData.observe(viewLifecycleOwner, { enabled ->
            sendButton.isEnabled = enabled
        })

        sendEvmTransactionViewModel.sendingLiveData.observe(viewLifecycleOwner, {
            snackbarInProcess = HudHelper.showInProcessMessage(
                requireView(),
                R.string.Send_Sending,
                SnackbarDuration.INDEFINITE
            )
        })

        sendEvmTransactionViewModel.sendSuccessLiveData.observe(viewLifecycleOwner, { transactionHash ->
            HudHelper.showSuccessMessage(
                requireActivity().findViewById(android.R.id.content),
                R.string.Hud_Text_Success
            )
            Handler(Looper.getMainLooper()).postDelayed({
                findNavController().popBackStack(R.id.transactionInfoFragment, true)
            }, 1200)
        })

        sendEvmTransactionViewModel.sendFailedLiveData.observe(viewLifecycleOwner, {
            HudHelper.showErrorMessage(requireActivity().findViewById(android.R.id.content), it)

            findNavController().popBackStack()
        })

        sendEvmTransactionView.init(
            sendEvmTransactionViewModel,
            feeViewModel,
            viewLifecycleOwner,
            parentFragmentManager,
            showSpeedInfoListener = {
                findNavController().navigate(
                    R.id.transactionSpeedUpCancelFragment_to_feeSpeedInfo,
                    null,
                    navOptions()
                )
            }
        )

        sendButton.setOnSingleClickListener {
            logger.info("click send button")
            sendEvmTransactionViewModel.send(logger)
        }
    }

    override fun onDestroyView() {
        snackbarInProcess?.dismiss()
        super.onDestroyView()
    }

    companion object {
        private const val OPTION_TYPE_KEY = "option_type_key"
        private const val TRANSACTION_HASH_KEY = "transaction_hash_key"

        fun prepareParams(
            optionType: TransactionInfoOption.Type,
            transactionHash: String
        ): Bundle {
            return bundleOf(
                OPTION_TYPE_KEY to optionType,
                TRANSACTION_HASH_KEY to transactionHash
            )
        }
    }

}