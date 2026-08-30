# Azure DevOps Integration — focused 4.1 fork

This branch is based on upstream tag `4.1` and produces plugin version
`4.1.1-focused.1`.

## Changes

- The default PR view is **Relevant to me**: PRs created by the current user or
  assigned to the current user. The two sides of this OR are fetched with
  Azure DevOps `searchCriteria.creatorId` and `searchCriteria.reviewerId`, then
  merged and de-duplicated.
- **Assigned to you** now means an actual reviewer assignment, not “you have
  already voted on it”.
- **Review requests** uses the reviewer assignment query and keeps PRs where
  the current user's vote is still zero or absent. Group assignments remain
  visible.
- Existing file-scoped PR comments are shown as persistent cards below their
  diff line. Clicking a card or its gutter bubble opens the existing thread UI
  for reply and Resolve.
- Azure's common response shape where line positions are in `threadContext`
  and iteration metadata is in `pullRequestThreadContext` is handled correctly.
- The explicit all-organization quick filter is labelled `Open (all
  organization)` so its larger request scope is visible.

## Build outside the company network

Use a machine with JDK 21 and network access to the Gradle Plugin Portal,
Maven Central, and JetBrains IntelliJ repositories:

```bash
./gradlew test
./gradlew verifyPlugin
./gradlew buildPlugin
```

The installable file is written to
`build/distributions/AzureDevOps-4.1.1-focused.1.zip`.

The project targets IntelliJ IDEA 2025.3 (`since-build 253`). In IntelliJ,
install it with **Settings → Plugins → gear → Install Plugin from Disk**.
Because the plugin ID is intentionally unchanged, disable or uninstall the
official Azure DevOps Integration plugin first if IntelliJ reports a conflict.
Restart IntelliJ after installation.

## Verification

The added tests cover the OR query plan, Azure query URL encoding, reviewer
vote semantics, organization-safe de-duplication, and the comment-context
regression. Live Azure DevOps integration still needs to be checked from a
machine that can reach the company's organization and repository.
