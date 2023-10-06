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
package net.consensys.fleet.follower.peer;

import net.consensys.fleet.common.peer.PeerNetworkMaintainer;
import net.consensys.fleet.common.peer.PeerNodesManager;
import net.consensys.fleet.common.rpc.client.WebClientWrapper;
import net.consensys.fleet.common.rpc.model.PeerNode;
import net.consensys.fleet.follower.rpc.client.FleetAddFollowerClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FollowerPeerNetworkMaintainer extends PeerNetworkMaintainer {

  private static final Logger LOG = LoggerFactory.getLogger(FollowerPeerNetworkMaintainer.class);
  private static final ScheduledExecutorService EXECUTOR_SERVICE =
      Executors.newSingleThreadScheduledExecutor();

  private final AtomicBoolean isRunning = new AtomicBoolean();
  private final String leaderHttpHost;
  private final int leaderHttpPort;

  private final String followerHttpHost;
  private final int followerHttpPort;
  private final PeerNodesManager peerNodesManager;
  private final FleetAddFollowerClient fleetAddFollowerClient;

  public FollowerPeerNetworkMaintainer(
      final String leaderHttpHost,
      final int leaderHttpPort,
      final String followerHttpHost,
      final int followerHttpPort,
      final PeerNodesManager peerNodesManager,
      final WebClientWrapper webClient) {
    this.leaderHttpHost = leaderHttpHost;
    this.leaderHttpPort = leaderHttpPort;
    this.followerHttpHost = followerHttpHost;
    this.followerHttpPort = followerHttpPort;
    this.peerNodesManager = peerNodesManager;
    fleetAddFollowerClient = new FleetAddFollowerClient(webClient);
  }

  @Override
  public void start() {
    if (!isRunning.getAndSet(true)) {

      // create cache and register the leader
      peerNodesManager.createCache(false);
      peerNodesManager.register(new PeerNode(leaderHttpHost, leaderHttpPort));

      // start heartbeat
      EXECUTOR_SERVICE.scheduleAtFixedRate(
          () -> {
            final CompletableFuture<Boolean> completableFuture =
                fleetAddFollowerClient.sendData(new PeerNode(followerHttpHost, followerHttpPort));
            completableFuture.whenComplete(
                (isSucceed, throwable) -> {
                  if (!isSucceed) {
                    LOG.error(
                        "Unable to establish a connection with the leader retry in 30 secondes");
                  } else {
                    LOG.info(
                        "Connection attempt with the leader was successful leader {}:{} for follower {}:{}",
                        leaderHttpHost,
                        leaderHttpPort,
                        followerHttpHost,
                        followerHttpPort);
                  }
                });
          },
          0,
          30,
          TimeUnit.SECONDS);
    }
  }

  @Override
  public PeerNodesManager getPeerNodesManager() {
    return peerNodesManager;
  }
}
