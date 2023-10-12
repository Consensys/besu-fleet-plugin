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
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.plugin.data.BlockHeader;

public class NewHeadParams {

  @JsonProperty("newHead")
  private BlockHeader head;

  @JsonProperty("safeBlock")
  private Hash safeBlock;

  @JsonProperty("finalizedBlock")
  private Hash finalizedBlock;

  public NewHeadParams() {}

  public NewHeadParams(final BlockHeader head, final Hash safeBlock, final Hash finalizedBlock) {
    this.head = head;
    this.safeBlock = safeBlock;
    this.finalizedBlock = finalizedBlock;
  }

  public BlockHeader getHead() {
    return head;
  }

  public Hash getSafeBlock() {
    return safeBlock;
  }

  public Hash getFinalizedBlock() {
    return finalizedBlock;
  }
}
