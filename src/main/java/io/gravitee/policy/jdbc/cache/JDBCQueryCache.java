/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.jdbc.cache;

import io.gravitee.policy.jdbc.el.JdbcResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Cache for JDBC Query and responses.
 * This will cache both the query (key) and response (value) in request context, to avoid doing the same query twice.
 * Cache key is : SQL query.
 */
public class JDBCQueryCache {

    private final Map<Integer, JdbcResponse> cache;

    public JDBCQueryCache() {
        cache = new HashMap<>();
    }

    public boolean contains(String jdbcQuery) {
        return cache.containsKey(buildCacheKey(jdbcQuery));
    }

    //public Optional<JdbcResponse> get(String jdbcQuery) {
    //    return Optional.ofNullable(cache.get(buildCacheKey(jdbcQuery)));
    //}
    public JdbcResponse get(String jdbcQuery) {
        return cache.get(buildCacheKey(jdbcQuery));
    }

    public void put(String jdbcQuery, JdbcResponse jdbcResponse) {
        cache.put(buildCacheKey(jdbcQuery), jdbcResponse);
    }

    private Integer buildCacheKey(String jdbcQuery) {
        return Objects.hash(jdbcQuery);
    }
}
