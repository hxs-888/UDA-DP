/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.synapse.mediators.validate;

import org.apache.synapse.mediators.AbstractTestCase;
import org.apache.synapse.config.xml.ValidateMediatorFactory;
import org.apache.synapse.config.xml.ValidateMediatorSerializer;

public class ValidateMediatorSerializationTest extends AbstractTestCase {

    private ValidateMediatorFactory validateMediatorFactory = null;
    private ValidateMediatorSerializer validateMediatorSerializer = null;

    public ValidateMediatorSerializationTest() {
        validateMediatorFactory = new ValidateMediatorFactory();
        validateMediatorSerializer = new ValidateMediatorSerializer();
    }

    public void testValidateMediatorSerialization() {

        String validateConfiguration = "<syn:validate xmlns:syn=\"http://ws.apache.org/ns/synapse\" source=\"//regRequest\">" +
                "<syn:schema key=\"file:synapse_repository/conf/sample/validate.xsd\"/>" +
                "<syn:on-fail>" +
                "<syn:drop/>" +
                "</syn:on-fail>" +
                "</syn:validate>";

        try {
            assertTrue(serialization(validateConfiguration, validateMediatorFactory, validateMediatorSerializer));
        } catch (Exception e) {
            fail("Exception in test.");
        }
    }

}

