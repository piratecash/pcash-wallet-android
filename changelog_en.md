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
