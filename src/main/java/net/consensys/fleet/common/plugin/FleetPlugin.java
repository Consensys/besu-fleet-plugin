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
package net.consensys.fleet.common.plugin;

import net.consensys.fleet.common.config.FleetOptions;
import net.consensys.fleet.common.config.Role;
import net.consensys.fleet.common.peer.PeerNetworkMaintainer;
import net.consensys.fleet.common.peer.PeerNodesManager;
import net.consensys.fleet.common.rpc.client.WebClientWrapper;
import net.consensys.fleet.common.rpc.json.ConvertMapperProvider;
import net.consensys.fleet.common.rpc.server.FleetGetConfigServer;
import net.consensys.fleet.common.rpc.server.PluginRpcMethod;
import net.consensys.fleet.common.trielog.FleetTrieLogService;
import net.consensys.fleet.follower.event.InitialSyncCompletionObserver;
import net.consensys.fleet.follower.peer.FollowerPeerNetworkMaintainer;
import net.consensys.fleet.follower.rpc.client.FleetGetBlockClient;
import net.consensys.fleet.follower.rpc.server.FleetShipNewHeadServer;
import net.consensys.fleet.follower.sync.BlockContextProvider;
import net.consensys.fleet.follower.sync.FleetModeSynchronizer;
import net.consensys.fleet.leader.event.BlockAddedObserver;
import net.consensys.fleet.leader.peer.LeaderPeerNetworkMaintainer;
import net.consensys.fleet.leader.rpc.client.FleetShipNewHeadClient;
import net.consensys.fleet.leader.rpc.server.FleetAddFollowerServer;
import net.consensys.fleet.leader.rpc.server.FleetGetBlockServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import com.google.auto.service.AutoService;
import org.hyperledger.besu.plugin.BesuPlugin;
import org.hyperledger.besu.plugin.ServiceManager;
import org.hyperledger.besu.plugin.services.BesuConfiguration;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.PicoCLIOptions;
import org.hyperledger.besu.plugin.services.RpcEndpointService;
import org.hyperledger.besu.plugin.services.TrieLogService;
import org.hyperledger.besu.plugin.services.p2p.P2PService;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;
import org.hyperledger.besu.plugin.services.transactionpool.TransactionPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoService(BesuPlugin.class)
public class FleetPlugin implements BesuPlugin {
  private static final Logger LOG = LoggerFactory.getLogger(FleetPlugin.class);
  private static final String NAME = "fleet";
  private static final FleetOptions CLI_OPTIONS = FleetOptions.create();
  private ServiceManager serviceManager;
  private PluginServiceProvider pluginServiceProvider;

  private PeerNodesManager peerManagers;
  private ConvertMapperProvider convertMapperProvider;
  private WebClientWrapper webClient;

  private FleetModeSynchronizer fleetModeSynchronizer;
  private final AtomicLong leaderBlockAddedObserverId = new AtomicLong(-1);
  private final AtomicLong followerSyncCompletionListenerId = new AtomicLong(-1);

  // TODO Spit logic of besu plugin to leader besu plugin and follower besu plugin
  @Override
  public void register(final ServiceManager serviceManager) {
    LOG.info("Registering Fleet plugin");
    this.serviceManager = serviceManager;
    this.pluginServiceProvider = new PluginServiceProvider();
    this.convertMapperProvider = new ConvertMapperProvider(pluginServiceProvider);

    LOG.info("Adding command line params");
    final Optional<PicoCLIOptions> cmdlineOptions = serviceManager.getService(PicoCLIOptions.class);

    if (cmdlineOptions.isEmpty()) {
      throw new IllegalStateException(
          "Expecting a PicoCLI options to register CLI options with, but none found.");
    }
    cmdlineOptions.get().addPicoCLIOptions(NAME, CLI_OPTIONS);

    LOG.debug("Creating peer manager");
    peerManagers = new PeerNodesManager();
    this.webClient = new WebClientWrapper(convertMapperProvider, peerManagers);

    LOG.debug("Setting up RPC endpoints");
    final List<PluginRpcMethod> pluginRpcMethods = createServerMethods();
    serviceManager
        .getService(RpcEndpointService.class)
        .ifPresent(
            rpcEndpointService ->
                pluginRpcMethods.forEach(
                    method -> {
                      LOG.info(
                          "Registering RPC plugin endpoint {}_{}",
                          method.getNamespace(),
                          method.getName());
                      rpcEndpointService.registerRPCEndpoint(
                          method.getNamespace(), method.getName(), method::execute);
                    }));

    LOG.debug("Registering trieLog service");
    final TrieLogService trieLogService = new FleetTrieLogService();
    serviceManager.addService(TrieLogService.class, trieLogService);
    pluginServiceProvider.provideService(TrieLogService.class, () -> trieLogService);
  }

  @Override
  public void start() {

    LOG.debug("Loading RLP converter service");
    final RlpConverterService rlpConverterService =
        serviceManager
            .getService(RlpConverterService.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Expecting a RLP converter service, but none found."));
    pluginServiceProvider.provideService(RlpConverterService.class, () -> rlpConverterService);

    LOG.debug("Loading blockchain service");
    final BlockchainService blockchainService =
        serviceManager
            .getService(BlockchainService.class)
            .orElseThrow(
                () -> new IllegalStateException("Expecting a blockchain service, but none found."));
    pluginServiceProvider.provideService(BlockchainService.class, () -> blockchainService);

    LOG.debug("Loading synchronization service");
    final SynchronizationService synchronizationService =
        serviceManager
            .getService(SynchronizationService.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Expecting a synchronization service, but none found."));
    pluginServiceProvider.provideService(
        SynchronizationService.class, () -> synchronizationService);

    LOG.debug("Loading P2P network service");
    final P2PService p2PService =
        serviceManager
            .getService(P2PService.class)
            .orElseThrow(
                () ->
                    new IllegalStateException("Expecting a P2P network service, but none found."));
    pluginServiceProvider.provideService(P2PService.class, () -> p2PService);

    loadingClientsMethods();

    createPeerNetworkMaintainer();
  }

  @Override
  public void afterExternalServicePostMainLoop() {
    if (isFollower()) {
      disableTransactionPool();
      fleetModeSynchronizer.disableP2P();
      fleetModeSynchronizer.disableTrie();
    }
  }

  @Override
  public void stop() {
    // no-op
  }

  private void createPeerNetworkMaintainer() {
    LOG.debug("Setting up connection parameters");
    final PeerNetworkMaintainer peerNetworkMaintainer;

    LOG.debug("Loading BesuConfiguration service");
    final BesuConfiguration besuConfigurationService =
        serviceManager
            .getService(BesuConfiguration.class)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Expecting a BesuConfiguration service, but none found."));

    switch (CLI_OPTIONS.getNodeRole()) {
      case LEADER -> {
        /* ********** LEADER ************* */
        peerNetworkMaintainer = new LeaderPeerNetworkMaintainer(peerManagers);
      }
      case FOLLOWER -> {
        /* ********** FOLLOWER ************* */
        peerNetworkMaintainer =
            new FollowerPeerNetworkMaintainer(
                CLI_OPTIONS.getLeaderPeerHttpHost(),
                CLI_OPTIONS.getLeaderPeerHttpPort(),
                besuConfigurationService.getRpcHttpHost().orElse("default"),
                besuConfigurationService.getRpcHttpPort().orElse(0),
                CLI_OPTIONS.getFollowerHeartBeatDelay(),
                peerManagers,
                webClient);
      }
      default -> throw new IllegalStateException(
          "Unexpected node role: " + CLI_OPTIONS.getNodeRole());
    }
    peerNetworkMaintainer.start();
  }

  @Override
  public CompletableFuture<Void> reloadConfiguration() {

    LOG.info("Reloading configuration");
    LOG.info(FleetOptions.create().toString());

    if (leaderBlockAddedObserverId.get() != -1) {
      serviceManager
          .getService(BesuEvents.class)
          .ifPresent(
              besuEvents -> {
                besuEvents.removeBlockAddedListener(leaderBlockAddedObserverId.get());
                leaderBlockAddedObserverId.set(-1);
              });
    }
    if (followerSyncCompletionListenerId.get() != -1) {
      serviceManager
          .getService(BesuEvents.class)
          .ifPresent(
              besuEvents -> {
                besuEvents.removeSyncStatusListener(followerSyncCompletionListenerId.get());
                followerSyncCompletionListenerId.set(-1);
              });
    }
    // reload clients methods
    loadingClientsMethods();
    LOG.info("Configuration reloaded");
    return CompletableFuture.completedFuture(null);
  }

  private List<PluginRpcMethod> createServerMethods() {
    final List<PluginRpcMethod> methods = new ArrayList<>();
    final BlockContextProvider blockContextProvider =
        new BlockContextProvider(pluginServiceProvider, new FleetGetBlockClient(webClient));
    methods.add(new FleetGetConfigServer(convertMapperProvider));
    methods.add(new FleetAddFollowerServer(peerManagers));
    methods.add(new FleetGetBlockServer(convertMapperProvider, pluginServiceProvider));
    fleetModeSynchronizer =
        new FleetModeSynchronizer(
            pluginServiceProvider,
            blockContextProvider,
            CLI_OPTIONS.getMaxBlocksPerPersist(),
            CLI_OPTIONS.getHeadDistanceForReceiptFetch());
    methods.add(
        new FleetShipNewHeadServer(
            (newHeadParams) -> fleetModeSynchronizer.syncNewHead(newHeadParams),
            convertMapperProvider,
            pluginServiceProvider));
    return methods;
  }

  private void loadingClientsMethods() {
    switch (CLI_OPTIONS.getNodeRole()) {
      case LEADER -> {
        /* ********** LEADER ************* */
        LOG.info("Adding blockchain observer");
        final BlockAddedObserver blockAddedObserver =
            new BlockAddedObserver(pluginServiceProvider, new FleetShipNewHeadClient(webClient));
        serviceManager
            .getService(BesuEvents.class)
            .ifPresentOrElse(
                besuEvents -> {
                  leaderBlockAddedObserverId.set(
                      besuEvents.addBlockAddedListener(blockAddedObserver));
                },
                () -> LOG.error("Could not obtain BesuEvents"));
      }
      case FOLLOWER -> {
        /* ********** FOLLOWER ************* */
        LOG.info("Adding sync status observer");
        serviceManager
            .getService(BesuEvents.class)
            .ifPresentOrElse(
                besuEvents -> {
                  followerSyncCompletionListenerId.set(
                      besuEvents.addInitialSyncCompletionListener(
                          new InitialSyncCompletionObserver(() -> fleetModeSynchronizer)));
                },
                () -> LOG.error("Could not obtain BesuEvents"));
      }
    }
  }

  @SuppressWarnings("unused")
  private boolean isLeader() {
    return CLI_OPTIONS.getNodeRole().equals(Role.LEADER);
  }

  private boolean isFollower() {
    return CLI_OPTIONS.getNodeRole().equals(Role.FOLLOWER);
  }

  private void disableTransactionPool() {
    LOG.debug("Disable transaction pool");
    serviceManager
        .getService(TransactionPoolService.class)
        .ifPresent(TransactionPoolService::disableTransactionPool);
  }
}
