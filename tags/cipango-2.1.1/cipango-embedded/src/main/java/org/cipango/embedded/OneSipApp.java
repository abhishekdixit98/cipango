// ========================================================================
// Copyright 2008-2011 NEXCOM Systems
// ------------------------------------------------------------------------
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at 
// http://www.apache.org/licenses/LICENSE-2.0
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
// ========================================================================

package org.cipango.embedded;

import org.cipango.dar.DefaultApplicationRouter;
import org.cipango.lab.UdpConnector;
import org.cipango.server.Server;
import org.cipango.server.SipConnector;
import org.cipango.server.bio.TcpConnector;
import org.cipango.server.handler.SipContextHandlerCollection;
import org.cipango.sipapp.SipAppContext;

public class OneSipApp 
{
	public static void main(String[] args) throws Exception
    {
		Server server = new Server();
		
		UdpConnector udp = new UdpConnector();
		TcpConnector tcp = new TcpConnector();
		
		int port = Integer.getInteger("cipango.port", 5060);
		udp.setPort(port);
		tcp.setPort(port);
		
		server.getConnectorManager().setConnectors(new SipConnector[] { udp, tcp });
		
		SipAppContext sipapp = new SipAppContext();
		sipapp.setContextPath("/");
		sipapp.setWar(args[0]);
		
		SipContextHandlerCollection handler = new SipContextHandlerCollection();
		handler.addHandler(sipapp);
		
		server.setApplicationRouter(new DefaultApplicationRouter());
		server.setHandler(handler);
		
		server.start();
		server.join();
    }
}
