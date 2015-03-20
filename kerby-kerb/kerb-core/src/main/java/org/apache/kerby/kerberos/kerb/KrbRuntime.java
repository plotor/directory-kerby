/**
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.apache.kerby.kerberos.kerb;

/**
 * This runtime allows hook external dependencies thru ServiceProvider interface.
 * The hook behavior should be done at the very initial time during startup.
 */
public class KrbRuntime {

    private static TokenProvider tokenProvider;

    /**
     * Set up token provider, should be done at very initial time
     * @return token provider
     */
    public synchronized static TokenProvider getTokenProvider() {
        if (tokenProvider == null) {
            throw new RuntimeException("No token provider is hooked into yet");
        }
        return tokenProvider;
    }

    /**
     * Get token provider.
     * @param tokenProvider
     */
    public static void setTokenProvider(TokenProvider tokenProvider) {
        KrbRuntime.tokenProvider = tokenProvider;
    }
}
