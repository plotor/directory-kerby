package org.apache.kerby.kerberos.tool.kinit;

import org.junit.Test;

/**
 * Kinit command test.
 *
 * @author zhenchao.wang 2022-05-28 15:51
 * @version 1.0.0
 */
public class KinitToolTest {
    
    /*
    Usage: sh bin/kinit.sh <-conf conf_dir> [-V] [-l lifetime] [-s start_time]
        [-r renewable_life] [-f | -F] [-p | -P] -n [-a | -A] [-C] [-E]
        [-v] [-R] [-k [-i|-t keytab_file]] [-c cachename]
        [-S service_name] [-T ticket_armor_cache]
        [-X <attribute>[=<value>]] <principal>

    DESCRIPTION:
        kinit obtains and caches an initial ticket-granting ticket for principal.

    OPTIONS:
        -V verbose
        -l lifetime
        -s start time
        -r renewable lifetime
        -f forwardable
        -F not forwardable
        -p proxiable
        -P not proxiable
        -n anonymous
        -a include addresses
        -A do not include addresses
        -v validate
        -R renew
        -C canonicalize
        -E client is enterprise principal name
        -k use keytab
        -i use default client keytab (with -k)
        -t filename of keytab to use
        -c Kerberos 5 cache name
        -S service
        -T armor credential cache
        -X <attribute>[=<value>]
     */

    @Test
    public void printUsageTest() throws Exception {
        System.out.println(KinitTool.USAGE);
    }

    @Test
    public void kinitTest() throws Exception {
        String[] args = {

        };

        KinitTool.main(args);
    }
}