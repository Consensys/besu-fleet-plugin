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

import java.util.concurrent.CompletableFuture;

public abstract class AbstractStateRpcSender<REQUEST, RESPONSE> {
  protected static final String ENDPOINT = "/";

  protected final WebClientWrapper webClient;

  public AbstractStateRpcSender(final WebClientWrapper webClient) {
    this.webClient = webClient;
  }

  protected abstract String getMethodeName();

  public static String getENDPOINT() {
    return ENDPOINT;
  }

  public WebClientWrapper getWebClient() {
    return webClient;
  }

  public abstract CompletableFuture<RESPONSE> sendData(REQUEST data);
}
