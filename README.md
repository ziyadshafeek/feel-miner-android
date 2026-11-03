# Feel Miner — Feelcoin only, Android ARM64

A minimal Android port of the official Feelcoin Miner v0.1.0
(https://github.com/feelcoin-org/feelcoin-miner, GPL-3.0, based on XMRig
6.26.0, RandomX rx/0). This is a new project, separate from the old
Anvil / photo-preset APKs. It installs alongside them under package
`io.github.j3r0nim0.feelminer`.

## What it is, first screen

- Feelcoin only. No Monero, no VeinPool, no Lajkto, no WAM, no Tari,
  no LAN proxy, no L3 masks, no Vulkan, no trading, no solo daemon.
- One screen: FEEL public Receive address, worker name, threads,
  pool choice, Start/Stop, live H/s, accepted/rejected shares, pool
  status, and a short log.
- Default pool: `pool.feelcoin.org:4244` TLS (official default).
  Alternative: `pool.feelcoin.org:4242` plaintext, labelled as such.
- Official pool fee: PPLNS, 0.5% (verified from the official README).
  No payout figure is claimed in this app.
- Donate: 0%.

## Safety, defaults ON

- Charging-only ON by default.
- CPU pause 65 C, resume 55 C.
- Battery pause 40 C, resume 37 C.
- No root. Pause is a clean stop and restart, not a hitless pause.

## Wallet rule

The user makes a Feelcoin wallet themselves at wallet.feelcoin.org,
writes the recovery seed on paper offline, and never pastes it anywhere.
This app accepts only the public Receive address, validated as
CryptoNote Base58 + Keccak checksum with Feelcoin mainnet prefixes
84 standard, 85 integrated, 86 subaddress (official README).
It rejects seed words, private keys, passwords, and Monero addresses.
No wallet address is hard-coded.

## Attribution

Feelcoin Miner (C) 2026 Feelcoin Project, based on XMRig (C) XMRig /
SChernykh, GPL-3.0-or-later. Upstream copyrights retained. See LICENSE.

## Build

CI only (local Gradle on the developer VM fails with the known daemon
IPC error before compilation). Tag `v*` triggers `.github/workflows/release.yml`:
build the feelcoin-miner binary with the Android NDK (arm64-v8a,
armv8.2-a+crypto, cortex-a78), then build the APK, attest it, and
upload the unsigned APK as a workflow artifact. Signing is done
separately with the existing fork key, only on CI success.

## On-device test still required

No H/s, temperature, or accepted-share figure is claimed for this app
until the user installs a signed APK, pastes their own FEEL public
address, and runs it while charging.
