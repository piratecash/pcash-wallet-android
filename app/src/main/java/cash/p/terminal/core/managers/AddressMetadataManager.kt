package cash.p.terminal.core.managers

import cash.p.terminal.modules.contacts.ContactsRepository
import cash.p.terminal.modules.contacts.model.Contact
import cash.p.terminal.strings.helpers.shorten
import io.horizontalsystems.core.entities.BlockchainType

data class AddressMetadata(
    val contact: Contact?,
    val label: String?,
)

class AddressMetadataManager(
    private val contactsRepository: ContactsRepository,
    private val addressLabelManager: AddressLabelManager,
) {
    fun get(blockchainType: BlockchainType, address: String): AddressMetadata {
        val contact = contactsRepository
            .getContactsFiltered(blockchainType, addressQuery = address)
            .firstOrNull()
        return AddressMetadata(
            contact = contact,
            label = if (contact == null) addressLabelManager.label(blockchainType, address) else null,
        )
    }

    fun mapped(blockchainType: BlockchainType, address: String): String {
        val metadata = get(blockchainType, address)
        return metadata.contact?.name ?: metadata.label ?: address.shorten()
    }
}
