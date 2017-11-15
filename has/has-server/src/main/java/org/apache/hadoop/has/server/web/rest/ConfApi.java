/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.has.server.web.rest;

import org.apache.hadoop.has.common.HasConfig;
import org.apache.hadoop.has.common.HasException;
import org.apache.hadoop.has.common.util.HasUtil;
import org.apache.hadoop.has.server.HasServer;
import org.apache.hadoop.has.server.web.WebServer;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.identity.backend.BackendConfig;
import org.apache.kerby.kerberos.kerb.server.KdcUtil;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * HAS configure web methods implementation.
 */
@Path("/conf")
public class ConfApi {

    @Context
    private ServletContext context;

    @Context
    private HttpServletRequest httpRequest;

    /**
     * Set HAS plugin.
     *
     * @param plugin HAS plugin name
     * @return Response
     */
    @PUT
    @Path("/setplugin")
    @Consumes({MediaType.TEXT_PLAIN})
    @Produces({MediaType.TEXT_PLAIN})
    public Response setPlugin(@QueryParam("plugin") final String plugin) {
        if (httpRequest.isSecure()) {
            final HasServer hasServer = WebServer.getHasServerFromContext(context);
            WebServer.LOG.info("Set HAS plugin...");
            try {
                Map<String, String> values = new HashMap<>();
                File hasConfFile = new File(hasServer.getConfDir(), "has-server.conf");
                HasConfig hasConfig = HasUtil.getHasConfig(hasConfFile);
                if (hasConfig != null) {
                    String defaultValue = hasConfig.getPluginName();
                    values.put(defaultValue, plugin);
                } else {
                    throw new RuntimeException("has-server.conf not found. ");
                }
                hasServer.updateConfFile("has-server.conf", values);
            } catch (IOException | HasException e) {
                throw new RuntimeException("Failed to set HAS plugin. ", e);
            }
            WebServer.LOG.info("HAS plugin set successfully.");

            return Response.status(200).entity("HAS plugin set successfully.\n").build();
        }
        return Response.status(403).entity("HTTPS required.\n").build();
    }

    /**
     * Config HAS server backend.
     *
     * @param backendType type of backend
     * @param dir         json dir
     * @param driver      mysql JDBC connector driver
     * @param url         mysql JDBC connector url
     * @param user        mysql user name
     * @param password    mysql password of user
     * @return Response
     */
    @PUT
    @Path("/configkdcbackend")
    @Consumes({MediaType.APPLICATION_JSON})
    @Produces({MediaType.TEXT_PLAIN})
    public Response configKdcBackend(
        @QueryParam("backendType") final String backendType,
        @QueryParam("dir") @DefaultValue("/tmp/has/jsonbackend") final String dir,
        @QueryParam("driver") @DefaultValue("com.mysql.jdbc.Driver") final String driver,
        @QueryParam("url") @DefaultValue("jdbc:mysql://127.0.0.1:3306/mysqlbackend") final String url,
        @QueryParam("user") @DefaultValue("root") final String user,
        @QueryParam("password") @DefaultValue("passwd") final String password) {

        if (httpRequest.isSecure()) {
            final HasServer hasServer = WebServer.getHasServerFromContext(context);
            if ("json".equals(backendType)) {
                WebServer.LOG.info("Set Json backend...");
                try {
                    Map<String, String> values = new HashMap<>();
                    values.put("_JAR_", "org.apache.kerby.kerberos.kdc.identitybackend.JsonIdentityBackend");
                    values.put("#_JSON_DIR_", "backend.json.dir = " + dir);
                    values.put("#_MYSQL_\n", "");
                    hasServer.updateConfFile("backend.conf", values);
                } catch (IOException | HasException e) {
                    throw new RuntimeException("Failed to set Json backend. ", e);
                }
                WebServer.LOG.info("Json backend set successfully.");

                return Response.status(200).entity("Json backend set successfully.\n").build();
            } else if ("mysql".equals(backendType)) {
                WebServer.LOG.info("Set MySQL backend...");
                try {
                    String mysqlConfig = "mysql_driver = " + driver + "\nmysql_url = " + url
                        + "\nmysql_user = " + user + "\nmysql_password = " + password;
                    Map<String, String> values = new HashMap<>();
                    values.put("_JAR_", "org.apache.hadoop.has.server.kdc.MySQLIdentityBackend");
                    values.put("#_JSON_DIR_\n", "");
                    values.put("#_MYSQL_", mysqlConfig);
                    hasServer.updateConfFile("backend.conf", values);
                } catch (IOException | HasException e) {
                    throw new RuntimeException("Failed to set MySQL backend. ", e);
                }
                WebServer.LOG.info("MySQL backend set successfully.");

                return Response.status(200).entity("MySQL backend set successfully.\n").build();
            } else {
                return Response.status(400).entity(backendType + " is not supported.\n").build();
            }
        }
        return Response.status(403).entity("HTTPS required.\n").build();
    }

    /**
     * Config HAS server KDC.
     * @param port KDC port to set
     * @param realm KDC realm to set
     * @param host KDC host to set
     * @return Response
     */
    @PUT
    @Path("/configkdc")
    @Consumes({MediaType.TEXT_PLAIN})
    @Produces({MediaType.TEXT_PLAIN})
    public Response configKdc(
        @QueryParam("port") final int port,
        @QueryParam("realm") final String realm,
        @QueryParam("host") final String host) {
        if (httpRequest.isSecure()) {
            final HasServer hasServer = WebServer.getHasServerFromContext(context);
            WebServer.LOG.info("Config HAS server KDC...");
            try {
                BackendConfig backendConfig = KdcUtil.getBackendConfig(hasServer.getConfDir());
                String backendJar = backendConfig.getString("kdc_identity_backend");
                if (backendJar.equals("org.apache.hadoop.has.server.kdc.MySQLIdentityBackend")) {
                    hasServer.configMySQLKdc(backendConfig, realm, port, host, hasServer);
                } else {
                    Map<String, String> values = new HashMap<>();
                    values.put("_HOST_", host);
                    values.put("_PORT_", String.valueOf(port));
                    values.put("_REALM_", realm);
                    hasServer.updateConfFile("kdc.conf", values);
                    String kdc = "\t\tkdc = " + host + ":" + port;
                    values.put("_KDCS_", kdc);
                    values.put("_UDP_LIMIT_", "4096");
                    hasServer.updateConfFile("krb5.conf", values);
                }
            } catch (IOException | HasException | KrbException e) {
                throw new RuntimeException("Failed to config HAS KDC. ", e);
            }
            WebServer.LOG.info("HAS server KDC set successfully.");
            return Response.status(200).entity("HAS server KDC set successfully.\n").build();
        }
        return Response.status(403).entity("HTTPS required.\n").build();
    }
}
