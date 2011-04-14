// ========================================================================
// Copyright 2011 NEXCOM Systems
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
package org.cipango.dns;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import org.eclipse.jetty.util.log.Log;

public class Resolver
{
	public static final int DEFAULT_PORT = 53;
	public static final int DEFAULT_TIMEOUT = 1000;
	
	private ResolverManager _resolverManager;
	private InetAddress _host;
	private int _port = DEFAULT_PORT;
	private long _timeout = DEFAULT_TIMEOUT;
	private int _attemps = 2;
	
	
	
	public DnsMessage resolve(DnsMessage query) throws IOException
	{
		DnsConnection c = _resolverManager.getDefaultConnector().newConnection(_host, _port);
		SocketTimeoutException e = null;
		long timeout = _timeout;
		for (int i = 0; i < _attemps; i++)
		{
			try
			{
				c.send(query);
				long end = System.currentTimeMillis() + timeout;
				DnsMessage answer;
				while (true)
				{
					answer = c.waitAnswer((int) (end - System.currentTimeMillis()));
					if (answer.getHeaderSection().getId() != query.getHeaderSection().getId())
						Log.warn("Drop DNS Answser {}, expected id {}", answer, query.getHeaderSection().getId());
					else
						return answer;
				}
			}
			catch (SocketTimeoutException e1)
			{
				e = e1;
				timeout *= 2;
			}
		}
		throw e;
		
	}

	public InetAddress getHost()
	{
		return _host;
	}

	public void setHost(String host) throws UnknownHostException
	{
		_host = InetAddress.getByName(host);
	}

	public void setHost(InetAddress host)
	{
		_host = host;
	}



	public int getPort()
	{
		return _port;
	}



	public void setPort(int port)
	{
		_port = port;
	}



	public long getTimeout()
	{
		return _timeout;
	}



	public void setTimeout(long timeout)
	{
		_timeout = timeout;
	}



	public int getAttemps()
	{
		return _attemps;
	}



	public void setAttemps(int attemps)
	{
		_attemps = attemps;
	}



	public ResolverManager getResolverManager()
	{
		return _resolverManager;
	}



	public void setResolverManager(ResolverManager resolverManager)
	{
		_resolverManager = resolverManager;
	}
}
