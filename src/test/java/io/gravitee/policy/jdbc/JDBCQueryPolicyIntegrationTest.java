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
package io.gravitee.policy.jdbc;

import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.gravitee.apim.gateway.tests.sdk.utils.HttpClientUtils.extractHeaders;
import static io.gravitee.policy.jdbc.JDBCQueryPolicy.CREATED;
import static io.gravitee.policy.jdbc.JDBCQueryPolicy.ERROR_PROCESSING_JDBC_SQL_EXCEPTION;
import static io.gravitee.policy.jdbc.JDBCQueryPolicy.ERROR_PROCESSING_JDBC_SQL_SYNTAX_ERROR;
import static io.gravitee.policy.jdbc.JDBCQueryPolicy.NO_RESULTS;
import static io.gravitee.policy.jdbc.JDBCQueryPolicy.X_JDBC_HEADER;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.definition.model.v4.Api;
import io.gravitee.definition.model.v4.flow.Flow;
import io.gravitee.definition.model.v4.flow.step.Step;
import io.gravitee.gateway.reactor.ReactableApi;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.gravitee.policy.jdbc.configuration.JDBCQueryPolicyConfiguration;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * @author Brent Hunter (brent.hunter at graviteesource.com)
 * @author GraviteeSource Team
 */
class JDBCQueryPolicyIntegrationTest {

  static class TestPreparer
    extends AbstractPolicyTest<JDBCQueryPolicy, JDBCQueryPolicyConfiguration> {

    @Override
    public void configureEntrypoints(
      Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints
    ) {
      entrypoints.putIfAbsent(
        "http-proxy",
        EntrypointBuilder.build(
          "http-proxy",
          HttpProxyEntrypointConnectorFactory.class
        )
      );
    }

    @Override
    public void configureEndpoints(
      Map<String, EndpointConnectorPlugin<?, ?>> endpoints
    ) {
      endpoints.putIfAbsent(
        "http-proxy",
        EndpointBuilder.build(
          "http-proxy",
          HttpProxyEndpointConnectorFactory.class
        )
      );
    }
  }

  @Nested
  @GatewayTest
  @DeployApi(
    {
      "/apis/jdbc-api.json",
      "/apis/jdbc-api-with-EL.json",
      "/apis/jdbc-api-invalid-connection-string.json",
    }
  )
  class OnRequest extends TestPreparer {

    @Test
    @DisplayName(
      "Test 1 - With the prepared configuration in jdbc-api.json, should successfully get results"
    )
    void test1_should_successfully_get_results(HttpClient httpClient) {
      wiremock.stubFor(get("/endpoint").willReturn(ok()));

      httpClient
        .rxRequest(HttpMethod.GET, "/jdbc-api")
        .flatMap(request -> request.rxSend())
        .flatMap(response -> {
          assertThat(response.statusCode()).isEqualTo(HttpStatusCode.OK_200);
          return response.body();
        })
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertComplete()
        .assertValue(Buffer.buffer())
        .assertNoErrors();

      wiremock.verify(
        1,
        getRequestedFor(urlPathEqualTo("/endpoint")).withHeader(
          X_JDBC_HEADER,
          equalTo(CREATED)
        )
      );
    }

    /*@Test
    @DisplayName(
      "Test 2 - Should be successful with myUserId provided (to the SQL statement)"
    )
    void test2_should_successfully_with_myuserid_provided(
      HttpClient httpClient
    ) {
      wiremock.stubFor(get("/endpoint").willReturn(ok()));

      httpClient
        .rxRequest(HttpMethod.GET, "/jdbc-api")
        //.flatMap(request -> request.putHeader("myUserId", "2").rxSend())
        .flatMap(response -> {
          assertThat(response.statusCode()).isEqualTo(HttpStatusCode.OK_200);
          return response.body();
        })
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertComplete()
        .assertValue(Buffer.buffer())
        .assertNoErrors();

      wiremock.verify(
        1,
        getRequestedFor(urlPathEqualTo("/endpoint")).withHeader(
          X_JDBC_HEADER,
          equalTo(CREATED)
        )
      );
    }*/

    @Test
    @DisplayName(
      "Test 3 - Should (silently) fail (with SQL SYNTAX erorr) when no myUserId provided to the SQL statement (thereby causing an invalid SQL statement)"
    )
    void test3_should_fail_with_sql_syntax_error_when_no_myUserId_provided(
      HttpClient httpClient
    ) {
      wiremock.stubFor(get("/endpoint").willReturn(ok()));

      httpClient
        .rxRequest(HttpMethod.GET, "/jdbc-api-with-EL")
        .flatMap(request -> request.rxSend())
        .flatMap(response -> {
          assertThat(response.statusCode()).isEqualTo(HttpStatusCode.OK_200);
          return response.body();
        })
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertComplete()
        .assertValue(Buffer.buffer())
        .assertNoErrors();

      wiremock.verify(
        1,
        getRequestedFor(urlPathEqualTo("/endpoint")).withHeader(
          X_JDBC_HEADER,
          equalTo(ERROR_PROCESSING_JDBC_SQL_SYNTAX_ERROR)
        )
      );
    }

    @Test
    @DisplayName(
      "Test 4 - Should be successful with myUserId provided (to the SQL statement)"
    )
    void test4_should_successfully_with_myUserId_provided(
      HttpClient httpClient
    ) {
      wiremock.stubFor(get("/endpoint").willReturn(ok()));

      httpClient
        .rxRequest(HttpMethod.GET, "/jdbc-api-with-EL")
        .flatMap(request -> request.putHeader("myUserId", "2").rxSend())
        .flatMap(response -> {
          assertThat(response.statusCode()).isEqualTo(HttpStatusCode.OK_200);
          return response.body();
        })
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertComplete()
        .assertValue(Buffer.buffer())
        .assertNoErrors();

      wiremock.verify(
        1,
        getRequestedFor(urlPathEqualTo("/endpoint")).withHeader(
          X_JDBC_HEADER,
          equalTo(CREATED)
        )
      );
    }

    @Test
    @DisplayName(
      "Test 5 - Should be successful (but empty) with non-existent myUserId provided (to the SQL statement)"
    )
    void test5_should_successfully_but_empty_with_non_existent_myUserId_provided(
      HttpClient httpClient
    ) {
      wiremock.stubFor(get("/endpoint").willReturn(ok()));

      httpClient
        .rxRequest(HttpMethod.GET, "/jdbc-api-with-EL")
        .flatMap(request -> request.putHeader("myUserId", "666").rxSend())
        .flatMap(response -> {
          assertThat(response.statusCode()).isEqualTo(HttpStatusCode.OK_200);
          return response.body();
        })
        .test()
        .awaitDone(10, TimeUnit.SECONDS)
        .assertComplete()
        .assertValue(Buffer.buffer())
        .assertNoErrors();

      wiremock.verify(
        1,
        getRequestedFor(urlPathEqualTo("/endpoint")).withHeader(
          X_JDBC_HEADER,
          equalTo(NO_RESULTS)
        )
      );
    }

    @Test
    @DisplayName(
      "Test 6 - Should (silently) fail because the supplied server name is bad in the JDBC Connection String (therefore causing a SQL Exception)"
    )
    void test6_should_fail_because_the_supplied_server_name_is_bad_in_the_JDBC_Connection_String(
      HttpClient httpClient
    ) {
      wiremock.stubFor(get("/endpoint").willReturn(ok()));

      httpClient
        .rxRequest(HttpMethod.GET, "/jdbc-api-invalid-connection-string")
        .flatMap(request -> request.putHeader("myUserId", "2").rxSend())
        .flatMap(response -> {
          assertThat(response.statusCode()).isEqualTo(HttpStatusCode.OK_200);
          return response.body();
        })
        .test()
        .awaitDone(30, TimeUnit.SECONDS)
        .assertComplete()
        .assertValue(Buffer.buffer())
        .assertNoErrors();

      wiremock.verify(
        1,
        getRequestedFor(urlPathEqualTo("/endpoint")).withHeader(
          X_JDBC_HEADER,
          containing("SQL Exception")
        )
      );
    }
  }
}
