## 🚀 Main changes in version 0.48.9
_Release date: November 19, 2025_

### ✨ New features
- **Premium: Read QR code from file**
    - Added a new premium feature — the ability to **read a QR code directly from an image/file**.
      Useful when paying on websites where a QR code is shown but cannot be scanned with the camera.
- **Zcash: Enhanced privacy for CEX swaps**
    - Implemented the use of **temporary refund addresses** to increase anonymity when interacting with CEX during Zcash swaps.
- **Zcash: Pending transactions**
    - After sending a transaction, a **local pending entry** is now created automatically for all t/u/z modes.
- **QR codes: Improved generation**
    - Updated the QR code generation algorithm:
      • higher detail level,
      • improved error correction,
      • fixes for devices that previously scanned slowly or failed to detect the QR code.

### 🛠 Fixes and improvements
- **EVM links:**
    - Links to transactions now point **directly to the appropriate blockchain explorer** instead of redirecting through etherscan.io, reducing user confusion.
- **mempool.merkle.io:**
    - Fixed an issue that sometimes showed a false “unable to connect” error when sending tokens.
- **Crash fixes:**
    - Fixed crashes occurring when opening:
        - `SwapConfirmFragmentKt.SwapConfirmScreen`
        - `SendEvmModule$Factory.<init>`
        - `SendTonModule$Factory.<init>`
        - `SwapSelectProviderViewModel.<init>`
        - `NavigationExtensionKt.slideFromRight`
        - `SendBitcoinModule$Factory.<init>`
    - Fixed critical error **Fatal Exception: java.lang.OutOfMemoryError**.
- **DASH:**
    - Fixed network interaction issues and improved the sending mechanism.
- **Zcash SDK:**
    - Updated **Zcash SDK to version 2.4.0**.



## 🚀 Main changes in version 0.48.8
_Release date: November 11, 2025_

### ✨ New features
- **InstantSend:**
  - Added support for **InstantSend** for **DASH / PirateCash / Cosanta**, enabling faster confirmations and improving overall transaction speed.

### 🛠 Fixes and improvements
- **Donation addresses:**
  - Updated **Donate** addresses. The project continues to grow thanks to community support — we appreciate every contribution.
- **EVM fee ticker:**
  - Fixed an issue with the **fee ticker display** for EVM-based blockchains.
- **Ston.fi:**
  - Fixed a problem with buying assets on the **Ston.fi DEX**, where price quotes were not always detected correctly.



## 🚀 Main changes in version 0.48.7
_Release date: November 7, 2025_

### ✨ New features
- **EVM transactions:**
  - Added **feeCoin handling** and improved **caution indicators** when preparing EVM-based transactions, making fee logic clearer and safer for users.

### 🛠 Fixes and improvements
- **Swap button:**
  - Fixed an issue where the **swap button enable state** behaved incorrectly in certain cases.



## 🚀 Main changes in version 0.48.6
_Release date: November 6, 2025_

### ✨ New features
- **Market synchronization:**
  - Improved the **market data synchronization mechanism**, providing more stable and faster updates of price and market information.

### 🛠 Fixes and improvements
- **Monero:**
  - Improved memory handling and added extended diagnostic logs.
    Also fixed a potential issue that could lead to a **memory allocation error (std::bad_alloc)**.
- **Keystore (HONOR):**
  - Fixed a potential compatibility issue with **Android Keystore** on **HONOR** devices.



## 🚀 Main changes in version 0.48.5
_Release date: November 5, 2025_

### ✨ New features
- **Token cache on first launch**
  - Added a local **token list cache** that is used if the app fails to load the token list from the network
    during the first launch.
    This improves startup stability and prevents issues with an empty token list.



## 🚀 Main changes in version 0.48.4
_Release date: November 4, 2025_

### ✨ New features
- **Bitcoin-like networks:**
  - You can now **use your funds right after the first confirmation**,
    without waiting for the full number of confirmations — making BTC, LTC, and similar networks faster to use.

### 🛠 Fixes and improvements
- **Tangem:**
  - Fixed an issue with **sending coins from Tangem wallets** when multiple signatures (different addresses) were used.
    Such transactions now process correctly.
- **Main screen:**
  - Improved the **balance hiding mechanism** — now animations and behavior are smoother and more consistent.



## 🚀 Main changes in version 0.48.3
_Release date: November 1, 2025_

### ✨ New features
- **Premium: Read QR code from file**
  - Added a new feature — **scan QR codes directly from an image or file**.
    Convenient when you have a saved screenshot or received the code as a picture.
- **Extended Monero logging:**
  - Introduced an **extended logging mode** for the Monero wallet to help developers quickly assist users experiencing synchronization or balance issues.

### 🛠 Fixes and improvements
- **Monero:**
  - Fixed an issue that occurred when a **wallet creation date was incorrect** — the system now handles such cases properly.
- **ChangeNOW (TON Jetton):**
  - Fixed a **bug during Jetton token swaps** on the TON blockchain via ChangeNOW.
- **PIN authorization:**
  - Fixed an issue that caused **slow app authorization when entering a PIN code**.



## 🚀 Main changes in version 0.48.2
_Release date: October 26, 2025_

### ✨ New features
- **Premium: Delete all wallets**
  - Added a new feature — **delete all wallets using a secret PIN code**.
    Instantly wipe the app in an emergency to protect your privacy.
- **Exchange rates refresh:**
  - Pulling down on the main screen now **forces a real-time update of coin prices**.

### 🛠 Fixes and improvements
- **Premium mode:**
  - Improved **premium status verification**, increasing stability and reliability of feature activation.



## 🚀 Main changes in version 0.48.1
_Release date: October 24, 2025_

### ✨ New features
- **Premium: Hidden Wallet**
  - Added a new **“Hidden Wallet”** feature that allows you to create separate wallets protected by **individual PIN codes**.
    Perfect for keeping private funds or separating personal and business wallets.
- **Monero:**
  - Improved the **Monero wallet synchronization mechanism**, enhancing stability and balance accuracy.

### 🛠 Fixes and improvements
- **Changelog (Dark Theme):**
  - Fixed an issue with **Changelog text visibility in dark mode**.
- **Log rotation:**
  - Enhanced **application log rotation** — old data is now automatically deleted,
    **never leaves your device**, but you can safely share logs with developers if you want to help fix issues.



## 🚀 Main changes in version 0.48.0
_Release date: October 22, 2025_

### ✨ New features
- **Number display settings:**
  - Added an option to **disable automatic number rounding** on the main screen.
    You can now see exact values without abbreviations (K, M, etc.).
- **Transaction history:**
  - The transaction list now shows the **exact time** of each transaction.
- **Number formatting (RU / UA):**
  - For Russian and Ukrainian interfaces, numbers are now always displayed with:
    - a **space** as a thousands separator;
    - a **dot** as a decimal separator, regardless of the system locale.
- **Contract send check (Premium):**
  - Improved validation logic — **specific wallet contracts** (e.g., TrustWallet and MetaMask) will **no longer trigger a warning** when sending funds.
- **Token synchronization:**
  - Enhanced the behavior of manually added tokens — if such a token appears in our database, it will be **automatically synchronized** with price monitoring and display its icon.

### 🎨 Interface
- **App icon:**
  - Updated the **application icon** to match the new **p.cash** branding.
- **Changelog formatting:**
  - Improved **text formatting** in the Changelog section for better readability and consistency.



## Main changes in version 0.47.6
_Release date: October 14, 2025_

### ✨ New Features
- **Coin Sending:**
  - Added the ability to **paste the amount** directly from the clipboard when sending coins.
- **MEV Protection:**
  - The app now **remembers the last selected MEV protection state** and automatically restores it on the next launch.
- **Hardware Card:**
  - Improved interaction with the hardware card when adding tokens — now an **error dialog is shown if the token cannot be added**.

### 🛠 Fixes
- **Hardware Card:**
  - Fixed an issue where the token **was not added in swap mode**.
- **ZEC Swap:**
  - Fixed a potential **NullPointerException** when retrieving the **ZEC refund address**.



## Main changes in version 0.47.5
_Release date: October 11, 2025_

### ✨ New Features
- **MEV Protection:**
  - DEX swaps now come with **MEV protection** — no more front-running bots eating your profits.
- **ZEC Swap:**
  - Added a warning when swapping **ZEC**, since a **t-address** is used for potential refunds, which may partially de-anonymize the user.
- **ZEC Restore:**
  - You can now specify the **wallet birth date** as a calendar date instead of only using the block height.

### 🛠 Fixes
- **Ton Connect:**
  - Removed wallets that don’t support Ton Connect (e.g., **Monero** with 25-word seed).
- **Monero:**
  - Fixed an issue where wallet data could become corrupted — added an **automatic recovery mechanism**.



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