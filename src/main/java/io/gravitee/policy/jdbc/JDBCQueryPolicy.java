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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.common.http.MediaType;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.el.EvaluableRequest;
import io.gravitee.gateway.api.el.EvaluableResponse;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.api.http.HttpHeaders;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.MessageExecutionContext;
import io.gravitee.gateway.reactive.api.message.Message;
import io.gravitee.gateway.reactive.api.policy.Policy;
import io.gravitee.node.api.configuration.Configuration;
import io.gravitee.policy.jdbc.configuration.JDBCQueryPolicyConfiguration;
import io.gravitee.policy.jdbc.configuration.Variable;
import io.gravitee.policy.jdbc.el.JdbcResponse;
import io.gravitee.policy.jdbc.exceptions.JDBCForbiddenException;
import io.gravitee.policy.jdbc.exceptions.JDBCProcessingException;
import io.gravitee.policy.jdbc.exceptions.NotStringArrayException;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.functions.Function;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Brent Hunter (brent.hunter at graviteesource.com)
 * @author GraviteeSource Team
 */
@Slf4j
public class JDBCQueryPolicy implements Policy {

  public static final String X_JDBC_HEADER = "X-JDBC";
  public static final String NO_RESULTS = "no-results";
  public static final String CREATED = "success";
  public static final String ERROR_PROCESSING_JDBC = "Error processing JDBC";
  public static final String ERROR_PROCESSING_JDBC_SQL_SYNTAX_ERROR =
    "Error processing JDBC - SQL Syntax error";
  public static final String ERROR_PROCESSING_JDBC_SQL_EXCEPTION =
    "Error processing JDBC - SQL Exception";
  public static final String X_JDBC_DEBUG_HEADER = "X-JDBC-DEBUG-RESPONSE";

  private static final String TEMPLATE_VARIABLE = "jdbcResponse";

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final JDBCQueryPolicyConfiguration configuration;

  @Override
  public String id() {
    return "gravitee-policy-jdbc-query";
  }

  public JDBCQueryPolicy(JDBCQueryPolicyConfiguration configuration) {
    this.configuration = configuration;
  }

  @Override
  public Completable onRequest(HttpExecutionContext ctx) {
    return Completable.defer(() -> {
      executeJdbcAndSQLStatement(ctx.request().headers(), ctx);
      return Completable.complete();
    });
  }

  @Override
  public Completable onResponse(HttpExecutionContext ctx) {
    return Completable.defer(() -> {
      executeJdbcAndSQLStatement(ctx.response().headers(), ctx);
      return Completable.complete();
    });
  }

  // TODO:  Support V4-Message APIs ???
  /*
  @Override
  public Completable onMessageRequest(MessageExecutionContext ctx) {
    return ctx
      .request()
      .onMessage(message -> executeJdbcAndSQLStatement(ctx.request().headers(), ctx));
  }

  @Override
  public Completable onMessageResponse(MessageExecutionContext ctx) {
    return ctx
      .response()
      .onMessage(message -> executeJdbcAndSQLStatement(ctx.response().headers(), ctx));
  }
  */
  // TODO: End

  /**
   * Execute the supplied JDBC Connection and SQL Statement and return the results into a jdbcResponse object.
   * @param headers - the request or response headers
   * @param ctx - the HttpExecutionContext to get/set headers
   * @return a Maybe.empty() if no response from the SQL statement, a Maybe.just(jdbcResponse) if there are records/results.
   * @throws IOException or RuntimeException that will be managed by the caller.
   */
  private Maybe<Buffer> executeJdbcAndSQLStatement(
    HttpHeaders headers,
    HttpExecutionContext ctx
  ) throws IOException {
    // Perform the JDBC Connection and SQL Query

    TemplateEngine tplEngine = ctx.getTemplateEngine();

    // Create a new ArrayList to hold the results of the RecordSet results
    ArrayList<Map<String, String>> content = new ArrayList<>();

    try {
      log.info("JDBC Query Policy: About to start JDBC connection....");
      // TODO:  Need to discover why jdbc driver is not auto-detecting
      try {
        /*
        if (
          configuration.getJdbcConnectionString().indexOf("snowflake://") > 1
        ) {
          Class.forName("net.snowflake.client.jdbc.SnowflakeDriver");
        }
        */
        if (
          configuration.getJdbcConnectionString().indexOf("postgresql://") > 1
        ) {
          Class.forName("org.postgresql.Driver");
        }
        if (configuration.getJdbcConnectionString().indexOf("mysql://") > 1) {
          Class.forName("com.mysql.cj.jdbc.Driver");
        }
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
      // TODO: end

      log.debug(
        "JDBC Query Policy:  Attempting to connect to remote JDBC database service..."
      );

      /*Connection connection = DriverManager.getConnection(
        configuration.getJdbcConnectionString(),
        configuration.getJdbcConnectionUsername(),
        configuration.getJdbcConnectionPassword()
      );*/
      Connection connection = DriverManager.getConnection(
        tplEngine.getValue(
          configuration.getJdbcConnectionString(),
          String.class
        ),
        tplEngine.getValue(
          configuration.getJdbcConnectionUsername(),
          String.class
        ),
        tplEngine.getValue(
          configuration.getJdbcConnectionPassword(),
          String.class
        )
      );
      log.debug(
        "JDBC Query Policy:  Successfully connected (to remote JDBC database service)."
      );

      log.debug(
        "JDBC Query Policy:  SQLQuery (before EL): {}",
        configuration.getJdbcQuery()
      );
      // Get the jdbcQuery configuration value. (Supports EL)
      String jdbcQuery = tplEngine.getValue(
        configuration.getJdbcQuery(),
        String.class
      );
      log.info("JDBC Query Policy:  SQLQuery (after EL): {}", jdbcQuery);

      log.debug("JDBC Query Policy:  Executing JDBC query...");
      Statement statement = connection.createStatement();
      ResultSet res = statement.executeQuery(jdbcQuery);
      // Create ResultSetMetaData object so we can also get Field/Column Names (to insert into JdbcResponse.content)
      ResultSetMetaData rsmd = res.getMetaData();

      if (isMyResultSetEmpty(res)) {
        // The JDBC Query produced no results, so return an empty buffer and add the (configurable) header.
        log.warn(
          "JDBC Query Policy: ** Received empty JDBC Response, so exiting **"
        );
        if (configuration.getJdbcHeaders() == true) {
          headers.set(X_JDBC_HEADER, NO_RESULTS);
        }
        return Maybe.empty();
      }

      while (res.next()) {
        log.debug("JDBC Query Policy:  Looping through results...");
        Map<String, String> thisMap = new HashMap<>();
        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
          thisMap.put(
            rsmd.getColumnName(i),
            res.getString(rsmd.getColumnName(i))
          );
        }
        content.add(thisMap);
      }
    } catch (SQLSyntaxErrorException e) {
      // The JDBC Query produced an Exception, so return an empty buffer and add the (configurable) headers.
      log.error(
        "JDBC Query Policy: SQLSyntaxErrorException: {}",
        e.getMessage()
      );
      if (configuration.getJdbcHeaders() == true) {
        headers.set(X_JDBC_HEADER, ERROR_PROCESSING_JDBC_SQL_SYNTAX_ERROR);
      }
      if (configuration.getJdbcDebugHeaders() == true) {
        headers.set(
          X_JDBC_DEBUG_HEADER,
          e.toString().replace("\n", " ").replace("\t", " ")
        );
      }
      return Maybe.empty();
    } catch (SQLException e) {
      // The JDBC Query produced an Exception, so return an empty buffer and add the (configurable) headers.
      log.error("JDBC Query Policy: SQLException: {}", e.getMessage());
      if (configuration.getJdbcHeaders() == true) {
        headers.set(X_JDBC_HEADER, ERROR_PROCESSING_JDBC_SQL_EXCEPTION);
      }
      if (configuration.getJdbcDebugHeaders() == true) {
        headers.set(
          X_JDBC_DEBUG_HEADER,
          e.toString().replace("\n", " ").replace("\t", " ")
        );
      }
      return Maybe.empty();
    }

    log.debug("JDBC Query Policy:  Final content - {}", content);
    log.info("JDBC Query Policy: Finished with JDBC database service.");

    final Buffer JdbcObject = createJdbcResponseObject(content, headers);
    log.debug("JDBC Query Policy:  test JdbcObject :  {}", JdbcObject);

    // Put response into template variable (=jdbcResponse) for EL (eg: defining Variables/Context Attributes)
    final JdbcResponse jdbcResponse = new JdbcResponse(CREATED, content);
    tplEngine.getTemplateContext().setVariable(TEMPLATE_VARIABLE, jdbcResponse);

    // Set context variables
    if (configuration.getVariables() != null) {
      configuration
        .getVariables()
        .forEach(variable -> {
          try {
            String extValue = (variable.getValue() != null)
              ? tplEngine.getValue(variable.getValue(), String.class)
              : null;
            ctx.setAttribute(variable.getName(), extValue);
          } catch (Exception ex) {
            // Do nothing
            log.warn(
              "JDBC Query Policy:  -> Failed to set Variable '{}'!",
              variable.getName()
            );
          }
        });
    }

    // The JDBC Query successfully retrieved results, so return a buffer with the results and add the (configurable) headers.
    return Maybe.just(JdbcObject);
  }

  /**
   * Determines if the received JDBC SQL Query is empty (i.e.: contains no records)
   * @param ResultSet rs
   * @return true if records exist, or false if no records
   */
  public static boolean isMyResultSetEmpty(ResultSet rs) throws SQLException {
    return (!rs.isBeforeFirst() && rs.getRow() == 0);
  }

  /**
   * Build a JDBC Response Buffer from the record(s) received from the SQL query
   * @param jsonMap
   * @param headers
   * @return a JDBC Response in a buffer
   */
  private Buffer createJdbcResponseObject(
    ArrayList<Map<String, String>> jsonMap,
    HttpHeaders headers
  ) {
    log.debug("JDBC Query Policy: In builder...");
    log.debug("JDBC Query Policy:  jsonMap = {}", jsonMap);

    try {
      final String jsonJDBC = MAPPER.writeValueAsString(jsonMap);
      log.debug("JDBC Query Policy:  jsonJDBC: {}", jsonJDBC);

      if (configuration.getJdbcHeaders() == true) {
        headers.set(X_JDBC_HEADER, CREATED);
      }
      if (configuration.getJdbcDebugHeaders() == true) {
        headers.set(X_JDBC_DEBUG_HEADER, jsonMap.toString());
      }
      return Buffer.buffer(jsonJDBC);
    } catch (JsonProcessingException e) {
      throw new JDBCProcessingException(ERROR_PROCESSING_JDBC);
    }
  }
}
