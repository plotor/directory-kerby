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
package org.apache.kerby.kerberos.tool.klist;

import org.apache.kerby.KOptionType;
import org.apache.kerby.KOptions;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.ccache.Credential;
import org.apache.kerby.kerberos.kerb.ccache.CredentialCache;
import org.apache.kerby.kerberos.kerb.client.KrbClient;
import org.apache.kerby.kerberos.kerb.keytab.Keytab;
import org.apache.kerby.kerberos.kerb.keytab.KeytabEntry;
import org.apache.kerby.kerberos.kerb.type.base.PrincipalName;
import org.apache.kerby.util.HexUtil;
import org.apache.kerby.util.OSUtil;
import org.apache.kerby.util.SysUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * klist like tool
 *
 * Ref. MIT klist command tool usage.
 */
public class KlistTool {
    private static final Logger LOG = LoggerFactory.getLogger(KlistTool.class);

    private static final String USAGE = (OSUtil.isWindows()
        ? "Usage: bin\\klist.cmd" : "Usage: sh bin/klist.sh")
            + " <-conf conf_dir> [-e] [-V] [[-c] [-l] [-A] [-d] [-f] [-s] "
            + "[-a [-n]]] [-k [-t] [-K]] [name]\n"
            + "\t-c specifies credentials cache\n"
            + "\t-k specifies keytab\n"
            + "\t   (Default is credentials cache)\n"
            + "\t-i uses default client keytab if no name given\n"
            + "\t-l lists credential caches in collection\n"
            + "\t-A shows content of all credential caches\n"
            + "\t-e shows the encryption type\n"
            + "\t-V shows the Kerberos version and exits\n"
            + "\toptions for credential caches:\n"
            + "\t\t-d shows the submitted authorization data types\n"
            + "\t\t-f shows credentials flags\n"
            + "\t\t-s sets exit status based on valid tgt existence\n"
            + "\t\t-a displays the address list\n"
            + "\t\t-n do not reverse-resolve\n"
            + "\toptions for keytabs:\n"
            + "\t\t-t shows keytab entry timestamps\n"
            + "\t\t-K shows keytab entry keys\n";

    // option "-k" hava a optional parameter, "/etc/krb5.keytab" if not specified
    private static String keytabFilePath = null;

    private static void printUsage(String error) {
        System.err.println(error + "\n");
        System.err.println(USAGE);
        System.exit(-1);
    }

    private static int printCredentialCacheInfo(KOptions klOptions) {
        CredentialCache cc = new CredentialCache();
        InputStream cis = null;
        String fileName;

        if (!klOptions.contains(KlistOption.CREDENTIALS_CACHE)) {
            fileName = getCcacheName(klOptions);
        } else {
            fileName = klOptions.getStringOption(KlistOption.CREDENTIALS_CACHE);
        }
        try {
            cis = Files.newInputStream(Paths.get(fileName));
            cc.load(cis);
        } catch (IOException e) {
            LOG.error("Failed to open CredentialCache from file: " + fileName + ". " + e.toString());
        } finally {
            try {
                if (cis != null) {
                    cis.close();
                }
            } catch (IOException e) {
                LOG.warn("Fail to close input stream. " + e);
            }
        }

        List<Credential> credentials = cc.getCredentials();

        System.out.println("Ticket cache: " + fileName);
        if (cc.getPrimaryPrincipal() != null) {
            System.out.println("Default principal: " + cc.getPrimaryPrincipal().getName());
        }

        if (credentials.isEmpty()) {
            System.out.println("No credential has been cached.");
        } else {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            System.out.println("Valid starting\t\tExpires\t\t\tService principal");

            for (Credential crd : credentials) {
                System.out.println(df.format(crd.getStartTime().getTime()) + "\t"
                    + df.format(crd.getEndTime().getTime()) + "\t"
                    + crd.getServerName() + "\n"
                    + "\t" + "renew until" + "\t" + df.format(crd.getRenewTill().getTime()));
            }
        }

        return 0;
    }

    /**
     * Get credential cache file name if not specified.
     */
    private static String getCcacheName(KOptions klOptions) {
        String ccacheName;
        String ccacheNameEnv = System.getenv("KRB5CCNAME");
        String ccacheNameConf = null;

        File confDir = new File("/etc");
        if (klOptions.contains(KlistOption.CONF_DIR)) {
            confDir = klOptions.getDirOption(KlistOption.CONF_DIR);
        }

        try {
            KrbClient krbClient = new KrbClient(confDir);
            ccacheNameConf = krbClient.getSetting().getKrbConfig().getString("default_ccache_name");
        } catch (KrbException e) {
            System.err.println("Create krbClient failed: " + e.getMessage());
            System.exit(1);
        }
        if (ccacheNameEnv != null) {
            ccacheName = ccacheNameEnv;
        } else if (ccacheNameConf != null) {
            ccacheName = ccacheNameConf;
        } else {
            StringBuilder uid = new StringBuilder();
            try {
                //Get UID through "id -u" command
                String command = "id -u";
                Process child = Runtime.getRuntime().exec(command);
                InputStream in = child.getInputStream();
                int c;
                while ((c = in.read()) != -1) {
                    uid.append((char) c);
                }
                in.close();
            } catch (IOException e) {
                System.err.println("Failed to get UID.");
                System.exit(1);
            }
            ccacheName = "krb5cc_" + uid.toString().trim();
            ccacheName = SysUtil.getTempDir().toString() + "/" + ccacheName;
        }

        return ccacheName;
    }

    private static int printKeytabInfo(KOptions klOptions) {
        String[] header = new String[4];
        header[0] = "KVNO Principal\n"
                + "---- --------------------------------------------------------------------------";
        header[1] = header[0];
        header[2] = "KVNO Timestamp           Principal\n"
                + "---- ------------------- ------------------------------------------------------";
        header[3] = header[2];
        int outputIndex = 0;
        if (klOptions.contains(KlistOption.SHOW_KTAB_ENTRY_TS)) {
            outputIndex |= 2;
        }
        if (klOptions.contains(KlistOption.SHOW_KTAB_ENTRY_KEY)) {
            outputIndex |= 1;
        }
        System.out.println("Keytab name: FILE:" + keytabFilePath);
        try {
            File keytabFile = new File(keytabFilePath);
            if (!keytabFile.exists()) {
                System.out.println("klist: Key table file '" + keytabFilePath + "' not found. ");
                return 0;
            }
            System.out.println(header[outputIndex]);
            SimpleDateFormat format = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            Keytab keytab = Keytab.loadKeytab(keytabFile);
            List<PrincipalName> principals = keytab.getPrincipals();
            for (PrincipalName principal : principals) {
                List<KeytabEntry> keytabEntries = keytab.getKeytabEntries(principal);
                for (KeytabEntry entry : keytabEntries) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("%-4d ", entry.getKvno()));
                    if ((outputIndex & 2) != 0) {
                        Date date = new Date(entry.getTimestamp().getTime());
                        sb.append(format.format(date));
                        sb.append(' ');
                    }
                    sb.append(String.format("%s ", principal.getName()));
                    if ((outputIndex & 1) != 0) {
                        sb.append("(0x");
                        sb.append(HexUtil.bytesToHex(entry.getKey().getKeyData()));
                        sb.append(")");
                    }
                    System.out.println(sb);
                }
            }

        } catch (IOException e) {
            System.err.println("klist: Error while scan key table file '" + keytabFilePath + "'");
        }
        return 0;
    }

    private static int printInfo(KOptions klOptions) {
        if (klOptions.contains(KlistOption.KEYTAB)) {
            return printKeytabInfo(klOptions);
        }
        return printCredentialCacheInfo(klOptions);
    }

    public static void main(String[] args) throws Exception {
        KOptions klOptions = new KOptions();
        KlistOption klopt;
        // String name = null;

        int i = 0;
        String opt, value, error;
        while (i < args.length) {
            error = null;
            opt = args[i++];

            if (opt.startsWith("-")) {
                klopt = KlistOption.fromName(opt);
                if (klopt == KlistOption.NONE) {
                    error = "Invalid option:" + opt;
                }
            } else {
                if (keytabFilePath == null && klOptions.contains(KlistOption.KEYTAB)) {
                    keytabFilePath = opt;
                }
                break;
            }

            if (error == null && klopt.getOptionInfo().getType() != KOptionType.NOV) {
                //needs value for this parameter
                value = null;
                if (i < args.length) {
                    value = args[i++];
                }
                if (value != null) {
                    KOptions.parseSetValue(klopt.getOptionInfo(), value);
                } else {
                    error = "Option" + klopt + "requires a following value";
                }
            }

            if (error != null) {
                printUsage(error);
            }

            klOptions.add(klopt);
            if (klOptions.contains(KlistOption.KEYTAB)
                && klOptions.contains(KlistOption.CREDENTIALS_CACHE)) {
                error = "Can not use '-c' and '-k' at the same time ";
                printUsage(error);
            }
        }

        if (keytabFilePath == null) {
            keytabFilePath = "/etc/krb5.keytab";
        }

        int errNo = KlistTool.printInfo(klOptions);
        System.exit(errNo);
    }
}
