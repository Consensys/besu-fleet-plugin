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
package net.consensys.fleet.common.rpc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.hyperledger.besu.plugin.data.BlockBody;
import org.hyperledger.besu.plugin.data.BlockHeader;

public class GetBlockParams {

  @JsonProperty("blockHeader")
  private BlockHeader blockHeader;

  @JsonProperty("blockBody")
  private BlockBody blockBody;

  @JsonProperty("trieLogRlp")
  private String trieLogRlp;

  public GetBlockParams() {}

  public GetBlockParams(
      final BlockHeader blockHeader, final BlockBody blockBody, final String trieLogRlp) {
    this.blockHeader = blockHeader;
    this.blockBody = blockBody;
    this.trieLogRlp = trieLogRlp;
  }

  public BlockHeader getBlockHeader() {
    return blockHeader;
  }

  public BlockBody getBlockBody() {
    return blockBody;
  }

  public String getTrieLogRlp() {
    return trieLogRlp;
  }
}
