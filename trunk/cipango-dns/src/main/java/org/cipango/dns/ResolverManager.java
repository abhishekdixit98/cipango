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
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

import org.cipango.dns.bio.UdpConnector;
import org.eclipse.jetty.util.component.AbstractLifeCycle;

public class ResolverManager extends AbstractLifeCycle
{

	private List<Resolver> _resolvers = new ArrayList<Resolver>();
	
	private List<DnsConnector> _connectors = new ArrayList<DnsConnector>();
	
	
	@Override
	protected void doStart() throws Exception
	{
		if (_resolvers.isEmpty())
		{
			sun.net.dns.ResolverConfiguration resolverConfiguration = sun.net.dns.ResolverConfiguration.open();
			@SuppressWarnings({ "unchecked" })
			List<String> servers = resolverConfiguration.nameservers();
			int attemps = resolverConfiguration.options().attempts();
			int retrans = resolverConfiguration.options().retrans();
			
			for (String server: servers)
			{
				Resolver resolver = new Resolver();
				resolver.setHost(server);
				if (attemps != -1)
					resolver.setAttemps(attemps);
				if (retrans != -1)
					resolver.setTimeout(retrans);
				addResolver(resolver);
			}
		}
		
		if (_connectors.isEmpty())
			addConnector(new UdpConnector());
	}
	
	public DnsMessage resolve(DnsMessage query) throws IOException
	{
		SocketTimeoutException e = null;
		for (Resolver resolver : _resolvers)
		{
			try
			{
				return resolver.resolve(query);
			}
			catch (SocketTimeoutException e1) {
				e = e1;
			}
		}
		if (e == null)
			throw new IOException("No resovler");
		else
			throw e;
	}
	
	public void addResolver(int index, Resolver resolver)
	{
		_resolvers.add(index, resolver);
		resolver.setResolverManager(this);
	}
	
	public void addResolver(Resolver resolver)
	{
		_resolvers.add(resolver);
		resolver.setResolverManager(this);
	}
	
	public void addConnector(DnsConnector connector)
	{
		_connectors.add(connector);
	}
	
	public DnsConnector getDefaultConnector()
	{
		return _connectors.get(0);
	}

	public List<Resolver> getResolvers()
	{
		return _resolvers;
	}

	public List<DnsConnector> getConnectors()
	{
		return _connectors;
	}
}
