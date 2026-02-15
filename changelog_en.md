## 🚀 Version 0.51.13 Update
_Release Date: February 15, 2026_

### 🐛 Bug Fixes

- **Fixed SOL synchronization issues**
  Resolved an issue where Solana appeared correctly on the main screen, but became unavailable (greyed out) when opened, preventing transactions from being processed.

- **Fixed crash when opening the coin list**
  The application could crash when attempting to load the coin list on some Xiaomi devices running Android 14.



## 🚀 Version 0.51.12 Update
_Release Date: February 15, 2026_

### 🐛 Bug Fixes

- **Removed redundant connections for Bitcoin / Litecoin when using multiple address types**
    Optimized the number of network connections when working with different BTC and LTC address types, reducing resource usage and network traffic when multiple address types are present.

- **Fixed delayed TRX20 token balance update on the main screen**
  Resolved an issue where the TRX20 token balance was not updated immediately and appeared with a delay on the main screen.

- **Fixed incorrect asset price changes when switching chart time periods**
  The asset price is now consistent and no longer depends on the selected chart time interval.

- **Fixed an issue where the “Next” button was inactive when sending coins**
  In rare cases, the “Next” button did not become active after entering the required transaction details. This behavior has been fixed.

- **Fixed adding COSA to the favorites list**
  Resolved an issue where the COSA token did not appear in the favorites list after being added.

- **Fixed removing wDASH from the favorites list**
  The wDASH token can now be removed from the favorites list correctly.

- **Fixed balance validation during swap execution**
  A swap can no longer be initiated when there are insufficient tokens.

- **Fixed crash when saving an account after Keystore authentication expires**
  The application could crash with a UserNotAuthenticatedException when attempting to save or update an account in the database after Keystore authentication had expired. This issue occurred rarely and only on certain devices.

- **Fixed SQLiteDatabaseCorruptException crash in Zcash SDK**
  The application could crash due to a SQLiteDatabaseCorruptException while collecting data from the internal Zcash SDK database. The crash occurred in the queryAndMap method during synchronizer operations.

- **Fixed crash when navigating to Hidden Wallet Terms screen after Premium purchase**
  The application could crash with an IllegalStateException when navigating to the hidden_wallet_terms_page after returning from the Premium purchase screen.

- **Fixed application crash when using Market widgets**
  The application could crash with an IllegalStateException when using Market widgets on the main screen.

- **Fixed crash when saving wallet balances: FOREIGN KEY constraint failed**
  The application could crash in a background thread while saving wallet balance data into cache due to a foreign key constraint failure in the database.



## 🚀 Version 0.51.11 Update
_Release date: February 7, 2026_

### 🔧 Fixes and Stability Improvements

- **Fixed incorrect message when sending USDT on the TRON network**
  Resolved an issue where *"Preview mode"* was shown instead of a proper error message when attempting to send USDT.

- **Fixed balance check logic for swaps on the TRX network**
  Swaps are now correctly blocked when there are insufficient tokens available for sending on the TRON network.

- **Fixed empty token selection screen during swaps**
  Resolved a race condition that could occasionally cause the token selection screen to appear empty during a swap.

- **Fixed crash when generating a ZCash receive address**
  Resolved an application crash related to GetAddressException when requesting a ZCash receive address.

- **Fixed incorrect investment chart display**
  Restored correct data rendering and visualization for the investment chart.

- **Fixed premium wallet balance transfer for PIRATE / COSA**
  Resolved an issue where premium wallet balances for PIRATE and COSA were not passed to the miniApp during the first wallet connection.



## 🚀 Version 0.51.10 Update
_Release date: February 4, 2026_

### 🔧 Fixes and stability improvements

- **Fixed transaction details disappearing**
  Resolved an issue where detailed transaction information (swaps via PancakeSwap, ChangeNow, and other services) was displayed correctly when opening the screen but was replaced with a minimal view after 5–15 seconds.

- **Fixed crash when navigating to the Receive screen**
  Fixed an application crash when navigating to the Receive screen (ReceiveFragment) with a missing or invalid navigation input parameter.

- **Fixed crash when TON balance is insufficient to pay the fee**
  The app no longer crashes when attempting to send TON with an insufficient balance to cover the network fee. An appropriate error message is now shown instead.

- **Fixed Binance Bridged USDT name display**
  Improved the display of the virtual asset name Binance Bridged USDT (BNB Smart Chain) to avoid an overly long and inconvenient string in the UI.

- **Fixed crash on the seed phrase confirmation screen**
  Resolved an issue where the app could crash when rapidly double-tapping a word on the backup seed phrase confirmation screen (BackupConfirmKeyFragment).

- **Improved Quickex exchange status handling**
  Optimized exchange status update logic to ensure the correct status is displayed in all scenarios.

- **Fixed crash when opening the account management screen**
  Fixed incorrect exception handling when navigating to the ManageAccount screen. The app no longer crashes when the input parameter is missing and now correctly handles IllegalArgumentException.



## 🚀 Version 0.51.9 Update
_Release date: February 1, 2026_

### 🔧 Fixes and Stability Improvements

- **Fixed issues with USDT and TON swaps via Quickex**
  Resolved a problem where USDT (Jetton) and TON swaps through the Quickex provider could work incorrectly or fail.

- **Fixed auto-hide balances behavior**
  Resolved an issue where, when auto-hide balances was enabled, transactions were sometimes not displayed.

- **Improved MiniApp connection mechanism for SWAP 5**
  Enhanced the application’s MiniApp connection logic, improving the stability and reliability of SWAP 5.



## 🚀 Version 0.51.8 Update
_Release date: January 31, 2026_

### ✨ Improvements

- **More swap details**
  Transaction details for swaps now show additional information, including which coins were used and which provider executed the swap. This helps you better understand how the swap was completed.

### 🔧 Fixes and stability improvements

- **Improved input handling when speeding up or canceling transactions**
  Fixed an issue where navigation between screens could occasionally behave incorrectly in these scenarios.

- **Fixed an issue with USDT**
  Resolved a token synchronization problem that could cause USDT (Tether) to be missing from the coin list.

- **Fixed token selection for swaps on slow devices**
  Resolved an issue where selecting a token for a swap could take too long or not respond properly on slower or heavily loaded devices.



## 🚀 Version 0.51.7 Update
_Release date: January 30, 2026_

### 🔧 Fixes and Improvements

- **Fixed guaranteed receive balance display when activating Premium**
  Resolved an issue where the guaranteed receive balance could be displayed incorrectly after enabling the Premium subscription.

- **Improved request versioning for Mini App connectivity**
  Enhanced the request versioning mechanism used to determine the minimum supported app version for Mini App connections.

- **Improved auto-scrolling behavior during captcha input**
  Fixed issues with screen auto-scrolling that occurred while entering captcha on certain devices.

- **Fixed TON wallet issues**
  Resolved problems that caused incorrect behavior of the TON wallet.



## 🚀 Version 0.51.6 Update
_Release date: January 30, 2026_

### ✨ New Features

- **Added virtual USDT (BEP-20) asset**
  A virtual USDT (BEP-20) asset has been created that references BSC-USD to improve UX and make it easier for users to find it in the USDT section.

- **Added information source for diagnostics**
  Display of the data source from which the information was obtained has been added to simplify diagnostics when users report issues.

### 🔧 Fixes and Improvements

- **Fixed miniapp registration when app password is enabled**
  Resolved an issue where miniapp registration failed in the p.cash app when an app-level password was enabled.

- **Fixed information display when swap fails on DEX side**
  Corrected information display in cases where the swap did not complete on the DEX side.

- **Fixed Pirate Jetton address retrieval**
  Resolved an issue with obtaining the Pirate Jetton address that could prevent miniapp registration.

- **Fixed rare crash on back navigation with restored fragments**
  Resolved a rare crash that could occur when navigating back on restored fragments.



## 🚀 Version 0.51.5 Update
_Release date: January 28, 2026_

### 🔧 Fixes and Improvements

- **Redesigned asset display logic during swap**
  Fixed an issue where asset prices were not displayed and assets could appear at the bottom of the list during swap operations.

- **Fixed Solana constant updating transactions message**
  Resolved an issue causing the transactions message to update continuously on the Solana network.

- **Added error handling for backup file manager absence**
  Implemented proper error handling when the backup file manager is not available.

- **Added support for queued transaction notifications in Bitcoin sending flow**
  Added support for notifications for Bitcoin transactions that are queued for sending.

- **Fixed continuous syncing after swap with Tangem card**
  Fixed an issue where the app remained in a continuous syncing state after a swap when using a Tangem card.

- **Fixed swap provider for BEP-20**
  Fixed an issue where, in some BEP-20 transactions, a provider address (e.g. PancakeSwap) was shown instead of the actual swap provider.



## 🚀 Version 0.51.4 Update
_Release date: January 26, 2026_

### 🔧 Fixes and Improvements

- **Fixed Tangem card connection issue with @piratecash_bot**
  Resolved issues that prevented Tangem cards from connecting to the Telegram mini-app game.

- **Improved handling of BNB data fetch failures**
  Improved UI behavior for cases when BNB data cannot be retrieved due to country-based IP restrictions. In such cases, the app correctly suggests using a VPN.

- **Added error handling for SQLCipher loading failures**
  Implemented proper handling for SQLCipher loading errors and added a dedicated error screen to clearly inform the user.



## 🚀 Version 0.51.3 Update
_Release date: January 25, 2026_

### ✨ New Features

- **Added P.CASH wallet connection mode to the mini-app @piratecash_bot**
  Implemented the ability to connect the P.CASH wallet to the Telegram mini-app to participate in **SWAP 5**, simplifying interaction with the ecosystem.

### 🔧 Fixes and Improvements

- **Fixed Polygon price fetching issue**
  Resolved an issue with incorrect fetching and display of the Polygon price.

- **Fixed crash when importing a wallet from Google Drive**
  Fixed an application crash that occurred when attempting to import a wallet from a Google Drive backup.



## 🚀 Version 0.51.2 Update
_Release date: January 22, 2026_

### 🔧 Fixes and Improvements

- **Fixed an issue with Polygon (charts and swaps)**
  Resolved a problem where charts for the Polygon coin were not displayed correctly. Additional diagnostic information was added to help investigate swap issues on some devices.

- **Fixed ChangeNOW transaction display**
  Restored correct display of swap transactions via ChangeNOW in the history section.

- **Fixed application version copyright**
  Updated and corrected the copyright information shown in the app version details.

- **Updated Zcash SDK to version 2.4.4**
  The Zcash SDK has been updated to improve compatibility, stability, and security.

- **Added build and download source information**
  Added details about the app build and the download source to improve issue diagnostics.

- **Removed non-working Cosanta tool from the Analytics section**
  The Cosanta tool, which was not functioning properly, has been removed from the Analytics section.



## 🚀 Version 0.51.1 Update
_Release date: January 16, 2026_

### 🔧 Fixes & Stability Improvements

- **Fixed an issue preventing Tangem hardware wallets from being added**
  Resolved a problem where, in some cases, Tangem hardware wallets could not be added to the app.

- **Fixed missing RSI indicator values**
  Addressed an issue where the RSI indicator could display no value or fail to calculate correctly.

- **Fixed approve transactions on the BNB network**
  Resolved issues with `approve` transactions on the Binance Smart Chain, improving contract interaction reliability.

- **Refactored token balance navigation**
  Refactored token balance navigation to fix application crashes on some devices and improve overall stability.



## 🚀 Version 0.51.0 Update
_Release date: January 14, 2026_

### ✨ New Features

- **Premium: Authorization & Security Event Logging**
  A new advanced logging system has been added to store data about failed login attempts, *Duress Mode* logins, and other security-related events. This provides greater transparency and control over wallet access.

- **Premium: Incoming Transaction Sanctions & Risk Check**
  Incoming transactions are now checked against sanctions lists, blacklists, and “dirty funds” indicators to improve compliance and user safety.

- **Updated App Icons**
  The app now includes a refreshed set of icons. You can use not only the default p.cash icons, but also logos of your favorite apps to personalize the interface.

- **QR Scanner Type Indicator**
  The type of QR scanner in use is now displayed, making scanning more transparent and easier to debug.

- **Real-time Balance Updates**
  Your balance now updates instantly without waiting for transaction confirmation, providing a true real-time view of your funds.

- **Automatic Leading Zero for Decimals**
  Improved number handling: a `0` is now automatically added before decimal points to ensure correct formatting.

### 🧭 Improvements

- **More Informative 403 Errors with VPN Suggestion**
  403 error messages now indicate possible regional blocking and suggest using a VPN to restore access.

- **Smart Lock Disable Recommendation**
  Error messages now include a recommendation to disable *Smart Lock*, as some recovery methods may reduce security (for example, reset on fingerprint addition).

- **Improved Hidden Value Inheritance**
  Stability and predictability of hidden wallet and hidden parameter inheritance has been improved.

- **Correct Localization of Number Formatting on the Main Screen**
  Synchronization values now use the correct decimal separator (dot or comma) based on the selected language.

### 🔧 Stability & Bug Fixes

- **Fixed SWAP errors when there were not enough tokens for gas fees**
  Resolved an issue where swaps could fail due to insufficient funds to pay transaction fees.

- **Fixed Zcash restore by date**
  An issue that caused Zcash wallet recovery by date to behave incorrectly has been fixed.

- **Fixed PirateCash and Cosanta balance display**
  Balances for PirateCash and Cosanta are now shown correctly.

- **Fixed “Token not supported” error during swap**
  Eliminated cases where a supported token was incorrectly reported as unsupported during a SWAP.

- **Fixed Ton Connect connection to https://getgems.io**
  Restored proper Ton Connect functionality when interacting with GetGems.

- **Fixed endless BNB synchronization and swap creation issue**
  Resolved a bug where BNB could get stuck in continuous synchronization and prevent swap creation.



## 🚀 Version 0.50.4 Update
_Release date: December 23, 2025_

### ✨ New Features

- **Automatic scrolling for QR code display**
  Added automatic scrolling to ensure the entire QR code is visible even when it does not fit on the screen.

### 🧭 Improvements

- **Improved BSC transaction fetching**
  Optimized the mechanism for obtaining transactions on the Binance Smart Chain, improving speed and reliability.

- **Fixed QR code issue in dark theme**
  QR codes are now displayed correctly in dark mode without losing contrast or scannability.

### 🔧 Stability Fixes

- **Set default Monero node when auto-selection fails**
  If Monero node auto-selection fails, the app now sets a default node via `MoneroKitManager`, preventing errors and improving wallet stability.



## 🚀 Version 0.50.3 Update
_Release date: December 22, 2025_

### ✨ New Features

- **Seed phrase transfer via QR code**
  Added the ability to transfer a seed phrase using a QR code to avoid potential leaks during copy-paste and significantly improve the security of transferring seed phrases between devices.

- **Declared vs actual exchange rate visibility**
  When swapping via **ChangeNOW** or **Quickex**, CEX providers declare an exchange rate that may differ from the final executed rate.
  The app now displays:
    - the declared rate history;
    - the actual executed rate;
    - the price deviation between declared and actual rates.

### 🧭 UX Improvements

- **Correct exchange ticker display before transaction confirmation**
  Fixed an issue where, before transaction confirmation, the blockchain ticker was shown instead of the actual token ticker.

- **Fixed profit percentage color on the swap screen**
  Resolved an issue where the profit percentage color was displayed incorrectly on the exchange screen.

### 🔧 Fixes and Performance Improvements

- **Fixed active exchange provider selection**
  Resolved an issue where the currently active exchange provider was occasionally not selected.

- **Fixed crash when generating developer reports larger than 2 MB**
  The app no longer crashes when creating developer reports with large data volumes.

- **Accelerated transaction parsing for BSC**
  Implemented a mechanism to speed up transaction parsing on **Binance Smart Chain (BSC)**, improving performance and responsiveness.

- **Fixed nullability issues in Monero node selection logic**
  Improved stability of Monero node selection by properly handling nullable states.

- **Fixed widget item crash on some devices**
  Resolved an issue where the app could crash on certain devices when interacting with widget items.

- **Handled missing intent extras in KeyStoreActivity**
  When required intent extras are missing, the app now redirects the user to **MainModule** instead of crashing.



## 🚀 Version 0.50.2 Update
_Release date: December 16, 2025_

### ✨ New Features

- **Duress Backup (Premium Feature)**
  Introduced a backup mechanism with encryption and plausible deniability. From the backup file itself, it is impossible to determine:
    - how many wallets it contains;
    - whether it includes hidden data.
      The same backup file is decrypted differently depending on the password:
    - the **duress password** unlocks only duress wallets;
    - the **main password** unlocks all wallets.

- **App Cache Cleanup**
  Added the ability to clear:
    - network cache;
    - image cache;
    - database cache;
    - market price cache.
      Path: **Settings → About → App status → App cache**.

### 🧭 UX Improvements

- **Added loading state for the transactions screen**
  Previously, users could see a “no transactions” message while data was still loading due to limited device resources.
  Now:
    - the “No transactions” message is shown only when there are truly no transactions;
    - a loading indicator or skeleton is displayed during data loading to prevent unnecessary concern.

### 🔧 Fixes and Improvements

- **Improved ticker mapping for Quickex**
  Fixed an issue where **Bitcoin (BTC)** could not be found.

- **Fixed wallet restoration after deletion**
  Resolved a rare issue where deleted wallets could reappear if there were unfinished synchronization operations.



## 🛠 Version 0.50.1 Update
_Release date: December 13, 2025_

### 🐞 Fixes and stability improvements

- **Fixed missing exchange options for native coins in Quickex**
  Previously, for native coins (including **ZEC**), the **Quickex.io** provider could return no available exchange options. This issue has been resolved.

- **Fixed crash when volume data is missing**
  The app no longer crashes in cases where trading volume data is unavailable or equals zero.

- **Fixed a rare crash in the Bitcoin Kit**
  Improved stability of the Bitcoin wallet in rare edge-case scenarios.



## 🚀 Version 0.50.0 — Major Updates
_Release date: December 12, 2025_

### 🏴‍☠️ Key Features

- **Support for branded Tangem cards**
  Added full support for **Semiconductor Smart Card Tangem x P Cash**, enabling seamless use of branded Tangem hardware cards.

- **Quickex.io CEX integration**
  Integrated **Quickex.io** as a new centralized exchange provider for currency swaps, expanding provider options and improving swap reliability.

### 🔄 Swaps & Fees

- **Fixed BTC amount reduction during ChangeNow swaps**
  Resolved a rare issue where the final BTC amount could be lower than expected when exchanging via ChangeNow.

- **Fixed incorrect Jetton transfer fee calculation**
  Jetton network fees are now calculated correctly and match actual network costs.

- **Improved swap provider comparison details**
  The swap provider selection screen now shows more data:
    - current exchange rate
    - comparison with the best available offer
    - how much worse or better the selected provider is versus the best one

### 🔐 Security & UX

- **Additional protection layer during wallet creation**
  Improved wallet creation flow when users are setting a password for the seed phrase, reducing confusion and preventing unintended actions.

- **Fixed UI issues with large system font sizes**
  Resolved an issue where control elements (including wallet deletion) were not visible when large font sizes were enabled.

- **Enhanced QR code scanning mechanism**
  Improved scanning logic and animations, allowing the app to correctly read partially damaged or non-standard QR codes.

### 🛠 Stability & Fixes

- **Fixed crash in `WalletManager.getActiveWallets`**
  Eliminated an application crash when retrieving active wallets.

- **Fixed crash in `SQLiteConnection.nativeOpen`**
  Improved stability of local database operations.



## 🚀 Main Changes in Version 0.49.4
_Release Date: December 2, 2025_

### 🛠 Fixes & Improvements

- **Fixed copy button layout**
  The Copy button now fits correctly in the menu for EVM addresses and Zcash Unified Full Viewing Key.

- **Added Zcash address types**
  In the Receive mode, the app now shows address types:
  **Transparent / Unified / Shielded**, depending on the selected network.

- **Fixed minor price change update issue**
  Corrected a bug where asset prices were not updated if the change was too small.

- **Fixed WalletManager Monero crash on Pixel 6 Pro**
  Resolved a crash affecting some Pixel 6 Pro devices when using the Monero wallet.

- **Fixed QR scan crash on the Swap screen**
  QR scanner now works reliably without causing a crash.

- **Fixed Monero fee crash on some devices**
  Improved Monero fee handling to prevent app crashes.

- **Improved WalletKit initialization**
  Enhanced initialization logic, preventing crashes related to WalletKit on certain devices.



## 🚀 Main Changes in Version 0.49.3
_Release Date: December 1, 2025_

### 🛠 Fixes & Improvements

- **Correct display of outgoing ZEC and TON transactions**
  Fixed an issue where outgoing **ZEC / TON** transactions might not appear in the main transaction list.

- **New BNB token synchronization mechanism**
  Completely rewritten the synchronization logic for **BNB Smart Chain** tokens, as **Etherscan no longer supports the old API method**.
  The app now uses a new stable mechanism compatible with current infrastructure.

- **Reduced SQLiteConnection.nativeOpen crashes**
  Added a protective mechanism and optimizations that significantly reduce the likelihood of `SQLiteConnection.nativeOpen` crashes on certain devices (especially older or low-memory ones).

- **Fixed NavigationExtensionKt.slideFromBottom crash**
  Resolved a bug that could cause the app to crash when opening screens using the `slideFromBottom` animation.



## 🚀 Key Changes in Version 0.49.2
_Release date: November 22, 2025_

### 🛠 Fixes and Improvements
- **ChangeNOW swap transaction display**
    - Fixed an issue where, in some cases, only the outgoing transaction was shown instead of the actual swap request when using **ChangeNOW**.
    - The swap request and its status are now displayed correctly.



## 🚀 Main changes in version 0.49.0-1
_Release date: November 20, 2025_

### ✨ New features
- **Alpha AML integration**
    - Added support for the **alpha-aml.com** service — a tool for checking cryptocurrency wallets and transactions.
      Alpha AML helps users:
      - assess the risk level of interacting with an address;
      - identify wallets from blacklists, sanction lists, and scam reports;
      - avoid receiving potentially “dirty” cryptocurrency.

### 🛠 Fixes and improvements
- **Smart contract address validation**
    - Fixed an issue related to smart contract address detection.

- **Sending funds to Smart Contracts**
    - Fixed and improved the logic for sending funds to smart contracts:
      - resolved an issue where the RPC did not return an accurate transaction cost;
      - added a fallback fee estimation mechanism using a dummy address `0x0000000000000000000000000000000000000000`;
      - added a user warning:
      _“Failed to accurately calculate the network fee. We applied an approximate value, but the transaction may fail or the fee may be higher than expected.”_

- **BalanceAdapterRepository**
    - Fixed an app crash caused by a **concurrency exception** in `BalanceAdapterRepository`.



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
      - higher detail level,
      - improved error correction,
      - fixes for devices that previously scanned slowly or failed to detect the QR code.

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