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
import net.consensys.fleet.common.rpc.model.GetBlockByHashRequest;
import net.consensys.fleet.common.rpc.model.GetBlockResponse;
import net.consensys.fleet.common.rpc.server.PluginRpcMethod;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.BlockContext;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.data.TransactionReceipt;
import org.hyperledger.besu.plugin.services.BlockchainService;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;
import org.hyperledger.besu.plugin.services.trielogs.TrieLogProvider;

public class FleetGetBlockByHashServer extends AbstractFleetGetBlockServer
    implements PluginRpcMethod {

  public FleetGetBlockByHashServer(
      final ConvertMapperProvider convertMapperProvider,
      final PluginServiceProvider pluginServiceProvider) {
    super(convertMapperProvider, pluginServiceProvider);
  }

  @Override
  public String getName() {
    return "getBlockByHash";
  }

  @Override
  protected Optional<GetBlockResponse> getBlock(
      final PluginRpcRequest rpcRequest,
      final BlockchainService blockchainService,
      final TrieLogProvider trieLogProvider)
      throws JsonProcessingException {
    final GetBlockByHashRequest getBlockRequest =
        OBJECT_MAPPER.readValue(rpcRequest.getParams()[0].toString(), GetBlockByHashRequest.class);
    final Hash blockHash = getBlockRequest.getBlockHash();
    final Optional<BlockContext> blockByHash = blockchainService.getBlockByHash(blockHash);
    if (blockByHash.isPresent()) {
      final BlockHeader blockHeader = blockByHash.get().getBlockHeader();
      final List<TransactionReceipt> receipts;
      if (getBlockRequest.isFetchReceipts()) {
        receipts =
            blockchainService
                .getReceiptsByBlockHash(blockHeader.getBlockHash())
                .orElse(Collections.emptyList());
      } else {
        receipts = Collections.emptyList();
      }
      return Optional.of(
          new GetBlockResponse(
              blockHeader,
              blockByHash.get().getBlockBody(),
              receipts,
              trieLogProvider
                  .getRawTrieLogLayer(blockHeader.getBlockHash())
                  .map(Bytes::toHexString)
                  .orElseThrow()));
    }
    return Optional.empty();
  }
}
