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
package org.apache.kerby.cms;

import org.apache.kerby.asn1.Asn1;
import org.apache.kerby.cms.type.ContentInfo;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;

public class TestCompressedData extends CmsTestBase {

    @Test
    public void testDump1WithCompressedData() throws IOException {
        byte[] data = readDataFile("/compressed-data.txt");
        try {
            Asn1.dump(data, true);

            ContentInfo contentInfo = new ContentInfo();
            contentInfo.decode(data);
            Asn1.dump(contentInfo);

            //To be fixed.
            /*
            CompressedData compressedData =
                contentInfo.getContentAs(CompressedData.class);
            Asn1.dump(compressedData);
            */
        } catch (Exception e) {
            e.printStackTrace();
            Assert.fail();
        }
    }
}
