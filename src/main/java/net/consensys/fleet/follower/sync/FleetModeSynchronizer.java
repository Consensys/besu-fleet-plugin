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
package net.consensys.fleet.follower.sync;

import net.consensys.fleet.common.plugin.PluginServiceProvider;
import net.consensys.fleet.common.rpc.model.NewHeadParams;
import net.consensys.fleet.follower.sync.BlockContextProvider.CompositeBlockKey;
import net.consensys.fleet.follower.sync.BlockContextProvider.FleetBlockContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.TrieLogService;
import org.hyperledger.besu.plugin.services.p2p.P2PService;
import org.hyperledger.besu.plugin.services.sync.SynchronizationService;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FleetModeSynchronizer {

  private static final Logger LOG = LoggerFactory.getLogger(FleetModeSynchronizer.class);

  private static final ScheduledExecutorService EXECUTOR_SERVICE =
      Executors.newSingleThreadScheduledExecutor();

  private final AtomicBoolean isWaitingForSync = new AtomicBoolean(true);

  private final PluginServiceProvider pluginServiceProvider;
  private final BlockContextProvider blockContextProvider;
  private final Integer
      maxBlocksPerPersist; // limit the number of blocks persisted in a single operation
  private final Integer headDistanceForReceiptFetch;

  /** retry interval increases by this number of milliseconds each time there's a block miss */
  private final long retryIncrease = 2;

  /** number of milliseconds to wait before retrying sync */
  private long syncDelay;

  private ScheduledFuture<?> syncScheduler;

  private NewHeadParams leaderHeader;

  public FleetModeSynchronizer(
      final PluginServiceProvider pluginServiceProvider,
      final BlockContextProvider blockContextProvider,
      final Integer maxBlocksPerPersist,
      final Integer headDistanceForReceiptFetch) {
    this.pluginServiceProvider = pluginServiceProvider;
    this.blockContextProvider = blockContextProvider;
    this.maxBlocksPerPersist = maxBlocksPerPersist;
    this.headDistanceForReceiptFetch = headDistanceForReceiptFetch;
  }

  public synchronized void syncNewHead(final NewHeadParams newHeadParams) {
    this.leaderHeader = newHeadParams;
    if (isBlockchainServiceReady()) {
      final SynchronizationService synchronizationService =
          pluginServiceProvider.getService(SynchronizationService.class);
      if (newHeadParams.getTrieLogRlp() != null) {
        LOG.info("add block to cache from leader {}", newHeadParams.getHead());
        blockContextProvider.provideLeaderBlockContext(newHeadParams);
      }
      synchronizationService.fireNewUnverifiedForkchoiceEvent(
          newHeadParams.getHead().getBlockHash(),
          newHeadParams.getSafeBlockHash(),
          newHeadParams.getFinalizedBlockHash());
      LOG.info(
          "Fire fork choice for safe block {} and finalized block {} ",
          newHeadParams.getSafeBlockHash(),
          newHeadParams.getFinalizedBlockHash());
      if (isWaitingForSync.get()) {
        LOG.info("Waiting for the end of the initial synchronization phase");
      } else {
        disableWaitingSync();
        startSync();
      }
    } else {
      LOG.info("Blockchain service is not ready");
    }
  }

  private void disableWaitingSync() {
    if (syncScheduler != null) {
      syncScheduler.cancel(false);
    }
    // start with 1ms interval
    syncDelay = 1;
  }

  private void startSync() {
    if (syncScheduler == null || syncScheduler.isDone()) {
      syncScheduler =
          EXECUTOR_SERVICE.schedule(
              () -> {
                if (isBlockchainServiceReady()) {
                  final BlockchainService blockchainService =
                      pluginServiceProvider.getService(BlockchainService.class);

                  final SynchronizationService synchronizationService =
                      pluginServiceProvider.getService(SynchronizationService.class);

                  final TrieLogProvider trieLogProvider =
                      pluginServiceProvider.getService(TrieLogService.class).getTrieLogProvider();
                  BlockHeader chainHead = blockchainService.getChainHeadHeader();
                  try {
                    do {

                      final List<FleetBlockContext> rollBackward = new ArrayList<>();
                      final List<FleetBlockContext> rollForward = new ArrayList<>();

                      FleetBlockContext persistedBlock =
                          getLocalBlockContext(chainHead.getNumber()).orElseThrow();
                      Hash persistedBlockHash = persistedBlock.getBlockHeader().getBlockHash();
                      long persistedBlockNumber = persistedBlock.getBlockHeader().getNumber();

                      FleetBlockContext targetBlock;
                      if ((leaderHeader.getFinalizedBlockNumber()
                              - persistedBlock.getBlockHeader().getNumber())
                          > maxBlocksPerPersist) {
                        targetBlock =
                            getLeaderBlockContext(
                                    new CompositeBlockKey(
                                        leaderHeader.getFinalizedBlockNumber(),
                                        leaderHeader.getFinalizedBlockHash()))
                                .orElseThrow(MissingBlockException::new);
                      } else {
                        targetBlock =
                            getLeaderBlockContext(new CompositeBlockKey(leaderHeader.getHead()))
                                .orElseThrow(MissingBlockException::new);
                      }

                      Hash targetBlockHash = targetBlock.getBlockHeader().getBlockHash();
                      long targetBlockNumber = targetBlock.getBlockHeader().getNumber();

                      LOG.info(
                          "New head (or leader block) being detected. {} ({})",
                          targetBlock.getBlockHeader().getNumber(),
                          targetBlock.getBlockHeader().getBlockHash());
                      LOG.info(
                          "Detected local chain head {} ({}",
                          chainHead.getNumber(),
                          chainHead.getBlockHash());

                      while (persistedBlock.getBlockHeader().getNumber()
                          > targetBlock.getBlockHeader().getNumber()) {
                        LOG.info("Rollback {}", persistedBlockHash);
                        rollBackward.add(
                            getLocalBlockContext(persistedBlock.getBlockHeader().getNumber())
                                .orElseThrow());
                        persistedBlock =
                            getLocalBlockContext(persistedBlock.getBlockHeader().getNumber() - 1)
                                .orElseThrow();
                        persistedBlockHash = persistedBlock.getBlockHeader().getBlockHash();
                        persistedBlockNumber = persistedBlock.getBlockHeader().getNumber();
                      }

                      while (persistedBlock.getBlockHeader().getNumber()
                          < targetBlock.getBlockHeader().getNumber()) {
                        LOG.info("Rollforward {}", targetBlockHash);
                        final FleetBlockContext toRollForwardBlock =
                            getLeaderBlockContext(
                                    new CompositeBlockKey(targetBlock.getBlockHeader()))
                                .orElseThrow(MissingBlockException::new);
                        rollForward.add(toRollForwardBlock);
                        if (persistedBlock.getBlockHeader().getNumber()
                                == (toRollForwardBlock.getBlockHeader().getNumber() - 1)
                            && toRollForwardBlock
                                .getBlockHeader()
                                .getParentHash()
                                .equals(persistedBlockHash)) {
                          targetBlock = persistedBlock;
                        } else {
                          targetBlock =
                              getLeaderBlockContext(
                                      new CompositeBlockKey(
                                          targetBlock.getBlockHeader().getNumber() - 1,
                                          targetBlock.getBlockHeader().getParentHash()))
                                  .orElseThrow(MissingBlockException::new);
                        }
                        targetBlockHash = targetBlock.getBlockHeader().getBlockHash();
                        targetBlockNumber = targetBlock.getBlockHeader().getNumber();
                      }

                      while (!persistedBlockHash.equals(targetBlockHash)
                          && persistedBlockNumber == targetBlockNumber) {
                        LOG.info("Reorg detected so we clean the cache");
                        blockContextProvider.clear();
                        // add again the the new head in the cache (avoid useless rpc request)
                        blockContextProvider.provideLeaderBlockContext(leaderHeader);
                        LOG.info("Paired rollback {}", persistedBlockHash);
                        LOG.info("Paired rollforward {}", targetBlockHash);

                        rollForward.add(
                            getLeaderBlockContext(
                                    new CompositeBlockKey(targetBlock.getBlockHeader()))
                                .orElseThrow(MissingBlockException::new));
                        targetBlock =
                            getLeaderBlockContext(
                                    new CompositeBlockKey(
                                        targetBlock.getBlockHeader().getNumber() - 1,
                                        targetBlock.getBlockHeader().getParentHash()))
                                .orElseThrow(MissingBlockException::new);

                        rollBackward.add(
                            getLocalBlockContext(persistedBlock.getBlockHeader().getNumber())
                                .orElseThrow(MissingBlockException::new));
                        persistedBlock =
                            getLocalBlockContext(persistedBlock.getBlockHeader().getNumber() - 1)
                                .orElseThrow();

                        targetBlockHash = targetBlock.getBlockHeader().getBlockHash();
                        targetBlockNumber = targetBlock.getBlockHeader().getNumber();
                        persistedBlockHash = persistedBlock.getBlockHeader().getBlockHash();
                        persistedBlockNumber = persistedBlock.getBlockHeader().getNumber();
                      }

                      for (FleetBlockContext blockContext : rollBackward) {
                        LOG.info(
                            "Attempting rollback of {}",
                            blockContext.getBlockHeader().getBlockHash());
                        Optional<Bytes> maybeTrieLog = blockContext.trieLogRlp();
                        maybeTrieLog.ifPresent(
                            bytes ->
                                trieLogProvider.saveRawTrieLogLayer(
                                    blockContext.getBlockHeader().getBlockHash(),
                                    blockContext.getBlockHeader().getNumber(),
                                    bytes));
                        final boolean result =
                            synchronizationService.setHeadUnsafe(
                                blockContext.getBlockHeader(), blockContext.getBlockBody());
                        if (!result) {
                          throw new Exception(
                              "Unable to complete rollback of block %s"
                                  .formatted(blockContext.getBlockHeader().getBlockHash()));
                        }
                      }

                      rollForward.sort(
                          Comparator.comparingLong(o -> o.getBlockHeader().getNumber()));

                      for (FleetBlockContext blockContext : rollForward) {
                        LOG.info(
                            "Attempting rollforward of {}",
                            blockContext.getBlockHeader().getBlockHash());
                        // save trielog and set head
                        Optional<Bytes> maybeTrieLog = blockContext.trieLogRlp();
                        if (maybeTrieLog.isPresent()) {
                          // save trielog
                          trieLogProvider.saveRawTrieLogLayer(
                              blockContext.getBlockHeader().getBlockHash(),
                              blockContext.getBlockHeader().getNumber(),
                              maybeTrieLog.get());
                          // save block
                          blockchainService.storeBlock(
                              blockContext.getBlockHeader(),
                              blockContext.getBlockBody(),
                              blockContext.getReceipts());
                          // update head
                          final boolean result =
                              synchronizationService.setHeadUnsafe(
                                  blockContext.getBlockHeader(), blockContext.getBlockBody());
                          if (!result) {
                            throw new Exception(
                                "Error occurred while importing block %s"
                                    .formatted(blockContext.getBlockHeader().getBlockHash()));
                          }
                        }
                      }

                      final BlockContext oldHead =
                          getLocalBlockContext(chainHead.getNumber()).orElseThrow();
                      // update chain head
                      chainHead = blockchainService.getChainHeadHeader();
                      final BlockContext newHead =
                          getLocalBlockContext(chainHead.getNumber()).orElseThrow();

                      if (oldHead
                          .getBlockHeader()
                          .getBlockHash()
                          .equals(chainHead.getBlockHash())) {
                        LOG.info("head not changed {}", chainHead.getBlockHash());
                      } else if (Math.abs(
                              newHead.getBlockHeader().getNumber()
                                  - oldHead.getBlockHeader().getNumber())
                          > 1) {
                        LOG.info(
                            "Fleet import progression: block {} ({}) reached.",
                            newHead.getBlockHeader().getNumber(),
                            newHead.getBlockHeader().getBlockHash());
                      } else {
                        logImportedBlockInfo(newHead.getBlockHeader(), newHead.getBlockBody());
                      }

                      // reset local cache
                      blockContextProvider.clear();

                    } while (!chainHead
                        .getBlockHash()
                        .equals(this.leaderHeader.getHead().getBlockHash()));
                  } catch (MissingBlockException e) {
                    // increase the time we wait before retrying
                    syncDelay += retryIncrease;
                    startSync();
                    LOG.info("Missing block in the leader, retry after {} ms", syncDelay);
                  } catch (Exception e) {
                    // reset local cache
                    blockContextProvider.clear();
                    LOG.error("Error during sync because of : {}", e.getMessage());
                  }
                }
              },
              syncDelay,
              TimeUnit.MILLISECONDS);
    }
  }

  public void disableP2P() {
    final SynchronizationService synchronizationService =
        pluginServiceProvider.getService(SynchronizationService.class);
    if (synchronizationService.isInitialSyncPhaseDone()) {
      if (isWaitingForSync.getAndSet(false)) {
        final P2PService p2PService = pluginServiceProvider.getService(P2PService.class);
        LOG.info("Disable P2P discovery");
        p2PService.disableDiscovery();
      }
    }
  }

  public void disableTrie() {
    final SynchronizationService synchronizationService =
        pluginServiceProvider.getService(SynchronizationService.class);
    if (synchronizationService.isInitialSyncPhaseDone()) {
      LOG.info("Disable state trie for follower");
      synchronizationService.disableWorldStateTrie();
    }
  }

  private void logImportedBlockInfo(final BlockHeader header, final BlockBody body) {
    final StringBuilder message = new StringBuilder();
    message.append("Fleet Imported #%,d / %d tx");
    final List<Object> messageArgs =
        new ArrayList<>(List.of(header.getNumber(), body.getTransactions().size()));
    if (body.getWithdrawals().isPresent()) {
      message.append(" / %d ws");
      messageArgs.add(body.getWithdrawals().get().size());
    }
    message.append(" / base fee %s / %,d (%01.1f%%) gas / (%s)");
    messageArgs.addAll(
        List.of(
            header
                .getBaseFee()
                .map(Wei::fromQuantity)
                .map(Wei::toHumanReadableString)
                .orElse("N/A"),
            header.getGasUsed(),
            (header.getGasUsed() * 100.0) / header.getGasLimit(),
            header.getBlockHash().toHexString()));
    LOG.info(String.format(message.toString(), messageArgs.toArray()));
  }

  private boolean isBlockchainServiceReady() {
    return pluginServiceProvider.isServiceAvailable(BlockchainService.class);
  }

  private Optional<FleetBlockContext> getLeaderBlockContext(
      final CompositeBlockKey compositeBlockKey) {
    return blockContextProvider.getLeaderBlockContextByNumber(
        compositeBlockKey,
        (leaderHeader.getHead().getNumber() - compositeBlockKey.getBlockNumber())
            <= headDistanceForReceiptFetch);
  }

  private Optional<FleetBlockContext> getLocalBlockContext(final long blockNumber) {
    return blockContextProvider.getLocalBlockContextByNumber(blockNumber, false);
  }
}
