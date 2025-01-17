# Besu-Fleet Plugin

A plugin that allows a leader to share the state with one or more followers.

Since the leader is trusted, the follower can operate in light mode and only contains the flat database, not the trie. 
The follower only needs to retrieve the trie logs from the leader and apply them to its database without having to validate or process the transactions.

# Quick Start

Use the Gradle wrapper to build the plugin:
`./gradlew build`

Then, run the besu with leader mode:
```
/bin/besu --data-storage-format=BONSAI \
--plugin-fleet-node-role=LEADER \
--rpc-http-apis=FLEET,ETH \
--rpc-http-enabled \
--rpc-http-host=127.0.0.1
--rpc-http-port=8545
```

Or, run the besu with follower mode:
```
/bin/besu --data-storage-format=BONSAI \
--data-path="workdir/besu-follower/data" \
--Xbonsai-full-flat-db-enabled=true \
--plugin-fleet-node-role=FOLLOWER \
--p2p-port=40404 \
--rpc-http-apis=FLEET,ETH \
--rpc-http-enabled \
--rpc-http-host=127.0.0.1 \
--rpc-http-port=8888 \
--engine-rpc-port=8661 \
--Xchain-pruning-blocks-retained=512 \
--plugin-fleet-leader-http-host=127.0.0.1 \
--plugin-fleet-leader-http-port=8545 \
--plugin-fleet-follower-http-host=127.0.0.1 \
--plugin-fleet-follower-http-port=8888
```

# Download
The latest plugin release version is available on the [releases](https://github.com/ConsenSys/besu-fleet-plugin/releases) page.

To use a release version, follow these steps:

1. Download the plugin jar.
2. Create a plugins/ folder in your Besu installation directory.
3. Copy the plugin jar into the plugins/ directory.
4. Start Besu with the Fleet-specific configurations listed below.

# Configuration
Besu will autodetect the presence of the plugin and load and configure it at startup.  However some additional configuration is to ensure besu and fleet can communicate.

## Enable FLEET RPC-HTTP-API Endpoint
The plugin registers an additional RPC namespace `FLEET` for Fleet to query Besu for trielogs. Enable this namespace in Besu's configuration through the [--rpc-http-api config](https://besu.hyperledger.org/en/stable/public-networks/reference/cli/options/#rpc-http-api) option.  

# Development

## Building from Source
To build the plugin from source and run tests, use the following command:

``./gradlew build``

The resulting JAR file will be located in the build/libs directory.

## Installation from Source
After building from source, you can install the plugin on an existing Besu installation. Here's how to do that:

1. Create a plugins directory in your Besu installation directory, if one doesn't already exist:
`mkdir -p /opt/besu/plugins`

2. Copy the built JAR file into the plugins directory: 
mkdir -p /opt/besu/plugins
`cp build/libs/besu-fleet-plugin-0.1.0.jar /opt/besu/plugins`


## Release Process
Here are the steps for releasing a new version of the plugin:
1. Create a tag with the release version number in the format vX.Y.Z (e.g., v0.2.0 creates a release version 0.2.0).
2. Push the tag to the repository.
3. GitHub Actions will automatically create a draft release for the release tag.
4. Once the release workflow completes, update the release notes, uncheck "Draft", and publish the release.

**Note**: Release tags (of the form v*) are protected and can only be pushed by organization and/or repository owners. 
