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
package net.consensys.fleet.follower.rpc.client;

import net.consensys.fleet.common.rpc.client.AbstractStateRpcSender;
import net.consensys.fleet.common.rpc.client.WebClientWrapper;
import net.consensys.fleet.common.rpc.model.PeerNode;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FleetAddFollowerClient extends AbstractStateRpcSender<PeerNode, Boolean> {

  private static final Logger LOG = LoggerFactory.getLogger(FleetAddFollowerClient.class);

  private static final String METHOD_NAME = "fleet_addFollowerNode";

  public FleetAddFollowerClient(final WebClientWrapper webClient) {
    super(webClient);
  }

  @Override
  protected String getMethodeName() {
    return METHOD_NAME;
  }

  @Override
  public CompletableFuture<Boolean> sendData(PeerNode data) {
    final CompletableFuture<Boolean> completableFuture = new CompletableFuture<>();
    try {
      webClient
          .sendToLeader(ENDPOINT, getMethodeName(), data)
          .whenComplete(
              (bufferHttpResponse, throwable) -> {
                completableFuture.complete(isConnected(bufferHttpResponse, throwable));
              });
    } catch (JsonProcessingException e) {
      completableFuture.complete(false);
    }
    return completableFuture;
  }

  private boolean isConnected(final HttpResponse<Buffer> response, final Throwable throwable) {
    if (throwable == null && response.statusCode() == 200) {
      return !response.bodyAsJsonObject().containsKey("error");
    }
    if (throwable != null) {
      StringWriter sw = new StringWriter();
      throwable.printStackTrace(new PrintWriter(sw));
      LOG.info(sw.toString());
    }
    LOG.info("is connect {}", response.statusCode());
    return false;
  }
}
