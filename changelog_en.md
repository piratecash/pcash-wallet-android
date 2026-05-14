## 🚀 Version 0.54.3 Update
_Release date: May 7, 2026_

### ✨ Improvements

- **Improved the Zcash loading indicator when scanning from block zero**

- **Added the ability to exclude a swap provider from the list of swap offers**

- **Added staking payout information**
  The app now shows when the next payout is expected and when holding rewards will be credited.

- **Improved the synchronization mechanism for BEP-20 tokens**

### 🐛 Fixes

- **Improved handling of `429` errors in the TON SDK**

- **Fixed a crash after closing the Tangem card scanning flow**

- **Reworked the password visibility behavior in input fields**

## 🚀 Version 0.54.2 Update
_Release date: April 21, 2026_

### ✨ Improvements

- **Added an extra security option for Duress Mode**
  You can now automatically clear the address book when entering `duress mode`.

- **Added support for multiple providers on the TRON network**

- **Added background notifications for new transactions**
  The wallet can now notify you about new transactions even while the app is running in the background.

- **Improved the token tree view**

- **Improved swap loss display**

- **Improved the fee balance warning logic**
  The app now shows the warning about missing native coins for network fees only after blockchain synchronization is complete and only if the actual balance is truly zero.

### 🐛 Fixes

- **Improved stability for Monero, Bitcoin, and Tangem wallet flows**
  Accounts created or restored from a Monero seed phrase are now correctly handled as Monero-only accounts.
  The Bitcoin resend screen now shows a proper error message instead of an empty red block.
  Overall stability has also been improved for Tangem-related flows.

- **Fixed issues with incomplete swap status and messaging**
  The `Swap not completed` message is now displayed in full, and the transaction status is detected correctly.

- **Unfinished two-step swaps now appear only in the wallet where they were started**
  The `Unfinished swap` banner and the unfinished swaps list are no longer shown in unrelated wallets.

- **Improved UX on the send screen when the wallet adapter is temporarily unavailable**
  The screen now handles cases more gracefully when the wallet adapter has not yet been restored after returning from the background or reinitializing the app.

- **Fixed duplicated receive amount field on the transaction screen**

- **Fixed a crash when opening the help screen from the info icon**

- **Fixed keyboard behavior when focusing the alternative amount input on the send screen**

- **Unified the provider field name in swap history**

- **Fixed a visual artifact in the confirmation dialog for creating a new Monero address**

- **Restored the swap button for XEC on the asset page**

- **Fixed a false `No internet` error after manually refreshing market widgets**

- **Fixed temporary duplicate outgoing transactions in history**
  Outgoing transactions no longer appear twice for a short time because of `pending transaction` state handling.

- **Removed an extra separator in the Blockchain API request**

## 🚀 Version 0.54.1 Update
_Release date: April 3, 2026_

### ✨ Improvements

- **Temporarily disabled suspicious transaction detection**
  A `false positive` was identified, so this mechanism has been temporarily disabled and will be refined in future updates.

### 🐛 Fixes

- **Fixed WalletConnect connection issue when the app is auto-locked**

## 🚀 Version 0.54.0 Update
_Release date: April 1, 2026_

### ✨ Improvements

- **Added protection against Address Poisoning**
  Suspicious transactions are now marked with a red icon, and their details are blurred.
  The app now classifies transactions as:
  `created in this wallet`, `from address book contacts`, `received from the blockchain`, and `suspicious (scam)`.

- **Added Monero address rotation for improved security**

- **Added the ability to hide all transactions in the transactions section**
  Works similarly to the tap-to-hide balance behavior.

- **Added clear information when network coins are required for token operations**
  If the user does not have enough native network coins to cover fees, the app now shows this explicitly.

### 🐛 Fixes

- **Fixed Jetton swap issue when using Tangem hardware cards**

- **Fixed an issue with selecting the optimal swap provider**
