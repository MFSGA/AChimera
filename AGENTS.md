# AChimera Agent Guidelines

## clash-lib / Chimera_Client dependency pinning

- All `clash-lib` dependencies used by AChimera must be pinned to an exact, reproducible release revision.
- Use a release-first maintenance workflow: choose an explicit `Chimera_Client` release (for example `v0.24.4`), resolve that remote tag to its exact full commit SHA, inspect that tagged source, and pin AChimera to that SHA.
- For Git dependencies from `MFSGA/Chimera_Client`, always use an exact commit SHA via `rev = "<full-commit-sha>"`. Do not use `master`, another floating branch, `HEAD`, or an unpinned Git dependency, even when that branch currently represents the newest code.
- Prefer the full commit SHA over `tag = "vX.Y.Z"` in AChimera dependency declarations. The release tag is used to select and audit the intended release; the resolved SHA is the immutable dependency identity used by the build.
- When resolving a release, verify the tag against the remote repository rather than assuming a local tag is current. Record both the human-readable release version and the exact SHA in the change/commit context.
- If `clash-lib` is ever consumed from a package registry instead of Git, use an exact version requirement rather than a floating semver range.
- Keep `uniffi/Cargo.lock` committed and synchronized with the pinned `clash-lib` revision/version.
- Treat every `clash-lib` upgrade as its own reviewable migration slice: document the old release/SHA and new release/SHA, inspect the relevant `Chimera_Client/clash-lib` API and behavior changes, update the lockfile, then run the smallest relevant Cargo checks/tests before Gradle/Android validation.
- Do not move AChimera to a newer `Chimera_Client` branch merely to pick up one fix. If the selected fixed release lacks a required fix, first land or backport that fix in `Chimera_Client`, validate it there, produce/select an explicit fixed release revision, then update AChimera to the exact verified SHA.
- Never silently mix code from different `Chimera_Client` release lines. When comparing or backporting behavior, state the source release/commit and the target release/commit explicitly.
- If `clash-android` expects a `clash-lib` API or behavior that is not present in the currently pinned `Chimera_Client` release revision, stop that migration slice and report the divergence instead of guessing compatibility or changing to a floating dependency.

### Standard clash-lib upgrade sequence

1. Select the intended `Chimera_Client` release version.
2. Resolve the remote release tag to its exact full commit SHA.
3. Inspect the tagged `clash-lib` source and confirm the required API/behavior exists at that SHA.
4. Update AChimera's `clash-lib` `rev` to that exact SHA; never point it at a branch.
5. Regenerate/update `uniffi/Cargo.lock` and confirm all `Chimera_Client` Git sources resolve to the same intended SHA.
6. Run focused Cargo checks/tests, then the minimum relevant Gradle/Android validation.
7. Commit the dependency upgrade as a standalone, reviewable change only after validation passes.
