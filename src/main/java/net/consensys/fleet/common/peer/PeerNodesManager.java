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
package net.consensys.fleet.common.peer;

import static java.nio.charset.StandardCharsets.UTF_8;

import net.consensys.fleet.common.rpc.model.PeerNode;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.logging.log4j.util.Strings;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Hash;

public class PeerNodesManager {

  private Cache<Hash, PeerNode> peerNodesCache;

  public PeerNodesManager() {}

  public void createCache(final boolean withExpireAfterWrite) {
    final CacheBuilder<Object, Object> builder = CacheBuilder.newBuilder().maximumSize(1000);
    if (withExpireAfterWrite) {
      builder.expireAfterWrite(2, TimeUnit.MINUTES);
    }
    peerNodesCache = builder.build();
  }

  public synchronized Hash register(final PeerNode peerNode) {
    final Hash id = getPeerNodeId(peerNode);
    peerNodesCache.put(id, peerNode);
    return id;
  }

  public static Hash getPeerNodeId(final PeerNode peerNode) {
    return Hash.hash(
        Bytes.of(
            Strings.concat(peerNode.host(), Integer.toString(peerNode.port())).getBytes(UTF_8)));
  }

  public List<PeerNode> getPeers() {
    return new HashMap<>(peerNodesCache.asMap()).values().stream().toList();
  }
}
