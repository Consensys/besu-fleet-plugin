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
package net.consensys.fleet.common.rpc.json;

import net.consensys.fleet.common.plugin.PluginServiceProvider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.hyperledger.besu.plugin.services.rlp.RlpConverterService;

public class ConvertMapperProvider {

  private ObjectMapper objectMapper;
  private final PluginServiceProvider pluginServiceProvider;

  public ConvertMapperProvider(final PluginServiceProvider pluginServiceProvider) {
    this.pluginServiceProvider = pluginServiceProvider;
  }

  public ObjectMapper getJsonConverter() {
    if (objectMapper == null) {
      if (pluginServiceProvider.isServiceAvailable(RlpConverterService.class)) {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(
            new JsonModule(pluginServiceProvider.getService(RlpConverterService.class)));
      } else {
        throw new RuntimeException("object mapper not available");
      }
    }
    return objectMapper;
  }
}
