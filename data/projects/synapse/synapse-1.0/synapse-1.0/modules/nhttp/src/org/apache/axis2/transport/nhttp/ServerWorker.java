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
package org.apache.axis2.transport.nhttp;

import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.engine.AxisEngine;
import org.apache.axis2.description.AxisService;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.MessageContext;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.transport.http.HTTPTransportUtils;
import org.apache.axis2.transport.http.HTTPTransportReceiver;
import org.apache.axis2.transport.RequestResponseTransport;
import org.apache.axiom.om.util.UUIDGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.*;
import org.apache.http.nio.NHttpServerConnection;
import org.apache.http.protocol.HTTP;
import org.apache.ws.commons.schema.XmlSchema;

import javax.xml.namespace.QName;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.InetAddress;

/**
 * Processes an incoming request through Axis2. An instance of this class would be created to
 * process each unique request
 */
public class ServerWorker implements Runnable {

    private static final Log log = LogFactory.getLog(ServerWorker.class);

    /** the incoming message to be processed */
    private MessageContext msgContext = null;
    /** the Axis2 configuration context */
    private ConfigurationContext cfgCtx = null;
    /** the message handler to be used */
    private ServerHandler serverHandler = null;
    /** the underlying http connection */
    private NHttpServerConnection conn = null;
    /** is this https? */
    private boolean isHttps = false;
    /** the http request */
    private HttpRequest request = null;
    /** the http response message (which the this would be creating) */
    private HttpResponse response = null;
    /** the input stream to read the incoming message body */
    private InputStream is = null;
    /** the output stream to write the response message body */
    private OutputStream os = null;
    private static final String SOAPACTION   = "SOAPAction";
    private static final String LOCATION     = "Location";
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String TEXT_HTML    = "text/html";
    private static final String TEXT_XML     = "text/xml";

    /**
     * Create a new server side worker to process an incoming message and optionally begin creating
     * its output. This however does not force the processor to write a response back as the
     * traditional servlet service() method, but creates the background required to write the
     * response, if one would be created.
     * @param cfgCtx the Axis2 configuration context
     * @param conn the underlying http connection
     * @param serverHandler the handler of the server side messages
     * @param request the http request received (might still be in the process of being streamed)
     * @param is the stream input stream to read the request body
     * @param response the response to be populated if applicable
     * @param os the output stream to write the response body if one is applicable
     */
    public ServerWorker(final ConfigurationContext cfgCtx, final NHttpServerConnection conn,
        final boolean isHttps,
        final ServerHandler serverHandler,
        final HttpRequest request, final InputStream is,
        final HttpResponse response, final OutputStream os) {

        this.cfgCtx = cfgCtx;
        this.conn = conn;
        this.isHttps = isHttps;
        this.serverHandler = serverHandler;
        this.request = request;
        this.response = response;
        this.is = is;
        this.os = os;
        this.msgContext = createMessageContext(request);
    }

    /**
     * Create an Axis2 message context for the given http request. The request may be in the
     * process of being streamed
     * @param request the http request to be used to create the corresponding Axis2 message context
     * @return the Axis2 message context created
     */
    private MessageContext createMessageContext(HttpRequest request) {

        MessageContext msgContext = new MessageContext();
        msgContext.setProperty(MessageContext.TRANSPORT_NON_BLOCKING, Boolean.TRUE);
        msgContext.setConfigurationContext(cfgCtx);
        if (isHttps) {
            msgContext.setTransportOut(cfgCtx.getAxisConfiguration()
                .getTransportOut("https"));
            msgContext.setTransportIn(cfgCtx.getAxisConfiguration()
                .getTransportIn("https"));
            msgContext.setIncomingTransportName("https");
        } else {
            msgContext.setTransportOut(cfgCtx.getAxisConfiguration()
                .getTransportOut(Constants.TRANSPORT_HTTP));
            msgContext.setTransportIn(cfgCtx.getAxisConfiguration()
                .getTransportIn(Constants.TRANSPORT_HTTP));
            msgContext.setIncomingTransportName(Constants.TRANSPORT_HTTP);
        }
        msgContext.setProperty(Constants.OUT_TRANSPORT_INFO, this);
        msgContext.setServiceGroupContextId(UUIDGenerator.getUUID());
        msgContext.setServerSide(true);
        msgContext.setProperty(
            Constants.Configuration.TRANSPORT_IN_URL, request.getRequestLine().getUri());

        Map headers = new HashMap();
        Header[] headerArr = request.getAllHeaders();
        for (int i = 0; i < headerArr.length; i++) {
            headers.put(headerArr[i].getName(), headerArr[i].getValue());
        }
        msgContext.setProperty(MessageContext.TRANSPORT_HEADERS, headers);
        // find the remote party IP address and set it to the message context
        if (conn instanceof HttpInetConnection) {
            HttpInetConnection inetConn = (HttpInetConnection) conn;
            InetAddress remoteAddr = inetConn.getRemoteAddress();
            if (remoteAddr != null) {
                msgContext.setProperty(MessageContext.REMOTE_ADDR, remoteAddr.getHostAddress());
            }
        }

        // this is required to support Sandesha 2
        msgContext.setProperty(RequestResponseTransport.TRANSPORT_CONTROL,
                new HttpCoreRequestResponseTransport(msgContext));
        return msgContext;
    }

    /**
     * Process the incoming request
     */
    public void run() {

        String method = request.getRequestLine().getMethod().toUpperCase();
        if ("GET".equals(method)) {
            processGet();
        } else if ("POST".equals(method)) {
            processPost();
        } else {
            handleException("Unsupported method : " + method, null);
        }

        if (msgContext != null && msgContext.getOperationContext() != null &&
            !Constants.VALUE_TRUE.equals(
                msgContext.getOperationContext().getProperty(Constants.RESPONSE_WRITTEN)) &&
            !"SKIP".equals(
                msgContext.getOperationContext().getProperty(Constants.RESPONSE_WRITTEN))) {

            response.setStatusCode(HttpStatus.SC_ACCEPTED);
            serverHandler.commitResponse(conn, response);

            // make sure that the output stream is flushed and closed properly
            try {
                is.close();
            } catch (IOException ignore) {}

            // make sure that the output stream is flushed and closed properly
            try {
                os.flush();
                os.close();
            } catch (IOException ignore) {}
        }
    }

    /**
     *
     */
    private void processPost() {

        try {
            Header contentType = request.getFirstHeader(HTTP.CONTENT_TYPE);
            Header soapAction  = request.getFirstHeader(SOAPACTION);

            HTTPTransportUtils.processHTTPPostRequest(
                msgContext, is,
                os,
                (contentType != null ? contentType.getValue() : null),
                (soapAction != null  ? soapAction.getValue()  : null),
                request.getRequestLine().getUri());
        } catch (AxisFault e) {
            handleException("Error processing POST request ", e);
        }
    }

    /**
     *
     */
    private void processGet() {

        String uri = request.getRequestLine().getUri();

        String contextPath = cfgCtx.getContextRoot();
        if (!contextPath.startsWith("/")) {
            contextPath = "/" + contextPath;
        }
        if (!contextPath.endsWith("/")) {
            contextPath = contextPath + "/";
        }

        String servicePath = cfgCtx.getServiceContextPath();
        if (!servicePath.startsWith("/")) {
            servicePath = "/" + servicePath;
        }

        String serviceName = null;
        if (uri.startsWith(servicePath)) {
            serviceName = uri.substring(servicePath.length());
            if (serviceName.startsWith("/")) {
                serviceName = serviceName.substring(1);
            }
            if (serviceName.indexOf("?") != -1) {
                serviceName = serviceName.substring(0, serviceName.indexOf("?"));
            }
        }

        Map parameters = new HashMap();
        int pos = uri.indexOf("?");
        if (pos != -1) {
            StringTokenizer st = new StringTokenizer(uri.substring(pos+1), "&");
            while (st.hasMoreTokens()) {
                String param = st.nextToken();
                pos = param.indexOf("=");
                if (pos != -1) {
                    parameters.put(param.substring(0, pos), param.substring(pos+1));
                } else {
                    parameters.put(param, null);
                }
            }
        }

        if (uri.equals("/favicon.ico")) {
            response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
            response.addHeader(LOCATION, "http://ws.apache.org/favicon.ico");
            serverHandler.commitResponse(conn,  response);

        } else if (!uri.startsWith(servicePath)) {
            response.setStatusCode(HttpStatus.SC_MOVED_PERMANENTLY);
            response.addHeader(LOCATION, servicePath + "/");
            serverHandler.commitResponse(conn, response);

        } else if (serviceName != null && parameters.containsKey("wsdl")) {
            AxisService service = (AxisService) cfgCtx.getAxisConfiguration().
                getServices().get(serviceName);
            if (service != null) {
                try {
                    response.addHeader(CONTENT_TYPE, TEXT_XML);
                    serverHandler.commitResponse(conn, response);
                    service.printWSDL(os, getIpAddress(), contextPath);

                } catch (AxisFault e) {
                    handleException("Axis2 fault writing ?wsdl output", e);
                    return;
                } catch (SocketException e) {
                    handleException("Error getting ip address for ?wsdl output", e);
                    return;
                }
            }

        } else if (serviceName != null && parameters.containsKey("wsdl2")) {
            AxisService service = (AxisService) cfgCtx.getAxisConfiguration().
                getServices().get(serviceName);
            if (service != null) {
                try {
                    response.addHeader(CONTENT_TYPE, TEXT_XML);
                    serverHandler.commitResponse(conn, response);
                    service.printWSDL2(os, getIpAddress(), contextPath);

                } catch (AxisFault e) {
                    handleException("Axis2 fault writing ?wsdl2 output", e);
                    return;
                } catch (SocketException e) {
                    handleException("Error getting ip address for ?wsdl2 output", e);
                    return;
                }
            }

        } else if (serviceName != null && parameters.containsKey("xsd")) {
            if (parameters.get("xsd") == null || "".equals(parameters.get("xsd"))) {
                AxisService service = (AxisService) cfgCtx.getAxisConfiguration()
                    .getServices().get(serviceName);
                if (service != null) {
                    try {
                        response.addHeader(CONTENT_TYPE, TEXT_XML);
                        serverHandler.commitResponse(conn, response);
                        service.printSchema(os);

                    } catch (AxisFault axisFault) {
                        handleException("Error writing ?xsd output to client", axisFault);
                        return;
                    } catch (IOException e) {
                        handleException("Error writing ?xsd output to client", e);
                        return;
                    }
                }

            } else {
                //cater for named xsds - check for the xsd name
                String schemaName = (String) parameters.get("xsd");
                AxisService service = (AxisService) cfgCtx.getAxisConfiguration()
                    .getServices().get(serviceName);

                if (service != null) {
                    //run the population logic just to be sure
                    service.populateSchemaMappings();
                    //write out the correct schema
                    Map schemaTable = service.getSchemaMappingTable();
                    final XmlSchema schema = (XmlSchema)schemaTable.get(schemaName);
                    //schema found - write it to the stream
                    if (schema != null) {
                        response.addHeader(CONTENT_TYPE, TEXT_XML);
                        serverHandler.commitResponse(conn, response);
                        schema.write(os);

                    } else {
                        // no schema available by that name  - send 404
                        response.setStatusCode(HttpStatus.SC_NOT_FOUND);
                    }
                }
            }

        } else if (serviceName == null || serviceName.length() == 0) {

            try {
                response.addHeader(CONTENT_TYPE, TEXT_HTML);
                serverHandler.commitResponse(conn, response);
                os.write(HTTPTransportReceiver.getServicesHTML(cfgCtx).getBytes());

            } catch (IOException e) {
                handleException("Error writing ? output to client", e);
            }

        } else {
            if (parameters.isEmpty()) {
                AxisService service = (AxisService) cfgCtx.getAxisConfiguration().
                    getServices().get(serviceName);
                if (service != null) {
                    try {
                        response.addHeader(CONTENT_TYPE, TEXT_HTML);
                        serverHandler.commitResponse(conn, response);
                        os.write(HTTPTransportReceiver.printServiceHTML(serviceName, cfgCtx).getBytes());

                    } catch (IOException e) {
                        handleException("Error writing service HTML to client", e);
                        return;
                    }
                } else {
                    handleException("Invalid service : " + serviceName, null);
                    return;
                }

            } else {
                try {
                    serverHandler.commitResponse(conn, response);
                    HTTPTransportUtils.processHTTPGetRequest(
                            msgContext, os,
                            (request.getFirstHeader(SOAPACTION) != null ?
                                    request.getFirstHeader(SOAPACTION).getValue() : null),
                            request.getRequestLine().getUri(),
                            cfgCtx,
                            parameters);

                } catch (AxisFault axisFault) {
                    handleException("Error processing GET request for: " +
                            request.getRequestLine().getUri(), axisFault);
                }
            }
        }

        // make sure that the output stream is flushed and closed properly
        try {
            os.flush();
            os.close();
        } catch (IOException ignore) {}
    }


    private void handleException(String msg, Exception e) {

        if (e == null) {
            log.error(msg);
        } else {
            log.error(msg, e);
        }

        if (e == null) {
            e = new Exception(msg);
        }

        try {
            AxisEngine engine = new AxisEngine(cfgCtx);
            MessageContext faultContext = engine.createFaultMessageContext(msgContext, e);
            engine.sendFault(faultContext);

        } catch (Exception ex) {
            response.setStatusCode(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            response.addHeader(CONTENT_TYPE, TEXT_XML);
            serverHandler.commitResponse(conn, response);

            try {
                os.write(msg.getBytes());
                if (ex != null) {
                    os.write(ex.getMessage().getBytes());
                }
            } catch (IOException ignore) {}

            if (conn != null) {
                try {
                    conn.shutdown();
                } catch (IOException ignore) {}
            }
        }
    }


    public HttpResponse getResponse() {
        return response;
    }

    public OutputStream getOutputStream() {
        return os;
    }

    public InputStream getIs() {
        return is;
    }

    public ServerHandler getServiceHandler() {
        return serverHandler;
    }

    public NHttpServerConnection getConn() {
        return conn;
    }

    /**
     * Copied from transport.http of Axis2
     *
     * Returns the ip address to be used for the replyto epr
     * CAUTION:
     * This will go through all the available network interfaces and will try to return an ip address.
     * First this will try to get the first IP which is not loopback address (127.0.0.1). If none is found
     * then this will return this will return 127.0.0.1.
     * This will <b>not<b> consider IPv6 addresses.
     * <p/>
     * TODO:
     * - Improve this logic to genaralize it a bit more
     * - Obtain the ip to be used here from the Call API
     *
     * @return Returns String.
     * @throws java.net.SocketException
     */
    private static String getIpAddress() throws SocketException {
        Enumeration e = NetworkInterface.getNetworkInterfaces();
        String address = "127.0.0.1";

        while (e.hasMoreElements()) {
            NetworkInterface netface = (NetworkInterface) e.nextElement();
            Enumeration addresses = netface.getInetAddresses();

            while (addresses.hasMoreElements()) {
                InetAddress ip = (InetAddress) addresses.nextElement();
                if (!ip.isLoopbackAddress() && isIP(ip.getHostAddress())) {
                    return ip.getHostAddress();
                }
            }
        }
        return address;
    }

    private static boolean isIP(String hostAddress) {
        return hostAddress.split("[.]").length == 4;
    }
}
