#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Robolectric tests require Java 21; auto-detect on macOS, keep the caller's JAVA_HOME elsewhere.
if [ -x /usr/libexec/java_home ]; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 21)
fi

ASSET=core/wallet/src/main/assets/initial_coins_list
count() { grep -c "INSERT OR REPLACE INTO $1 " "$ASSET" || true; }

before_blockchains=$(count BlockchainEntity)
before_coins=$(count Coin)
before_tokens=$(count TokenEntity)

./gradlew :core:wallet:testDebugUnitTest \
    --tests "cash.p.terminal.wallet.tools.InitialCoinsListGenerator" \
    -PupdateCoinsList=true --rerun

report() {
    local after delta sign=""
    after=$(count "$1")
    delta=$((after - $2))
    [ "$delta" -ge 0 ] && sign="+"
    echo "$1: $2 → $after ($sign$delta)"
}
report BlockchainEntity "$before_blockchains"
report Coin "$before_coins"
report TokenEntity "$before_tokens"
git diff --stat "$ASSET"
