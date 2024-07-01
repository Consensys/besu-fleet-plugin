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
package net.consensys.fleet.leader.rpc.client;

import net.consensys.fleet.common.rpc.client.AbstractStateRpcSender;
import net.consensys.fleet.common.rpc.client.WebClientWrapper;
import net.consensys.fleet.common.rpc.model.NewHeadParams;

import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FleetShipNewHeadClient extends AbstractStateRpcSender<NewHeadParams, Boolean> {

  private static final String METHOD_NAME = "fleet_shipNewHead";
  private static final Logger LOG = LoggerFactory.getLogger(FleetShipNewHeadClient.class);

  public FleetShipNewHeadClient(final WebClientWrapper webClient) {
    super(webClient);
  }

  @Override
  protected String getMethodeName() {
    return METHOD_NAME;
  }

  @Override
  public CompletableFuture<Boolean> sendData(final NewHeadParams data) {
    LOG.debug("Sending new head to followers");
    final CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();
    try {
      webClient.sendToFollowers(ENDPOINT, getMethodeName(), data);
      completableFuture.complete(true);
    } catch (JsonProcessingException e) {
      LOG.error("Error sending new head to followers", e);
      completableFuture.complete(false);
    }
    return completableFuture;
  }
}
