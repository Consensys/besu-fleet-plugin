# Changelog

## Unreleased
- Fixed a bug that prevented the node from syncing with an old snapshot [#46](https://github.com/Consensys/besu-fleet-plugin/pull/46)
  
## 0.3.8
- `--plugin-fleet-follower-http-host` and `--plugin-fleet-follower-http-port` options are deprecated and will be removed in a future release.
- use BesuConfiguration service to get RPC host and port values as configured in Besu
- Changes the logic to have the new head information directly in the besu event. [#45](https://github.com/Consensys/besu-fleet-plugin/pull/45)

## 0.2.0
- fix cli parameter parsing regression in 0.1.0

## 0.1.0
- Initial build of besu-fleet-plugin
- uses 23.4.1-SNAPSHOT as besu version
