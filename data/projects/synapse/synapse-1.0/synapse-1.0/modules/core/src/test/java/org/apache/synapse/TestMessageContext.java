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

package org.apache.synapse;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.soap.SOAP11Constants;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axis2.AxisFault;
import org.apache.axis2.addressing.EndpointReference;
import org.apache.axis2.addressing.RelatesTo;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.endpoints.Endpoint;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class TestMessageContext implements MessageContext {

    private Map properties = new HashMap();

    private Map localEntries = new HashMap();

    private Stack faultStack = new Stack();

    private SynapseConfiguration synCfg = null;

    SOAPEnvelope envelope = null;

    private EndpointReference to = null;
   
    public SynapseConfiguration getConfiguration() {
        return synCfg;
    }

    public void setConfiguration(SynapseConfiguration cfg) {
        this.synCfg = cfg;
    }

    public SynapseEnvironment getEnvironment() {
        return null;
    }

    public void setEnvironment(SynapseEnvironment se) {
    }

    public Object getProperty(String key) {
        return properties.get(key);
    }

    public Object getEntry(String key) {
        Object ret = properties.get(key);
        if (ret != null) {
            return ret;
        } else if (getConfiguration() != null) {
            return getConfiguration().getEntry(key);
        } else {
            return null;
        }
    }

    public void setProperty(String key, Object value) {
        properties.put(key, value);
    }

    public Set getPropertyKeySet() {
        return properties.keySet();
    }

    public Mediator getMainSequence() {
        Object o = localEntries.get(Constants.MAIN_SEQUENCE_KEY);
        if (o != null && o instanceof Mediator) {
            return (Mediator) o;
        } else {
            Mediator main = getConfiguration().getMainSequence();
            localEntries.put(Constants.MAIN_SEQUENCE_KEY, main);
            return main;
        }
    }

    public Mediator getFaultSequence() {
        Object o = localEntries.get(Constants.FAULT_SEQUENCE_KEY);
        if (o != null && o instanceof Mediator) {
            return (Mediator) o;
        } else {
            Mediator fault = getConfiguration().getFaultSequence();
            localEntries.put(Constants.FAULT_SEQUENCE_KEY, fault);
            return fault;
        }
    }

    public Mediator getSequence(String key) {
        Object o = localEntries.get(key);
        if (o != null && o instanceof Mediator) {
            return (Mediator) o;
        } else {
            Mediator m = getConfiguration().getSequence(key);
            localEntries.put(key, m);
            return m;
        }
    }

    public Endpoint getEndpoint(String key) {
        Object o = localEntries.get(key);
        if (o != null && o instanceof Endpoint) {
            return (Endpoint) o;
        } else {
            Endpoint e = getConfiguration().getEndpoint(key);
            localEntries.put(key, e);
            return e;
        }
    }

    //---------
    public SOAPEnvelope getEnvelope() {
        if (envelope == null)
            return OMAbstractFactory.getSOAP11Factory().getDefaultEnvelope();
        else
            return envelope;
    }

    public void setEnvelope(SOAPEnvelope envelope) throws AxisFault {
        this.envelope = envelope;
    }

    public EndpointReference getFaultTo() {
        return null;
    }

    public void setFaultTo(EndpointReference reference) {
    }

    public EndpointReference getFrom() {
        return null;
    }

    public void setFrom(EndpointReference reference) {
    }

    public String getMessageID() {
        return null;
    }

    public void setMessageID(String string) {
    }

    public RelatesTo getRelatesTo() {
        return null;
    }

    public void setRelatesTo(RelatesTo[] reference) {
    }

    public EndpointReference getReplyTo() {
        return null;
    }

    public void setReplyTo(EndpointReference reference) {
    }

    public EndpointReference getTo() {
        return to;
    }

    public void setTo(EndpointReference reference) {
        to = reference;
    }

    public void setWSAAction(String actionURI) {
    }

    public String getWSAAction() {
        return null;
    }

    public String getSoapAction() {
        return null;
    }

    public void setSoapAction(String string) {
    }

    public void setWSAMessageID(String messageID) {
    }

    public String getWSAMessageID() {
        return null;
    }

    public boolean isDoingMTOM() {
        return false;
    }

    public boolean isDoingSWA() {
        return false;
    }

    public void setDoingMTOM(boolean b) {
    }

    public void setDoingSWA(boolean b) {
    }

    public boolean isDoingPOX() {
        return false;
    }

    public void setDoingPOX(boolean b) {
    }

    public boolean isSOAP11() {
        return envelope.getNamespace().getNamespaceURI().equals(
            SOAP11Constants.SOAP_ENVELOPE_NAMESPACE_URI);
    }

    public void setResponse(boolean b) {
    }

    public boolean isResponse() {
        return false;
    }

    public void setFaultResponse(boolean b) {
    }

    public boolean isFaultResponse() {
        return false;
    }

    public int getTracingState() {
        return 0;  //Todo
    }

    public void setTracingState(int tracingState) {
        //Todo
    }

    public Stack getFaultStack() {
        return faultStack;
    }

    public void pushFaultHandler(FaultHandler fault) {
        faultStack.push(fault);
    }

    public MessageContext getSynapseContext() {
        return null;
    }

    public void setSynapseContext(MessageContext env) {
    }
}
