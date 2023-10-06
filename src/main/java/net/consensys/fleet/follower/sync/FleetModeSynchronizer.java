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
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
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

  private static final int MAX_BLOCKS_TO_IMPORT_AT_ONCE = 500;

  private final AtomicBoolean isWaiting = new AtomicBoolean(true);
  private final AtomicBoolean isRunning = new AtomicBoolean();

  private final PluginServiceProvider pluginServiceProvider;
  private final BlockContextProvider blockContextProvider;

  private BlockHeader leaderHeader;

  public FleetModeSynchronizer(
      final PluginServiceProvider pluginServiceProvider,
      final BlockContextProvider blockContextProvider) {
    this.pluginServiceProvider = pluginServiceProvider;
    this.blockContextProvider = blockContextProvider;
  }

  public void syncNewHead(final BlockHeader newLeaderHead) {
    this.leaderHeader = newLeaderHead;

    if (isWaiting.get()) {
      LOG.debug("Waiting for the end of the initial synchronization phase");
    } else if (isBlockchainServiceReady() && !isRunning.getAndSet(true)) {
      final BlockchainService blockchainService =
          pluginServiceProvider.getService(BlockchainService.class);
      final TrieLogProvider trieLogProvider =
          pluginServiceProvider.getService(TrieLogService.class).getTrieLogProvider();
      BlockHeader chainHead = blockchainService.getChainHead();
      try {
        do {

          final List<BlockContextProvider.FleetBlockContext> rollBackward = new ArrayList<>();
          final List<BlockContextProvider.FleetBlockContext> rollForward = new ArrayList<>();

          BlockContext persistedBlock = getLocalBlockContext(chainHead.getNumber()).orElseThrow();
          Hash persistedBlockHash = persistedBlock.getBlockHeader().getBlockHash();

          final long targetBlockNumber =
              calculateRangeLimit(chainHead.getNumber(), this.leaderHeader.getNumber());
          BlockContext targetBlock = getLeaderBlockContext(targetBlockNumber).orElseThrow();
          Hash targetBlockHash = targetBlock.getBlockHeader().getBlockHash();

          LOG.debug(
              "New head (or leader block) being detected. {} ({})",
              targetBlock.getBlockHeader().getNumber(),
              targetBlock.getBlockHeader().getBlockHash());
          LOG.debug(
              "Detected local chain head {} ({}", chainHead.getNumber(), chainHead.getBlockHash());

          while (persistedBlock.getBlockHeader().getNumber()
              > targetBlock.getBlockHeader().getNumber()) {
            LOG.debug("Rollback {}", persistedBlockHash);
            rollBackward.add(
                getLocalBlockContext(persistedBlock.getBlockHeader().getNumber()).orElseThrow());
            persistedBlock =
                getLocalBlockContext(persistedBlock.getBlockHeader().getNumber() - 1).orElseThrow();
            persistedBlockHash = persistedBlock.getBlockHeader().getBlockHash();
          }

          while (persistedBlock.getBlockHeader().getNumber()
              < targetBlock.getBlockHeader().getNumber()) {
            LOG.debug("Rollforward {}", targetBlockHash);
            rollForward.add(
                getLeaderBlockContext(targetBlock.getBlockHeader().getNumber()).orElseThrow());
            targetBlock =
                getLeaderBlockContext(targetBlock.getBlockHeader().getNumber() - 1).orElseThrow();
            targetBlockHash = targetBlock.getBlockHeader().getBlockHash();
          }

          while (!persistedBlockHash.equals(targetBlockHash)) {
            LOG.debug("Paired Rollback {}", persistedBlockHash);
            LOG.debug("Paired Rollforward {}", targetBlockHash);
            rollForward.add(
                getLeaderBlockContext(targetBlock.getBlockHeader().getNumber()).orElseThrow());
            targetBlock =
                getLeaderBlockContext(targetBlock.getBlockHeader().getNumber() - 1).orElseThrow();

            rollBackward.add(
                getLocalBlockContext(persistedBlock.getBlockHeader().getNumber()).orElseThrow());
            persistedBlock =
                getLocalBlockContext(persistedBlock.getBlockHeader().getNumber() - 1).orElseThrow();

            targetBlockHash = targetBlock.getBlockHeader().getBlockHash();
            persistedBlockHash = persistedBlock.getBlockHeader().getBlockHash();
          }

          final SynchronizationService synchronizationService =
              pluginServiceProvider.getService(SynchronizationService.class);

          for (BlockContextProvider.FleetBlockContext blockContext : rollBackward) {
            LOG.debug("Attempting RollBack of {}", blockContext.getBlockHeader().getBlockHash());
            final boolean result =
                synchronizationService.setHeadUnsafe(
                    blockContext.getBlockHeader(), blockContext.getBlockBody());
            if (!result) {
              throw new Exception(
                  "Unable to complete rollback of block %s"
                      .formatted(blockContext.getBlockHeader().getBlockHash()));
            }
          }

          rollForward.sort(Comparator.comparingLong(o -> o.getBlockHeader().getNumber()));

          for (BlockContextProvider.FleetBlockContext blockContext : rollForward) {
            LOG.debug("Attempting RollForward of {}", blockContext.getBlockHeader().getBlockHash());
            // save trielog and set head
            Optional<Bytes> maybeTrieLog = blockContext.trieLogRlp();
            if (maybeTrieLog.isPresent()) {
              trieLogProvider.saveRawTrieLogLayer(
                  blockContext.getBlockHeader().getBlockHash(), maybeTrieLog.get());
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

          final BlockContext oldHead = getLocalBlockContext(chainHead.getNumber()).orElseThrow();
          // update chain head
          chainHead = blockchainService.getChainHead();
          final BlockContext newHead = getLocalBlockContext(chainHead.getNumber()).orElseThrow();

          if (newHead.getBlockHeader().getNumber() - oldHead.getBlockHeader().getNumber() != 1) {
            LOG.info(
                "Fleet import progression: block {} ({}) reached.",
                newHead.getBlockHeader().getNumber(),
                newHead.getBlockHeader().getBlockHash());
          } else {
            LOG.info(
                String.format(
                    "Fleet Imported #%,d / %d tx / %d ws / %d ds / %d om / %,d (%01.1f%%) gas / (%s) ",
                    newHead.getBlockHeader().getNumber(),
                    newHead.getBlockBody().getTransactions().size(),
                    newHead.getBlockBody().getWithdrawals().map(List::size).orElse(0),
                    newHead.getBlockBody().getDeposits().map(List::size).orElse(0),
                    newHead.getBlockBody().getOmmers().size(),
                    newHead.getBlockHeader().getGasUsed(),
                    (newHead.getBlockHeader().getGasUsed() * 100.0)
                        / newHead.getBlockHeader().getGasLimit(),
                    newHead.getBlockHeader().getBlockHash().toHexString()));
          }
        } while (!chainHead.getBlockHash().equals(this.leaderHeader.getBlockHash()));
      } catch (Exception e) {
        LOG.error("Error during sync, retry later because of : {}", e.getMessage());
      }
      isRunning.set(false);
    }
  }

  public void disableInitialSync() {
    if (isWaiting.getAndSet(false)) {
      final SynchronizationService synchronizationService =
          pluginServiceProvider.getService(SynchronizationService.class);
      final P2PService p2PService = pluginServiceProvider.getService(P2PService.class);
      LOG.debug("Disable FullSync and P2P discovery");
      synchronizationService.stopSynchronizer();
      p2PService.disableDiscovery();
    }
  }

  private long calculateRangeLimit(final long min, final long max) {
    if ((max - min) > (long) MAX_BLOCKS_TO_IMPORT_AT_ONCE) {
      return min + (long) MAX_BLOCKS_TO_IMPORT_AT_ONCE;
    }
    return max;
  }

  private boolean isBlockchainServiceReady() {
    return pluginServiceProvider.isServiceAvailable(BlockchainService.class);
  }

  private Optional<BlockContextProvider.FleetBlockContext> getLeaderBlockContext(
      final long blockNumber) {
    return blockContextProvider.getLeaderBlockContextByNumber(blockNumber);
  }

  private Optional<BlockContextProvider.FleetBlockContext> getLocalBlockContext(
      final long blockNumber) {
    return blockContextProvider.getLocalBlockContextByNumber(blockNumber);
  }
}
