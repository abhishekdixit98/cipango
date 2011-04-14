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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import org.cipango.dns.record.ARecord;
import org.cipango.dns.record.AaaaRecord;
import org.cipango.dns.record.Record;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;


public class DnsService extends AbstractLifeCycle
{
	private ResolverManager _resolverManager = new ResolverManager();
	private Cache _cache;
	
	
	@Override
	protected void doStart() throws Exception
	{
		_resolverManager.start();
		
		if (_cache == null)
			_cache = new Cache();
	}

	public String getHostByAddr(byte[] addr) throws UnknownHostException
	{
		// TODO Auto-generated method stub
		return null;
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
		return new Lookup(_resolverManager, _cache, record).resolve();
								
	}

	public ResolverManager getResolverManager()
	{
		return _resolverManager;
	}

	public void setResolverManager(ResolverManager resolverManager)
	{
		_resolverManager = resolverManager;
	}

	public Cache getCache()
	{
		return _cache;
	}

	public void setCache(Cache cache)
	{
		_cache = cache;
	}

	
	
	
	
}
