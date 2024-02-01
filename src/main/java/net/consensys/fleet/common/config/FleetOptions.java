/*
 * Copyright ConsenSys 2023
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package net.consensys.fleet.common.config;

import com.google.common.base.MoreObjects;
import picocli.CommandLine;

/** Fleet cli options. */
public class FleetOptions {

  static final FleetOptions INSTANCE = new FleetOptions();

  public static final String DEFAULT_LEADER_PEER_HTTP_HOST = "127.0.0.1";

  public static final int DEFAULT_LEADER_PEER_HTTP_PORT = 8545;
  public static final String DEFAULT_FOLLOWER_PEER_HTTP_HOST = "127.0.0.1";

  public static final int DEFAULT_FOLLOWER_PEER_HTTP_PORT = 8545;
  public static final int DEFAULT_FOLLOWER_MAX_BLOCK_PER_PERSIST = 50;
  public static final int DEFAULT_FOLLOWER_HEAD_DISTANCE_FOR_RECEIPT_FETCH = 2048;

  public static final String OPTION_LEADER_PEER_HTTP_HOST = "--plugin-fleet-leader-http-host";

  public static final String OPTION_LEADER_PEER_HTTP_PORT = "--plugin-fleet-leader-http-port";

  public static final String OPTION_FOLLOWER_PEER_HTTP_HOST = "--plugin-fleet-follower-http-host";
  public static final String OPTION_FOLLOWER_PEER_HTTP_PORT = "--plugin-fleet-follower-http-port";

  public static final String OPTION_FOLLOWER_MAX_BLOCK_PER_PERSIST =
      "--plugin-fleet-max-block-per-persist";

  public static final String HEAD_DISTANCE_FOR_RECEIPT_FETCH =
      "--plugin-fleet-follower-head_distance-for-receipt-fetch";

  @CommandLine.Option(
      names = {"--plugin-fleet-node-role"},
      paramLabel = "<MODE>",
      description = "Node role, possible values are ${COMPLETION-CANDIDATES} (default: LEADER)")
  Role nodeRole = Role.LEADER;

  @CommandLine.Option(
      names = {OPTION_LEADER_PEER_HTTP_HOST},
      hidden = true,
      defaultValue = DEFAULT_LEADER_PEER_HTTP_HOST,
      paramLabel = "<STRING>",
      description = "HTTP host of the leader peer")
  String leaderPeerHttpHost = DEFAULT_LEADER_PEER_HTTP_HOST;

  @CommandLine.Option(
      names = {OPTION_LEADER_PEER_HTTP_PORT},
      hidden = true,
      defaultValue = "8545",
      paramLabel = "<INTEGER>",
      description = "HTTP host port of the leader peer")
  Integer leaderPeerHttpPort = DEFAULT_LEADER_PEER_HTTP_PORT;

  @CommandLine.Option(
      names = {OPTION_FOLLOWER_PEER_HTTP_HOST},
      hidden = true,
      defaultValue = DEFAULT_FOLLOWER_PEER_HTTP_HOST,
      paramLabel = "<STRING>",
      description = "HTTP host of the leader peer")
  String followerPeerHttpHost = DEFAULT_FOLLOWER_PEER_HTTP_HOST;

  @CommandLine.Option(
      names = {OPTION_FOLLOWER_PEER_HTTP_PORT},
      hidden = true,
      defaultValue = "8545",
      paramLabel = "<INTEGER>",
      description = "HTTP host port of the leader peer")
  Integer followerPeerHttpPort = DEFAULT_FOLLOWER_PEER_HTTP_PORT;

  @CommandLine.Option(
      names = {OPTION_FOLLOWER_MAX_BLOCK_PER_PERSIST},
      hidden = true,
      defaultValue = "50",
      paramLabel = "<INTEGER>",
      description = "Limit the number of blocks persisted in a single operation")
  Integer maxBlocksPerPersist = DEFAULT_FOLLOWER_MAX_BLOCK_PER_PERSIST;

  @CommandLine.Option(
      names = {HEAD_DISTANCE_FOR_RECEIPT_FETCH},
      hidden = true,
      defaultValue = "2048",
      paramLabel = "<INTEGER>",
      description =
          "The distance from the head of the chain at which receipt fetching begins.(default: ${DEFAULT-VALUE})")
  Integer headDistanceForReceiptFetch = DEFAULT_FOLLOWER_HEAD_DISTANCE_FOR_RECEIPT_FETCH;

  private FleetOptions() {}

  public static FleetOptions create() {
    return INSTANCE;
  }

  public Role getNodeRole() {
    return nodeRole;
  }

  public String getLeaderPeerHttpHost() {
    return leaderPeerHttpHost;
  }

  public Integer getLeaderPeerHttpPort() {
    return leaderPeerHttpPort;
  }

  public String getFollowerPeerHttpHost() {
    return followerPeerHttpHost;
  }

  public Integer getFollowerPeerHttpPort() {
    return followerPeerHttpPort;
  }

  public Integer getMaxBlocksPerPersist() {
    return maxBlocksPerPersist;
  }

  public Integer getHeadDistanceForReceiptFetch() {
    return headDistanceForReceiptFetch;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("nodeRole", nodeRole)
        .add("leaderPeerHttpHost", leaderPeerHttpHost)
        .add("leaderPeerHttpPort", leaderPeerHttpPort)
        .add("leaderPeerHttpHost", followerPeerHttpHost)
        .add("leaderPeerHttpPort", followerPeerHttpPort)
        .add("persistRangeSize", maxBlocksPerPersist)
        .add("headDistanceForReceiptFetch", headDistanceForReceiptFetch)
        .toString();
  }
}
