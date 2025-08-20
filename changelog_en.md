## Version 0.46.0 Highlights

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



## Version 0.45.4 Highlights

- Fixed an issue with displaying transactions when using **12-word Monero wallets**.
  This bug did not affect 25-word wallets.

## User recommendations

- If you have a 12-word Monero wallet and **your transactions are not visible**, you need to:
  1. Delete the wallet.
  2. Re-import it.
- If you are using a **25-word wallet** or **have never used a 12-word Monero wallet**, no action is required.



## Version 0.45.3 Highlights

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



## Version 0.45.2 Highlights

## 🪙 Fixes for TRON

- 🚀 Fixed an issue with sending TRC-20 tokens and TRX coin.

## 🌙 Interface improvements

- 🌗 Fixed a dark mode display issue when checking addresses
  All interface elements are now readable in both light and dark themes.



## Version 0.45.1 Highlights

## 🔐 Privacy Improvements

- 🕵️‍♂️ Enhanced anonymity when sending transactions

  Users can now choose whether to check addresses against sanctions and blacklists.

  The app performs no background checks or tracking without explicit user consent.

  We stay focused on security without compromising user privacy.



## Version 0.45.0 Highlights

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



## Version 0.44.2 Highlights

- 🐞 Fixed an issue where, in some cases, the **"Next"** button couldn't be pressed after entering an address.
- ✏️ Corrected a wording in the settings description as reported in issue #56.


## Version 0.44.1 Highlights



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



## Version 0.44 Highlights


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



## Version 0.43 Highlights


### Monero native blockchain

You can now store, send and receive Monero directly on its native blockchain.

### Hardware wallet visibility without module

The hardware wallet option is now visible even if the module is not available.
Users will be warned when NFC is not supported on the device.

### Fixed wallet creation screen issue

Resolved an issue where the wallet creation menu did not appear after changing the system PIN.



## Version 0.42 Highlights


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