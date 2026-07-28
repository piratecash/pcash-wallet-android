## 🚀 Version 0.58.2 Update
_Release date: July 28, 2026_

### ✨ Improvements

- **Updated the Zcash SDK to support the Ironwood network upgrade**
  The wallet is now ready for the latest Zcash network changes, including support for the new shielded pool and improvements to transaction protection and verification.

## 🚀 Version 0.58.1 Update
_Release date: July 25, 2026_

### ✨ Improvements

- **Added support for encrypted P2P connections with BIP324 v2**
  Supported for `PirateCash`, `Cosanta`, `Dash`, and `Bitcoin`.

- **Added Unstoppable DEX integration**

- **Added in-app updates**

- **Added support for configuring wallet creation date / Restore Height for Zcash and Monero**
  These parameters can now be adjusted after wallet initialization as well.

- **Added WalletConnect connection status display**

- **Wallets with active Premium are now highlighted in a separate `Premium Active` section**

- **Improved hiding of the available fee balance when sending tokens**

- **Added automatic scrolling to the transaction search field on the asset page**

### 🐛 Fixes

- **Swap transactions no longer appear under the `Received` and `Sent` tabs**

- **Fixed a PayCore issue during first wallet registration**

- **Fixed a Tangem issue on the first BEP-20 send attempt**
  Android no longer opens the card's `NDEF URL` instead of starting NFC signing.

- **Fixed the swap provider types info modal overlapping the system header**

## 🚀 Version 0.58.0 Update
_Release date: July 22, 2026_

### ✨ Improvements

- **The Trezor hardware wallet now works through a direct USB connection**
  A deep link to the Trezor app is no longer required.

- **Added a new swap provider: PayCore**

- **Added transaction search**

- **Added support for creating offline transactions**
  If the user has internet connectivity issues, a transaction can be prepared on one device and transferred to another device by any convenient method, then broadcast to the network there.
  For example, this can be done through Meshtastic.

- **Added provider types in swap flow**
  This helps users better understand how a swap is being executed.

- **Improved the asset display mode**
  The interface now makes it clearer when an asset has not finished syncing yet.

- **Added the ability to enable transaction sorting**

- **Unified the background style of modal windows**

- **Improved app cold-start speed**

### 🐛 Fixes

- **Improved automatic restart behavior for ZEC Kit when it stops**

- **Fixed a WalletConnect issue when using Tangem cards**

- **Optimized Monero transaction block storage handling**

- **Fixed a crash when returning to the send confirmation screen after the app was evicted from memory**
  This could happen if the app was sent to the background and the system removed it from memory before the user returned.

- **Fixed stability and performance issues across the app**
