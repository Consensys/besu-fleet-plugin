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
package net.consensys.fleet.leader.rpc.server;

import net.consensys.fleet.common.plugin.PluginServiceProvider;
import net.consensys.fleet.common.rpc.json.ConvertMapperProvider;
import net.consensys.fleet.common.rpc.model.GetBlockParams;
import net.consensys.fleet.common.rpc.server.PluginRpcMethod;

import java.util.Optional;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.TrieLogService;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FleetGetBlockServer implements PluginRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(FleetAddFollowerServer.class);
  private final ConvertMapperProvider convertMapperProvider;
  private final PluginServiceProvider pluginServiceProvider;

  public FleetGetBlockServer(
      final ConvertMapperProvider convertMapperProvider,
      final PluginServiceProvider pluginServiceProvider) {
    this.convertMapperProvider = convertMapperProvider;
    this.pluginServiceProvider = pluginServiceProvider;
  }

  @Override
  public String getNamespace() {
    return "fleet";
  }

  @Override
  public String getName() {
    return "getBlock";
  }

  @Override
  public Object execute(PluginRpcRequest rpcRequest) {
    LOG.debug("execute {} request with body {}", getName(), rpcRequest.getParams());
    if (isBlockchainServiceReady() && rpcRequest.getParams().length > 0) {
      final BlockchainService blockchainService =
          pluginServiceProvider.getService(BlockchainService.class);
      final TrieLogProvider trieLogProvider =
          pluginServiceProvider.getService(TrieLogService.class).getTrieLogProvider();

      final long blockNumber = Long.decode(rpcRequest.getParams()[0].toString());
      final Optional<BlockContext> blockByNumber = blockchainService.getBlockByNumber(blockNumber);
      if (blockByNumber.isPresent()) {
        final BlockHeader blockHeader = blockByNumber.get().getBlockHeader();
        return convertMapperProvider
            .getJsonConverter()
            .valueToTree(
                new GetBlockParams(
                    blockHeader,
                    blockByNumber.get().getBlockBody(),
                    trieLogProvider
                        .getRawTrieLogLayer(blockHeader.getBlockHash())
                        .map(Bytes::toHexString)
                        .orElseThrow()));
      }
    }
    return null;
  }

  private boolean isBlockchainServiceReady() {
    return pluginServiceProvider.isServiceAvailable(BlockchainService.class);
  }
}
