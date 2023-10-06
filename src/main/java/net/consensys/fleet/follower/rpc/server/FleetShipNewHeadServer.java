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
package net.consensys.fleet.follower.rpc.server;

import net.consensys.fleet.common.plugin.PluginServiceProvider;
import net.consensys.fleet.common.rpc.server.PluginRpcMethod;
import net.consensys.fleet.leader.event.NewHeadObserver;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.plugin.data.BlockHeader;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FleetShipNewHeadServer implements PluginRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(FleetShipNewHeadServer.class);

  private final NewHeadObserver newHeadObserver;
  private final PluginServiceProvider pluginServiceProvider;

  public FleetShipNewHeadServer(
      final NewHeadObserver newHeadObserver, final PluginServiceProvider pluginServiceProvider) {
    this.newHeadObserver = newHeadObserver;
    this.pluginServiceProvider = pluginServiceProvider;
  }

  @Override
  public String getNamespace() {
    return "fleet";
  }

  @Override
  public String getName() {
    return "shipNewHead";
  }

  @Override
  public Object execute(PluginRpcRequest rpcRequest) {
    LOG.debug("execute {} request with body {}", getName(), rpcRequest.getParams());
    if (rpcRequest.getParams().length > 0 && isRlpConverterReady()) {
      final RlpConverterService rlpConverterService =
          pluginServiceProvider.getService(RlpConverterService.class);
      final BlockHeader blockHeader =
          rlpConverterService.buildHeaderFromRlp(
              Bytes.fromHexString(rpcRequest.getParams()[0].toString()));
      LOG.debug("receive new head {}({})", blockHeader.getNumber(), blockHeader.getBlockHash());
      newHeadObserver.onNewHead(blockHeader);
    }
    return null;
  }

  private boolean isRlpConverterReady() {
    return pluginServiceProvider.isServiceAvailable(RlpConverterService.class);
  }
}
