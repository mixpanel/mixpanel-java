# Releasing the OpenFeature Provider

The OpenFeature provider (`com.mixpanel:mixpanel-java-openfeature`) is published to Maven Central independently from the core SDK.

## Prerequisites

The following GitHub secrets must be configured (shared with the core SDK release workflow):

- `GPG_PRIVATE_KEY` — Base64-encoded GPG private key
- `GPG_PASSPHRASE` — GPG key passphrase
- `MAVEN_CENTRAL_USERNAME` — Maven Central Portal username
- `MAVEN_CENTRAL_TOKEN` — Maven Central Portal token

## Releasing via GitHub Actions

1. Go to **Actions** > **Release OpenFeature Provider to Maven Central**
2. Click **Run workflow**
3. Enter the version to release (e.g., `0.1.0`)
4. The workflow will:
   - Build and install the core SDK locally
   - Run OpenFeature provider tests
   - Sign artifacts with GPG
   - Deploy to Maven Central Portal
   - Wait 5 minutes then verify the artifact is available

After deployment, artifacts are visible at:
- Deployments: https://central.sonatype.com/publishing/deployments
- Published: https://central.sonatype.com/artifact/com.mixpanel/mixpanel-java-openfeature

Note: `autoPublish` is set to `false` in `pom.xml`, so you may need to manually publish the deployment from the Sonatype Central Portal.

## Releasing manually

```bash
# 1. Set the version
cd openfeature-provider
mvn versions:set -DnewVersion=0.1.0 -DgenerateBackupPoms=false

# 2. Build and install the core SDK locally
cd ..
mvn clean install -DskipTests -Dgpg.skip=true

# 3. Run tests
cd openfeature-provider
mvn clean test

# 4. Deploy
mvn clean deploy -Dgpg.passphrase=<YOUR_GPG_PASSPHRASE>
```

## Versioning

The OpenFeature provider is versioned independently from the core SDK. The current core SDK dependency version is pinned in `pom.xml` — update it when the provider needs features from a newer core SDK release.
