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
package io.gravitee.policy.jdbc.resource;

import io.gravitee.policy.jdbc.el.JdbcResponse;
import io.gravitee.resource.cache.api.Element;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author Brent Hunter (brent.hunter at graviteesource.com)
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CacheElement implements Element {

    private final String key;
    //private final ArrayList<Map<String, String>> object;
    private final JdbcResponse object;

    //private int timeToLive = 0;

    //public CacheElement(String key, ArrayList<Map<String, String>> object) {
    public CacheElement(String key, JdbcResponse object) {
        this.key = key;
        this.object = object;
    }

    //public int getTimeToLive() {
    //    return timeToLive;
    //}

    //public void setTimeToLive(int timeToLive) {
    //    this.timeToLive = timeToLive;
    //}

    //@Override
    public Object key() {
        return key;
    }

    //@Override
    public JdbcResponse value() {
        return object;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + object + "]";
    }
    //@Override
    //public int timeToLive() {
    //    return timeToLive;
    //}
}
