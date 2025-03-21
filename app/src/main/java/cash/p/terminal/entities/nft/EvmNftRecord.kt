package cash.p.terminal.entities.nft

import io.horizontalsystems.core.entities.BlockchainType
import io.horizontalsystems.nftkit.models.NftType

class EvmNftRecord(
    blockchainType: BlockchainType,
    val nftType: NftType,
    val contractAddress: String,
    val tokenId: String,
    val tokenName: String?,
    balance: Int
) : NftRecord(blockchainType, balance) {

    override val nftUid: NftUid
        get() = NftUid.Evm(blockchainType, contractAddress, tokenId)

    override val displayName: String
        get() = tokenName ?: "#$tokenId"
}