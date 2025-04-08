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
package net.consensys.fleet.leader.event;

import net.consensys.fleet.common.plugin.PluginServiceProvider;
import net.consensys.fleet.common.rpc.model.NewHeadParams;
import net.consensys.fleet.leader.rpc.client.FleetShipNewHeadClient;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.AddedBlockContext;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.services.BesuEvents;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.TrieLogService;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockAddedObserver implements BesuEvents.BlockAddedListener {

  private final PluginServiceProvider pluginServiceProvider;
  private final FleetShipNewHeadClient stateShipNewHeadSender;
  private static final Logger LOG = LoggerFactory.getLogger(BlockAddedObserver.class);

  public BlockAddedObserver(
      final PluginServiceProvider pluginServiceProvider,
      final FleetShipNewHeadClient stateShipNewHeadSender) {
    this.pluginServiceProvider = pluginServiceProvider;
    this.stateShipNewHeadSender = stateShipNewHeadSender;
  }

  @Override
  public void onBlockAdded(final AddedBlockContext addedBlockContext) {
    LOG.atDebug()
        .setMessage("New block added: {}")
        .addArgument(() -> addedBlockContext.getBlockHeader().getBlockHash())
        .log();
    if (pluginServiceProvider.isServiceAvailable(BlockchainService.class)) {
      stateShipNewHeadSender.sendData(buildNewHeadEvent(addedBlockContext));
    } else {
      LOG.error("BlockchainService is not available");
    }
  }

  private NewHeadParams buildNewHeadEvent(final AddedBlockContext headBlockContext) {
    final BlockchainService service = pluginServiceProvider.getService(BlockchainService.class);
    final Hash safeBlock =
        service.getSafeBlock().orElse(headBlockContext.getBlockHeader().getBlockHash());
    final Hash finalizedBlock =
        service.getFinalizedBlock().orElse(headBlockContext.getBlockHeader().getBlockHash());
    final TrieLogProvider trieLogProvider =
        pluginServiceProvider.getService(TrieLogService.class).getTrieLogProvider();
    final Optional<String> maybeTrielog =
        trieLogProvider
            .getRawTrieLogLayer(headBlockContext.getBlockHeader().getBlockHash())
            .map(Bytes::toHexString);
    if (maybeTrielog.isEmpty()) {
      return new NewHeadParams(headBlockContext.getBlockHeader(), safeBlock, finalizedBlock);
    } else {
      return new NewHeadParams(
          headBlockContext.getBlockHeader(),
          headBlockContext.getBlockBody(),
          headBlockContext.getTransactionReceipts().stream()
              .map(TransactionReceipt.class::cast)
              .toList(),
          maybeTrielog.get(),
          safeBlock,
          finalizedBlock);
    }
  }
}
