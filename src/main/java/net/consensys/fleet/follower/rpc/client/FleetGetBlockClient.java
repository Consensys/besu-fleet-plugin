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
import net.consensys.fleet.common.rpc.model.AbstractGetBlockRequest;
import net.consensys.fleet.common.rpc.model.GetBlockResponse;

import java.util.concurrent.CompletableFuture;

import com.fasterxml.jackson.core.JsonProcessingException;

public class FleetGetBlockClient
    extends AbstractStateRpcSender<AbstractGetBlockRequest, GetBlockResponse> {

  public FleetGetBlockClient(final WebClientWrapper webClient) {
    super(webClient);
  }

  @Override
  public CompletableFuture<GetBlockResponse> sendData(
      final AbstractGetBlockRequest getBlockRequest) {
    final CompletableFuture<GetBlockResponse> completableFuture = new CompletableFuture<>();
    try {
      webClient
          .sendToLeader(ENDPOINT, getBlockRequest.getMethodName(), getBlockRequest)
          .whenCompleteAsync(
              (result, throwable) -> {
                if (throwable == null) {
                  try {
                    completableFuture.complete(
                        webClient.decode(result, "result", GetBlockResponse.class));
                  } catch (JsonProcessingException e) {
                    completableFuture.completeExceptionally(e);
                  }
                } else {
                  completableFuture.completeExceptionally(throwable);
                }
              });
    } catch (JsonProcessingException e) {
      completableFuture.completeExceptionally(e);
    }
    return completableFuture;
  }
}
