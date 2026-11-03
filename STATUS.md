# Feel Miner — STATUS (v0.1.0-feel1)

## Source verified
- Official repo: https://github.com/feelcoin-org/feelcoin-miner, public, GPL-3.0.
- Tag v0.1.0 object: `4a83a5c4ad6fbe9037c9140fef2956eeb31deffd` (GitHub tag ref).
- Local shallow clone HEAD at check time: `8409fb34cdd6e978f1e325876ce9786b83f529a2`
  ("Prepare Feelcoin Miner v0.1.0 release documentation", 2026-10-06).
  The CI workflow clones the repo and checks out `v0.1.0` itself, so the
  CI binary is built from the tag, not from this local shallow clone.
  Record the CI run's head SHA from its log before quoting a commit.
- Upstream version header: APP_VERSION 0.1.0, APP_ID feelcoin-miner,
  APP_COPYRIGHT "Copyright (C) 2026 Feelcoin Project; based on XMRig".
- Default config in the upstream source: coin=feelcoin, algo=rx/0,
  url=pool.feelcoin.org:4244, tls=true, donate-level=0. This app uses
  exactly that pool, user, and donate level, with 4242 as a labelled
  plaintext alternative only.

## Verified vs unverified
- VERIFIED from the official README/release: TLS pool 4244 is the
  official default; standard pool 4242; official pool PPLNS, 0.5% fee;
  0% default upstream donation; RandomX rx/0; Linux x86_64 prebuilt
  only (so this Android ARM64 build is a port, not the prebuilt binary).
- VERIFIED from upstream source: coin=feelcoin, algo=rx/0 in
  src/config.json; APP_VERSION 0.1.0.
- UNVERIFIED / NOT CLAIMED: any H/s figure for any device, any
  temperature figure, any accepted-share figure, any payout figure
  (no payout amount is stated in this app), and any VeinPool/Lajkto/
  WAM/Tari endpoint (none are in this app at all).
- WALLET VALIDATION NOTE, IMPORTANT: the official README says
  "--wallet validates CryptoNote Base58 encoding and checksum and
  accepts Feelcoin mainnet prefixes: 84 standard, 85 integrated,
  86 subaddress". This app implements exactly that (Base58 block
  decode + Keccak-256 checksum + varint prefix 84/85/86). However,
  the upstream v0.1.0 WalletAddress tag table read in this run does
  NOT list a FEELCOIN entry, so the miner's own coin() mapping for
  a FEEL tag is not a FEELCOIN coin in that table. This app does not
  depend on that mapping: it configures coin=feelcoin explicitly in
  the JSON config. If the official miner rejects a FEEL address on
  the phone for a tag reason, that will show in the miner log and
  must be reported as a bug, not hidden.

## What was built (this project, separate from pool-presets-7050)
- New directory: ~/workspace/feel-miner-android
- New GitHub repo: ziyadshafeek/feel-miner-android
- New package: io.github.j3r0nim0.feelminer (installs alongside, not
  over, the old Anvil / photo-preset APKs)
- Files: MainActivity (Feelcoin-only first screen), FeelWallet
  (prefix/checksum validation), MiningService (foreground service,
  ProcessBuilder to libfeelminer.so from nativeLibraryDir, JSON
  config, speed/accepted parsing, charging-only / CPU 65/55 C /
  battery 40/37 C pauses as clean stop-and-restart).
- CI: .github/workflows/release.yml — build feelcoin-miner with NDK
  28.2 (libuv + OpenSSL static, WITH_HWLOC=OFF, WITH_OPENCL=OFF,
  WITH_CUDA=OFF, WITH_NVML=OFF, WITH_MSR=OFF, WITH_DMI=OFF, WITH_TLS=ON,
  WITH_ASM=ON, armv8.2-a+crypto+aes+sha2+dotprod+fp16, cortex-a78),
  then assembleRelease. CI has no keystore, so its APK is unsigned;
  signing is a separate local step, only on CI success.

## Local verification actually run
- git diff --check: exit 0.
- Kotlin brace/paren balance: all three files balanced.
- Local Kotlin unit test for FeelWallet: NOT completed. The VM's
  standalone Kotlin compiler classpath (annotations/stdLib) failed
  with NoClassDefFoundError, before any test code ran. This is a VM
  toolchain issue, not a code verdict. CI Gradle compilation is the
  verification route for the Kotlin code.
- Local Gradle: NOT attempted as a build proof. Known VM failure:
  "Could not receive a message from the daemon" before compilation.
- adb devices: empty in prior checks; paired phone is not an adb
  device. No install, no on-device benchmark from here.

## CI / signing — to be filled when the watcher returns
- Branch/tag pushed: main, tag v0.1.0-feel1 (see CI watcher output).
- CI run: (fill in run id, conclusion, binary hash).
- Signed APK: (fill in path, size, SHA-256, signer) or exact CI
  failure if CI failed. Do not install or quote a signed APK before
  that step.
