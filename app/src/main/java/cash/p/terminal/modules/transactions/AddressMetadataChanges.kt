package cash.p.terminal.modules.transactions

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

internal fun addressMetadataChangesFlow(
    contactsFlow: Flow<*>,
    poisonAddressesChangedFlow: Flow<Unit>,
    labelsChangedFlow: Flow<Unit>,
): Flow<Unit> {
    return merge(
        contactsFlow.map { Unit },
        poisonAddressesChangedFlow,
        labelsChangedFlow,
    )
}
