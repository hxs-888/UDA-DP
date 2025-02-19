
/* ====================================================================
   Copyright 2002-2004   Apache Software Foundation

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
==================================================================== */
        

package org.apache.poi.hssf.util;

import junit.framework.TestCase;

/**
 * Tests the RKUtil class.
 */
public class TestRKUtil
        extends TestCase
{
    public TestRKUtil(String s)
    {
        super(s);
    }

    /**
     * Check we can decode correctly.
     */
    public void testDecode()
            throws Exception
    {
        assertEquals(3.0, RKUtil.decodeNumber(1074266112), 0.0000001);
        assertEquals(3.3, RKUtil.decodeNumber(1081384961), 0.0000001);
        assertEquals(3.33, RKUtil.decodeNumber(1081397249), 0.0000001);
    }
}
