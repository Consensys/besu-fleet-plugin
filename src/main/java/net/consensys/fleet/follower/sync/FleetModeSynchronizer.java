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

  private final long retryFactor = 2;

  private long syncDelay;

  private ScheduledFuture<?> syncScheduler;

  private BlockHeader leaderHeader;

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

  public synchronized void syncNewHead(
      final BlockHeader head, final Hash safeBlock, final Hash finalizedBlock) {
    this.leaderHeader = head;
    if (isBlockchainServiceReady()) {
      final SynchronizationService synchronizationService =
          pluginServiceProvider.getService(SynchronizationService.class);
      synchronizationService.fireNewUnverifiedForkchoiceEvent(
          head.getBlockHash(), safeBlock, finalizedBlock);
      LOG.debug(
          "fire fork choice for safe block {} an finalized block {} ", safeBlock, finalizedBlock);

      if (isWaitingForSync.get()) {
        LOG.debug("Waiting for the end of the initial synchronization phase");
      } else {
        disableWaitingSync();
        startSync();
      }
    }
  }

  private void disableWaitingSync() {
    if (syncScheduler != null) {
      syncScheduler.cancel(false);
    }
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

                      final List<BlockContextProvider.FleetBlockContext> rollBackward =
                          new ArrayList<>();
                      final List<BlockContextProvider.FleetBlockContext> rollForward =
                          new ArrayList<>();

                      BlockContext persistedBlock =
                          getLocalBlockContext(chainHead.getNumber()).orElseThrow();
                      Hash persistedBlockHash = persistedBlock.getBlockHeader().getBlockHash();

                      final long targetBlockNumber =
                          calculateRangeLimit(chainHead.getNumber(), this.leaderHeader.getNumber());
                      BlockContext targetBlock =
                          getLeaderBlockContext(targetBlockNumber)
                              .orElseThrow(MissingBlockException::new);
                      Hash targetBlockHash = targetBlock.getBlockHeader().getBlockHash();

                      LOG.debug(
                          "New head (or leader block) being detected. {} ({})",
                          targetBlock.getBlockHeader().getNumber(),
                          targetBlock.getBlockHeader().getBlockHash());
                      LOG.debug(
                          "Detected local chain head {} ({}",
                          chainHead.getNumber(),
                          chainHead.getBlockHash());

                      while (persistedBlock.getBlockHeader().getNumber()
                          > targetBlock.getBlockHeader().getNumber()) {
                        LOG.debug("Rollback {}", persistedBlockHash);
                        rollBackward.add(
                            getLocalBlockContext(persistedBlock.getBlockHeader().getNumber())
                                .orElseThrow());
                        persistedBlock =
                            getLocalBlockContext(persistedBlock.getBlockHeader().getNumber() - 1)
                                .orElseThrow();
                        persistedBlockHash = persistedBlock.getBlockHeader().getBlockHash();
                      }

                      while (persistedBlock.getBlockHeader().getNumber()
                          < targetBlock.getBlockHeader().getNumber()) {
                        LOG.debug("Rollforward {}", targetBlockHash);
                        rollForward.add(
                            getLeaderBlockContext(targetBlock.getBlockHeader().getNumber())
                                .orElseThrow(MissingBlockException::new));
                        targetBlock =
                            getLeaderBlockContext(targetBlock.getBlockHeader().getNumber() - 1)
                                .orElseThrow(MissingBlockException::new);
                        targetBlockHash = targetBlock.getBlockHeader().getBlockHash();
                      }

                      while (!persistedBlockHash.equals(targetBlockHash)) {
                        LOG.info("Reorg detected so we clean the cache");
                        blockContextProvider.clear();
                        LOG.debug("Paired Rollback {}", persistedBlockHash);
                        LOG.debug("Paired Rollforward {}", targetBlockHash);

                        rollForward.add(
                            getLeaderBlockContext(targetBlock.getBlockHeader().getNumber())
                                .orElseThrow(MissingBlockException::new));
                        final long targetBlockParent = targetBlock.getBlockHeader().getNumber() - 1;
                        targetBlock =
                            getLeaderBlockContext(targetBlockParent)
                                .orElseThrow(MissingBlockException::new);

                        rollBackward.add(
                            getLocalBlockContext(persistedBlock.getBlockHeader().getNumber())
                                .orElseThrow(MissingBlockException::new));
                        persistedBlock =
                            getLocalBlockContext(persistedBlock.getBlockHeader().getNumber() - 1)
                                .orElseThrow();

                        targetBlockHash = targetBlock.getBlockHeader().getBlockHash();
                        persistedBlockHash = persistedBlock.getBlockHeader().getBlockHash();
                      }

                      for (BlockContextProvider.FleetBlockContext blockContext : rollBackward) {
                        LOG.debug(
                            "Attempting RollBack of {}",
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

                      for (BlockContextProvider.FleetBlockContext blockContext : rollForward) {
                        LOG.debug(
                            "Attempting RollForward of {}",
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
                        LOG.debug("head not changed " + chainHead.getBlockHash());
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

                    } while (!chainHead.getBlockHash().equals(this.leaderHeader.getBlockHash()));
                  } catch (MissingBlockException e) {
                    syncDelay *= retryFactor;
                    startSync();
                    LOG.trace("There's missing block in the leader, retry after {} ms", syncDelay);
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
        LOG.debug("Disable P2P discovery");
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
    if (body.getDeposits().isPresent()) {
      message.append(" / %d ds");
      messageArgs.add(body.getDeposits().get().size());
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

  private long calculateRangeLimit(final long min, final long max) {
    if ((max - min) > (long) maxBlocksPerPersist) {
      return min + (long) maxBlocksPerPersist;
    }
    return max;
  }

  private boolean isBlockchainServiceReady() {
    return pluginServiceProvider.isServiceAvailable(BlockchainService.class);
  }

  private Optional<BlockContextProvider.FleetBlockContext> getLeaderBlockContext(
      final long blockNumber) {
    return blockContextProvider.getLeaderBlockContextByNumber(
        blockNumber, (leaderHeader.getNumber() - blockNumber) <= headDistanceForReceiptFetch);
  }

  private Optional<BlockContextProvider.FleetBlockContext> getLocalBlockContext(
      final long blockNumber) {
    return blockContextProvider.getLocalBlockContextByNumber(blockNumber, false);
  }
}
