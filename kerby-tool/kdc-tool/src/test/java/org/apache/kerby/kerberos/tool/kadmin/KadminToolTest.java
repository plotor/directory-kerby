package org.apache.kerby.kerberos.tool.kadmin;

import org.junit.Test;

/**
 * KAdmin commant test.
 *
 * @author zhenchao.wang 2022-05-28 16:30
 * @version 1.0.0
 */
public class KadminToolTest {

    /*
    Usage: sh bin/kadmin.sh <conf-dir> <-c cache_name>|<-k keytab>
        Example:
            sh bin/kadmin.sh conf -k admin.keytab
     */

    @Test
    public void printUsageTest() {
        System.out.println(KadminTool.USAGE);
    }

}