## Main changes in version 0.47.4
_Release date: October 8, 2025_

### 🛠 Fixes
- **DOGE and LTC sending:**
  - Fixed an issue where the transaction appeared on **Blockchair** but was not included in the blockchain.
- **EVM transactions:**
  - Fixed duplicate transaction display — the received amount is no longer shown twice.
- **Monero:**
  - Fixed an app crash when using **Monero**.



## Main changes in version 0.47.3
_Release date: October 5, 2025_

### ✨ New Features
- **Hardware wallet:**
  - Updated the default coin initialization logic for the hardware wallet.

### 🛠 Fixes
- **TRX-20 Swap:**
  - Fixed an app crash that occurred when granting permission for **TRX-20** token swaps.
- **Token addition:**
  - Removed the required card number field when adding a token for the hardware wallet.



## Main changes in version 0.47.2
_Release date: October 4, 2025_

### ✨ New Features
- **Wallet creation:**
  - Added automatic inclusion of **ZEC (Unified)** and **Monero** coins when creating a new wallet.

### 🛠 Fixes
- **TRX Swap:**
  - Fixed an issue where the **TRX** swap process could hang during the quote determination stage.



## Main changes in version 0.47.1
_Release date: October 3, 2025_

### ✨ New Features
- **Ston.fi (V1/V2):**
  - Added support for working with both **V1** and **V2** liquidity versions.
- **Swap Mode:**
  - Added price change indication with color scheme:
    - 🟢 **Green** — profitable swap.
    - 🔴 **Red** — swap with losses.
    - ⚪ **White** — no loss.

### 🛠 Fixes
- **Ton Connect (UI):**
  - Fixed a visual issue when removing a connection.



## Main changes in version 0.47.0
_Release date: October 1, 2025_

### ✨ New Features
- **Jetton Token Swap:**
  - Added the ability to swap Jetton tokens directly in the app via the **Ston.fi** DEX.
- **Maya Swap Support:**
  - Integration with **Maya Swap** is now available.

### 🛠 Fixes
- **Orphan Blocks:**
  - Fixed an issue with orphan blocks that caused problems when sending coins for Bitcoin forks
    (**BTC / LTC / DASH / PIRATE / COSANTA / DOGE**).
- **Ton Connect (UI):**
  - Fixed missing scroll in the Ton Connect select menu.
  - Fixed a visual bug when removing a connection and adjusted padding in the interface.
- **Premium Mode:**
  - Fixed an issue with displaying the **PIRATE** amount (value didn’t fit on the screen).



## Main changes in version 0.46.8
_Release date: September 19, 2025_

### ✨ New Features
- **Privacy Mode:**
  - When hiding transactions, the address and operation type are now also hidden to prevent identifying a transaction by the first and last characters of the address.
- **Amount Display:**
  - Removed rounding on the transaction confirmation screen — now the exact amount being sent is shown.

### 🛠 Fixes
- **Etherscan API:**
  - Fixed compatibility with the updated transactions API (V2).
- **Stability:**
  - Fixed an issue where the app could crash when trying to open the send transaction screen before synchronization was completed.



## Main changes in version 0.46.7
_Release date: September 17, 2025_

### ✨ New features
- **TonConnect:**
  - You can now connect the app to the [@piratecash](https://t.me/piratecash) mini-game.
  - Wallet connection is supported for [ston.fi](https://ston.fi) and [dedust.io](https://dedust.io) exchanges.
- **TonConnect scanning:**
  Added ability to scan QR codes directly from the main screen.
- **Hardware wallet:**
  TonConnect signing support for hardware wallets.

### 🛠 Fixes
- **TonConnect:**
  Improved crash handling for TonConnect screen and hardware wallet signing.



## Main changes in version 0.46.6
_Release date: September 16, 2025_

### 🛠 Fixes
- **Coins and tokens list:**
  Now displayed in the correct order, as users expect.

- **WalletConnect (Tangem):**
  Connecting a Tangem card no longer requires a backup.

- **After sending a transaction:**
  The app now shows the transaction status and automatically returns to the main screen.

- **Block explorer:**
  Fixed incorrect naming for PirateCash and Cosanta in sync mode.

- **Receiving addresses:**
  All addresses are now displayed, even for non-standard transactions.



## Main changes in version 0.46.5
_Release date: September 13, 2025_

### 🎛 Improvements
- **Send interface:**
  The send screen has been completely redesigned — now **address and amount are on the same screen**, allowing you to better control what and where you send.

- **Localization (RU/UA):**
  Fixed an issue with displaying the contract verification type description in Russian and Ukrainian languages. Added minor visual improvements for these localizations.



## Main changes in version 0.46.4
_Release date: September 6, 2025_

### 🎛 Improvements
- **Wallet warning:**
  Fixed an issue where a null wallet warning was sometimes displayed.

- **Wallet Connect (Reown):**
  Updated the Wallet Connect version and migrated the code to the newer **Reown** library, since Wallet Connect reached End-of-Life on February 17, 2025.

- **Message signing with Tangem:**
  Fixed an issue with **Wallet Connect message signing** in some cases when using a **Tangem card**.

- **DASH synchronization (Hybrid):**
  Fixed an issue where **DASH synchronization** could stop in some cases when using **Hybrid mode**.



## Main changes in version 0.46.3
_Release date: September 3, 2025_

### ✨ New features
- **View incoming transaction addresses (Bitcoin-like chains):**
  Incoming transaction details now display the **address funds were sent to** for the following blockchains: **BTC / LTC / DASH / DOGE / PIRATE / COSA**. This simplifies auditing and tracking deposits.

- **Monero (25-word seed mode): added QR scanner**
  For wallets restored with a 25-word seed, the **main screen** now includes the option to **scan recipient addresses via QR code**. This makes sending faster and eliminates manual entry errors.

### 🎛 Improvements
- **Wallet cloning (copying):**
  When cloning a wallet profile, the **backup status is now inherited**, making the process more convenient and consistent.

### 💎 Premium
- Fixed an issue that prevented **Premium** from being activated in some cases.
- Overall improvements to Premium handling.



## Main changes in version 0.46.2
_Release date: August 29, 2025_

### ✨ New Features
- **Wallet cloning (no clipboard):** 

  Create an additional wallet profile on the same **seed phrase** with a **different password**—without copying the seed to the clipboard. This improves security and speeds up setting up an alternate wallet.

### 🎛 Interface Improvements
- The **Stacking** section now displays your **Premium** status

### 🛠 Important Fixes
- Fixed an app crash on the **Solana** blockchain
- Fixed an app crash on the **Bitcoin** blockchain
- Fixed **Demo Premium** behavior



## Main changes in version 0.46.1
_Release date: August 27, 2025_

### ✨ New Features

- Added a new price change display mechanic:
  - In percentages and in fiat
  - Available intervals: **1 hour, 1 day, 1 week, 1 month, 1 year, and all time**
  - Added support for the **Ukrainian language**

### 🎛 Interface Improvements

- Completely redesigned the token management mode:
  - The interface has become more clear and user-friendly
  - Added new navigation with quick access via the 🔍 icon on the main screen. The old mode is still available if you’re used to it

### 🛠 Important Fixes

- Fixed an issue with activating **Premium** mode
- Fixed a problem with swapping **JETTON** tokens on the **TON** blockchain
- Fixed an app crash when no account was found



## Main changes in version 0.46.0
_Release date: August 20, 2025_

### ✨ New Features

- Introduced the new **Premium** mode
  - Free 30-day trial available for each wallet
  - After the trial ends, Premium access remains without a subscription if a single wallet holds:
    ≥ 10,000 **PIRATE** or
    ≥ 100 **COSA**
  - Tokens remain in your wallet, continue to generate passive income, and unlock access to all Premium features
  - 🔒 All PIRATE tokens in the liquidity pool are locked for 10 years — preventing rug pulls and ensuring fairness

- New Premium features:
  - Outgoing address contract check — get a warning if the destination is a contract, protecting your funds
  - Export of non-standard **Monero** seed phrases into the classic 25-word format

### 🛠 Important Fixes

- Fixed issues with **LTC (BIP-86)** addresses when using the Blockchair API
- Fixed app crash during **POL / Tangem (fdroid)** swaps

### 🔧 Technical Improvements

- **Monero**: the wallet now supports non-standard seed phrases by transparently using a built-in converter into the 25-word format (available without Premium)



## Main changes in version 0.45.4
_Release date: August 17, 2025_

- Fixed an issue with displaying transactions when using **12-word Monero wallets**.
  This bug did not affect 25-word wallets.

## User recommendations

- If you have a 12-word Monero wallet and **your transactions are not visible**, you need to:
  1. Delete the wallet.
  2. Re-import it.
- If you are using a **25-word wallet** or **have never used a 12-word Monero wallet**, no action is required.



## Main changes in version 0.45.3
_Release date: August 13, 2025_

### ✨ New Features

- Added support for **Monero** with 12-word seed phrases

### 🛠 Important Fixes

- Fixed app crash: android.net.ConnectivityManager$TooManyRequestsException
- Fixed issue with **WalletConnect** confirmation
- Fixed cutting of the *staking* word in the description
- Fixed button padding on the send screen

### 🔧 Technical Improvements

- Added filter for unsupported **LTC BIP86** in **Thorchain** (disabled Taproot support for LTC)
- Updated **zcashAndroidSdk** to version 2.3.0



## Main changes in version 0.45.2
_Release date: August 7, 2025_

## 🪙 Fixes for TRON

- 🚀 Fixed an issue with sending TRC-20 tokens and TRX coin.

## 🌙 Interface improvements

- 🌗 Fixed a dark mode display issue when checking addresses
  All interface elements are now readable in both light and dark themes.



## Main changes in version 0.45.1
_Release date: July 29, 2025_

## 🔐 Privacy Improvements

- 🕵️‍♂️ Enhanced anonymity when sending transactions

  Users can now choose whether to check addresses against sanctions and blacklists.

  The app performs no background checks or tracking without explicit user consent.

  We stay focused on security without compromising user privacy.



## Main changes in version 0.45.0
_Release date: July 28, 2025_

# 📝 Changelog

## 🆕 New

- 🛡 **Address Checker (Sanctions, Blacklist)**

  A new section allows you to check any address against sanctions and blacklists on demand. Especially useful when handling large transfers from third parties.

## 🔧 Improvements

- ⚙️ Improved **auto-hide** mechanism — now it works properly with transactions and their details.  
- 📴 Added an option to **disable the changelog popup** after the app update.

## 🐞 Fixes

- 📊 Fixed staking chart display — now it correctly shows **coins** instead of **currency**.  
- 💸 Fixed incorrect **network fee** display during ChangeNow swaps.



## Main changes in version 0.44.2
_Release date: July 26, 2025_

- 🐞 Fixed an issue where, in some cases, the **"Next"** button couldn't be pressed after entering an address.
- ✏️ Corrected a wording in the settings description as reported in issue #56.



## Main changes in version 0.44.1
_Release date: July 24, 2025_

### 💰 **Exchange Interface Improvements**
- The **available balance is now always visible during amount input**, not just before it 🧮

### 🛠 **Stability & Fixes**
- Fixed **crashes that caused the app to close unexpectedly** 📱💥  
- Improved price chart — **currency is now displayed correctly** 📊💸

### 📱 **Monero & Samsung Support**
- Fixed an issue where the **keyboard on Samsung devices** overlapped important information during Monero recovery 🛡️🔑

### 👀 **WatchOnly Addresses**
- Enhanced logic for **WatchOnly addresses** — now more stable and reliable 🔍🔐

### 🔒 **Auto-Hide Balance Logic**
- Fixed cases where **numbers were hidden with asterisks**, even when they should have been shown ⭐➡️123



## Main changes in version 0.44
_Release date: July 22, 2025_


## THORCHAIN SWAP INTEGRATION

Experience seamless cross-chain swaps! Trade BTC ↔ ETH and more directly within your wallet. No KYC required. No centralized exchanges involved. Your privacy stays intact.

## STELLAR BLOCKCHAIN SUPPORT

Complete Stellar blockchain compatibility and access to its full asset ecosystem — all integrated right inside your wallet.

## App Improvements

- 🔐 **Improved auto-hide transaction behavior**: the PIN is now only requested when changing an asset, changing a category, or after the device was locked.
- 🌐 **Translation refinements**: corrected inaccuracies and improved overall localization.
- 🚫 **Option to launch without system PIN**: the app can now be launched without entering the system PIN if the user accepts the associated risks.
- 🧷 **Fixed duress mode configuration issue**: resolved a bug that occurred when setting up duress mode with many wallets (scroll was required).
- 🛠️ **Fixed crashes**: resolved several issues that caused the app to crash in specific situations.



## Main changes in version 0.43
_Release date: July 9, 2025_

### Monero native blockchain

You can now store, send and receive Monero directly on its native blockchain.

### Hardware wallet visibility without module

The hardware wallet option is now visible even if the module is not available.
Users will be warned when NFC is not supported on the device.

### Fixed wallet creation screen issue

Resolved an issue where the wallet creation menu did not appear after changing the system PIN.



## Main changes in version 0.42
_Release date: Jun 20, 2025_


### Tangem hardware wallet

Now with Tangem hardware wallet support — private keys are securely generated and stored directly on the card, never exposed or saved anywhere. There is no seed phrase to write down or store, making it virtually impossible for attackers to steal your assets.

### ChangeNow

Cross-chain exchange is available through our partner ChangeNow, allowing you to swap coins from one blockchain to another at a competitive rate.

### DogeCoin native blockchain

You can now store, send and receive Dogecoin directly on its native blockchain.

### Cosanta native blockchain

Cosanta blockchain is now fully supported within the wallet.

### PirateCash native blockchain

Manage PirateCash natively with improved performance and compatibility.

### zCash Transparent and Unified Address Support

Added support for both Transparent and Unified address formats in zCash.

We've fixed various bugs and made improvements to ensure your wallet works better daily.

Sic Parvis Magna ✌️