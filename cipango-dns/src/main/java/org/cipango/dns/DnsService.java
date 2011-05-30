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
import java.util.ArrayList;
import java.util.List;

import org.cipango.dns.bio.UdpConnector;
import org.cipango.dns.record.ARecord;
import org.cipango.dns.record.AaaaRecord;
import org.cipango.dns.record.Record;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.LazyList;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;


public class DnsService extends AbstractLifeCycle implements DnsClient
{
	private Cache _cache;
	private List<Name> _searchList = new ArrayList<Name>(); 
	private Resolver[] _resolvers;
	private DnsConnector[] _connectors;
	private Server _server;
	
	@Override
	protected void doStart() throws Exception
	{
		if (_resolvers == null || _resolvers.length == 0)
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
		
		if (_connectors == null || _connectors.length == 0)
			addConnector(new UdpConnector());
		
		if (_searchList.isEmpty())
		{
			sun.net.dns.ResolverConfiguration resolverConfiguration = sun.net.dns.ResolverConfiguration.open();
			for (Object name : resolverConfiguration.searchlist())
				_searchList.add(new Name((String) name));
		}
		
		if (_cache == null)
			_cache = new Cache();
	}

	public List<InetAddress> lookupIpv4HostAddr(String name) throws UnknownHostException
	{
		try
		{
			List<Record> records = lookup(new ARecord(name));
			List<InetAddress> addresses = new ArrayList<InetAddress>();
			
			for (Record record : records)
				if (record.getType() == Type.A)
					addresses.add(((ARecord) record).getAddress());
			return addresses;
		}
		catch (Exception e) 
		{
			if (e instanceof UnknownHostException)
				throw (UnknownHostException) e;
			e.printStackTrace();
			Log.debug(e);
			throw new UnknownHostException(name);
		}
	}
	
	public List<InetAddress> lookupIpv6HostAddr(String name) throws UnknownHostException
	{
		try
		{
			List<Record> records = lookup(new AaaaRecord(name));
			List<InetAddress> addresses = new ArrayList<InetAddress>();
			
			for (Record record : records)
				if (record.getType() == Type.AAAA)
					addresses.add(((AaaaRecord) record).getAddress());
			return addresses;
		}
		catch (Exception e) 
		{
			if (e instanceof UnknownHostException)
				throw (UnknownHostException) e;
			Log.debug(e);
			throw new UnknownHostException(name);
		}
	}
	
	public List<Record> lookup(Record record) throws IOException
	{
		return new Lookup(this, record).resolve();
								
	}

	public Cache getCache()
	{
		return _cache;
	}

	public void setCache(Cache cache)
	{
		_cache = cache;
	}

	public List<Name> getSearchList()
	{
		return _searchList;
	}

	public void setSearchList(List<Name> searchList)
	{
		_searchList = searchList;
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
		
	public void addResolver(Resolver resolver)
	{
		setResolvers((Resolver[]) LazyList.addToArray(getResolvers(), resolver, Resolver.class));
	}
	
	public void addConnector(DnsConnector connector)
	{
		setConnectors((DnsConnector[]) LazyList.addToArray(getConnectors(), connector, DnsConnector.class));
	}
	
	public DnsConnector getDefaultConnector()
	{
		if (_connectors == null || _connectors.length == 0)
    		return null;
    	return _connectors[0];
	}

	public Resolver[] getResolvers()
	{
		return _resolvers;
	}

	public DnsConnector[] getConnectors()
	{
		return _connectors;
	}

	public Server getServer()
	{
		return _server;
	}

	public void setServer(Server server)
	{
		_server = server;
	}

	public void setConnectors(DnsConnector[] connectors)
	{
		 if (_server != null)
	        _server.getContainer().update(this, _connectors, connectors, "connectors");
		_connectors = connectors;
	}

	public void setResolvers(Resolver[] resolvers)
	{
		 if (_server != null)
		        _server.getContainer().update(this, _resolvers, resolvers, "resolvers");
		 for (int i = 0; i < resolvers.length; i++)
			 resolvers[i].setDnsClient(this);
		_resolvers = resolvers;
	}

	
	
	
	
}
