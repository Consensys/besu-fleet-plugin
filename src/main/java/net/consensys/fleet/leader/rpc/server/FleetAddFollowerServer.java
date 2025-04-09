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

import net.consensys.fleet.common.peer.PeerNodesManager;
import net.consensys.fleet.common.rpc.model.PeerNode;
import net.consensys.fleet.common.rpc.server.PluginRpcMethod;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hyperledger.besu.plugin.services.rpc.PluginRpcRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FleetAddFollowerServer implements PluginRpcMethod {

  private static final Logger LOG = LoggerFactory.getLogger(FleetAddFollowerServer.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final PeerNodesManager followerNodesManager;

  public FleetAddFollowerServer(final PeerNodesManager followerNodesManager) {
    this.followerNodesManager = followerNodesManager;
  }

  @Override
  public String getName() {
    return "addFollowerNode";
  }

  @Override
  public Object execute(PluginRpcRequest rpcRequest) {
    LOG.info("execute {} request with body {}", getName(), rpcRequest.getParams());
    try {
      final PeerNode peerNode =
          OBJECT_MAPPER.readValue(rpcRequest.getParams()[0].toString(), PeerNode.class);
      followerNodesManager.register(peerNode);
    } catch (JsonProcessingException e) {
      LOG.info("Ignore invalid request for method {} with {}", getName(), rpcRequest.getParams());
    }
    return null;
  }
}
