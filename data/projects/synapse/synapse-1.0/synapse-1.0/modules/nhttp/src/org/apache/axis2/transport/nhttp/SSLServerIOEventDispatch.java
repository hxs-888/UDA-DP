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

import java.io.IOException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;

import org.apache.http.impl.DefaultHttpRequestFactory;
import org.apache.http.impl.nio.DefaultNHttpServerConnection;
import org.apache.http.impl.nio.reactor.SSLIOSession;
import org.apache.http.impl.nio.reactor.SSLIOSessionHandler;
import org.apache.http.impl.nio.reactor.SSLMode;
import org.apache.http.nio.NHttpServiceHandler;
import org.apache.http.nio.reactor.IOEventDispatch;
import org.apache.http.nio.reactor.IOSession;
import org.apache.http.params.HttpParams;

public class SSLServerIOEventDispatch implements IOEventDispatch {

    private static final String NHTTP_CONN = "SYNAPSE.NHTTP_CONN";
    private static final String SSL_SESSION = "SYNAPSE.SSL_SESSION";
   
    private final NHttpServiceHandler handler;
    private final SSLContext sslcontext;
    private final SSLIOSessionHandler sslHandler;
    private final HttpParams params;
    
    public SSLServerIOEventDispatch(
            final NHttpServiceHandler handler,
            final SSLContext sslcontext,
            final SSLIOSessionHandler sslHandler,
            final HttpParams params) {
        super();
        if (handler == null) {
            throw new IllegalArgumentException("HTTP service handler may not be null");
        }
        if (sslcontext == null) {
            throw new IllegalArgumentException("SSL context may not be null");
        }
        if (params == null) {
            throw new IllegalArgumentException("HTTP parameters may not be null");
        }
        this.handler = new LoggingNHttpServiceHandler(handler);
        this.params = params;
        this.sslcontext = sslcontext;
        this.sslHandler = sslHandler;
    }
    
    public SSLServerIOEventDispatch(
            final NHttpServiceHandler handler,
            final SSLContext sslcontext,
            final HttpParams params) {
        this(handler, sslcontext, null, params);
    }
    
    public void connected(final IOSession session) {

        SSLIOSession sslSession = new SSLIOSession(
                session, 
                this.sslcontext,
                this.sslHandler); 
        
        DefaultNHttpServerConnection conn = new DefaultNHttpServerConnection(
                new LoggingIOSession(sslSession), 
                new DefaultHttpRequestFactory(),
                this.params); 
        
        session.setAttribute(NHTTP_CONN, conn);
        session.setAttribute(SSL_SESSION, sslSession);

        this.handler.connected(conn);

        try {
            sslSession.initialize(SSLMode.SERVER, this.params);
        } catch (SSLException ex) {
            this.handler.exception(conn, ex);
            sslSession.shutdown();
        }
    }

    public void disconnected(final IOSession session) {
        DefaultNHttpServerConnection conn = (DefaultNHttpServerConnection) session.getAttribute(
                NHTTP_CONN);
        this.handler.closed(conn);
    }

    public void inputReady(final IOSession session) {
        DefaultNHttpServerConnection conn = (DefaultNHttpServerConnection) session.getAttribute(
                NHTTP_CONN);
        SSLIOSession sslSession = (SSLIOSession) session.getAttribute(
                SSL_SESSION);
        try {
            synchronized (sslSession) {
                if (sslSession.isAppInputReady()) {
                    conn.consumeInput(this.handler);
                }
                sslSession.inboundTransport();
            }
        } catch (IOException ex) {
            this.handler.exception(conn, ex);
            sslSession.shutdown();
        }
    }

    public void outputReady(final IOSession session) {
        DefaultNHttpServerConnection conn = (DefaultNHttpServerConnection) session.getAttribute(
                NHTTP_CONN);
        SSLIOSession sslSession = (SSLIOSession) session.getAttribute(
                SSL_SESSION);
        try {
            synchronized (sslSession) {
                if (sslSession.isAppOutputReady()) {
                    conn.produceOutput(this.handler);
                }
                sslSession.outboundTransport();
            }
        } catch (IOException ex) {
            this.handler.exception(conn, ex);
            sslSession.shutdown();
        }
    }

    public void timeout(final IOSession session) {
        DefaultNHttpServerConnection conn = (DefaultNHttpServerConnection) session.getAttribute(
                NHTTP_CONN);
        SSLIOSession sslSession = (SSLIOSession) session.getAttribute(
                SSL_SESSION);
        this.handler.timeout(conn);
        synchronized (sslSession) {
            if (sslSession.isOutboundDone() && !sslSession.isInboundDone()) {
                // The session failed to terminate cleanly 
                sslSession.shutdown();
            }
        }
    }

}
