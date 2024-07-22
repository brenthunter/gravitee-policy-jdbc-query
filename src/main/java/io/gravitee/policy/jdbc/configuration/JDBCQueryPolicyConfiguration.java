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
package io.gravitee.policy.jdbc.configuration;

import io.gravitee.policy.api.PolicyConfiguration;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author Brent Hunter (brent.hunter at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@NoArgsConstructor
public class JDBCQueryPolicyConfiguration implements PolicyConfiguration {

    private String jdbcConnectionString;
    private Boolean jdbcUseSSL = false;
    private String jdbcConnectionUsername;
    private String jdbcConnectionPassword;
    private String jdbcQuery;
    private List<Variable> variables = new ArrayList<>();
    private Boolean jdbcHeaders = true;
    private Boolean jdbcDebugHeaders = false;
    private String jdbcCacheResource;
}
