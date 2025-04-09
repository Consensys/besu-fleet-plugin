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
package net.consensys.fleet.common.rpc.client;

import net.consensys.fleet.common.peer.PeerNodesManager;
import net.consensys.fleet.common.rpc.json.ConvertMapperProvider;
import net.consensys.fleet.common.rpc.model.PeerNode;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebClientWrapper {
  private static final Logger LOG = LoggerFactory.getLogger(WebClientWrapper.class);

  private static final String JSON_RPC_VERSION = "2.0";
  private static final String CONTENT_TYPE = "application/json";
  private final WebClient webClient;
  private final ConvertMapperProvider convertMapperProvider;
  private final PeerNodesManager peerManagers;

  public WebClientWrapper(
      final ConvertMapperProvider convertMapperProvider, final PeerNodesManager peerManagers) {
    this.peerManagers = peerManagers;
    this.convertMapperProvider = convertMapperProvider;
    this.webClient = WebClient.create(Vertx.vertx(), new WebClientOptions());
  }

  public CompletableFuture<HttpResponse<Buffer>> sendToLeader(
      final String endpoint, final String methodName, final Object o)
      throws JsonProcessingException {

    final JsonObject jsonObject =
        new JsonObject()
            .put("jsonrpc", JSON_RPC_VERSION)
            .put("id", 1)
            .put("method", methodName)
            .put("params", List.of(convertMapperProvider.getJsonConverter().writeValueAsString(o)));

    final PeerNode leader = peerManagers.getPeers().get(0);

    CompletableFuture<HttpResponse<Buffer>> completableFuture = new CompletableFuture<>();

    webClient
        .post(leader.port(), leader.host(), endpoint)
        .timeout(100)
        .putHeader("Content-Type", CONTENT_TYPE)
        .sendJsonObject(
            jsonObject,
            event -> {
              if (event.failed()) {
                LOG.info("event failed {}", String.valueOf(event.cause()));
                completableFuture.completeExceptionally(event.cause());
              } else {
                completableFuture.complete(event.result());
              }
              LOG.info(
                  "Send RPC request {} result {} for body {} {}",
                  methodName,
                  event.succeeded(),
                  jsonObject.toString(),
                  event.result());
            });
    return completableFuture;
  }

  public void sendToFollowers(final String endpoint, final String methodName, final Object o)
      throws JsonProcessingException {
    final JsonObject jsonObject =
        new JsonObject()
            .put("jsonrpc", JSON_RPC_VERSION)
            .put("id", 1)
            .put("method", methodName)
            .put("params", List.of(convertMapperProvider.getJsonConverter().writeValueAsString(o)));

    peerManagers
        .getPeers()
        .forEach(
            peerNode -> {
              webClient
                  .post(peerNode.port(), peerNode.host(), endpoint)
                  .timeout(100)
                  .putHeader("Content-Type", CONTENT_TYPE)
                  .sendJsonObject(
                      jsonObject,
                      event -> {
                        LOG.info(
                            "Send RPC request {} to {} result {} for body {}",
                            methodName,
                            peerNode,
                            event.succeeded(),
                            jsonObject.toString());
                      });
            });
  }

  public <T> T decode(final HttpResponse<Buffer> response, final String key, final Class<T> type)
      throws JsonProcessingException {
    return convertMapperProvider
        .getJsonConverter()
        .readValue(response.bodyAsJsonObject().getJsonObject(key).toString(), type);
  }
}
