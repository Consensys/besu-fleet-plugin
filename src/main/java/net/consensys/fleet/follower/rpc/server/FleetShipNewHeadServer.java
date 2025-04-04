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
import net.consensys.fleet.common.rpc.json.ConvertMapperProvider;
import net.consensys.fleet.common.rpc.model.NewHeadParams;
import net.consensys.fleet.common.rpc.server.PluginRpcMethod;
import net.consensys.fleet.leader.event.NewHeadObserver;

import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FleetShipNewHeadServer implements PluginRpcMethod {
  private static final Logger LOG = LoggerFactory.getLogger(FleetShipNewHeadServer.class);

  private final NewHeadObserver newHeadObserver;
  private final ConvertMapperProvider convertMapperProvider;
  private final PluginServiceProvider pluginServiceProvider;

  public FleetShipNewHeadServer(
      final NewHeadObserver newHeadObserver,
      final ConvertMapperProvider convertMapperProvider,
      final PluginServiceProvider pluginServiceProvider) {
    this.newHeadObserver = newHeadObserver;
    this.convertMapperProvider = convertMapperProvider;
    this.pluginServiceProvider = pluginServiceProvider;
  }

  @Override
  public String getName() {
    return "shipNewHead";
  }

  @Override
  public Object execute(PluginRpcRequest rpcRequest) {

    LOG.debug("execute {} request with body {}", getName(), rpcRequest.getParams());

    try {
      if (isRlpConverterReady()) {
        final NewHeadParams newHeadParams =
            convertMapperProvider
                .getJsonConverter()
                .readValue(rpcRequest.getParams()[0].toString(), NewHeadParams.class);
        LOG.info(
            "receive new head {} ({}) , safe block {} and finalized block {}",
            newHeadParams.getHead().getNumber(),
            newHeadParams.getHead().getBlockHash(),
            newHeadParams.getSafeBlock(),
            newHeadParams.getFinalizedBlock());
        newHeadObserver.onNewHead(newHeadParams);
      }
    } catch (Exception e) {
      LOG.trace("Ignore invalid request for method {} with {}", getName(), rpcRequest.getParams());
    }

    return null;
  }

  private boolean isRlpConverterReady() {
    return pluginServiceProvider.isServiceAvailable(RlpConverterService.class);
  }
}
