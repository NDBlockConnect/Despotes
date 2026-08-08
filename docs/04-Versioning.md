# Despotes Versioning

> Doc 4 / 4 | Despotes Docs > Version: v26.0

Despotes follows the Aprism version scheme (Aprism docs §12.1), adapted for a mod:

## 1. Scheme

```
v<Year>.<minor>[-Alpha.<n>][-<Loader>-<MCVer>]
```

- A major line corresponds to a calendar year: `v26` = 2026, minors `v26.0` … `v26.9`.
- Within a minor: `Alpha.1` … `Alpha.9`, each published as a **GitHub Pre-Release**. The last Alpha (Alpha.9) is the release candidate.
- The minor-version release is the bare version (e.g. `v26.0`), published as a **GitHub Release**.
- No Beta channel.

## 2. Artifact naming

Release artifacts are named per loader and Minecraft version:

```
Despotes-v26.0-Alpha.1-fabric-26.2.jar
Despotes-v26.0-Alpha.1-neoforge-1.21.1.jar
Despotes-v26.0-Alpha.1-forge-1.21.1.jar
Despotes-v26.0-Alpha.1-native-1.21.4.jar
Despotes-v26.0-Alpha.1-aprism-26.2.aje
```

Each GitHub release contains every artifact built in that cycle plus a `RELEASE_NOTES.md`.

## 3. Loader branches

Per Aprism §12 branch spec: one branch per loader — `fabric`, `neoforge`, `forge`, `native`, `aprism` — plus `main` (docs/spec only). Tags are cut from the loader branches; a release aggregates artifacts from all loader branches at the same version.

## 4. Minecraft support per line (v26.0 minimum goal)

| Loader | MC range |
|---|---|
| native | 1.20 – 26.2 |
| fabric | 1.20 – 26.2 |
| neoforge | 1.20.1 – 26.2 |
| forge | 1.20 – 1.21.4 |
| aprism | 26.1 – 26.2 (Aprism Loader v26 line) |

## 5. Interface contract

The control protocol and `despotes.json` schema are monotonically growing: fields may be added or deprecated (kept ≥ 1 LTS cycle), never removed or renamed within the v26 line.
