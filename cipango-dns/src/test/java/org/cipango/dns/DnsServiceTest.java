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

import static org.junit.Assert.*;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.cipango.dns.record.NaptrRecord;
import org.cipango.dns.record.PtrRecord;
import org.cipango.dns.record.Record;
import org.cipango.dns.record.SrvRecord;
import org.junit.Before;
import org.junit.Test;

public class DnsServiceTest
{
	private DnsService _dnsService;
	public static final String  IPV4_ADDR = "46.105.46.188";
	public static final String  IPV6_ADDR = "2001:41d0:2:7a93::1";
	
	@Before
	public void setUp() throws Exception
	{
		_dnsService = new DnsService();
		_dnsService.start();
	}
	
	
	@Test
	public void testA() throws Exception
	{
		List<InetAddress> addr = _dnsService.lookupIpv4HostAddr("jira.cipango.org");
		assertNotNull(addr);
		assertEquals(1, addr.size());
		assertEquals(IPV4_ADDR, addr.get(0).getHostAddress());
	}
	
	@Test (expected = UnknownHostException.class)
	public void testInvalidHost() throws Exception
	{
		_dnsService.lookupIpv4HostAddr("host.bad.cipango.org");
	}
	
	@Test
	public void testAaaa() throws Exception
	{
		List<InetAddress> addr = _dnsService.lookupIpv6HostAddr("cipango.org");
		assertNotNull(addr);
		assertEquals(1, addr.size());
		assertEquals(InetAddress.getByName(IPV6_ADDR), addr.get(0));
	}
	
	@Test
	public void testSrv() throws Exception
	{
		List<Record> records = _dnsService.lookup(new SrvRecord("sip", "udp", "cipango.org"));
		assertNotNull(records);
		assertEquals(1, records.size());
		//System.out.println(records);
		SrvRecord srvRecord = (SrvRecord) records.get(0);
		assertEquals(10, srvRecord.getPriority());
		assertEquals(60, srvRecord.getWeight());
		assertEquals(5060, srvRecord.getPort());
		assertEquals("cipango.org", srvRecord.getTarget().toString());
	}
	
	@Test
	public void testNaptr() throws Exception
	{
		List<Record> records = _dnsService.lookup(new NaptrRecord("cipango.org"));
		assertNotNull(records);
		assertEquals(2, records.size());
		//System.out.println(records);
		for (Record record : records)
		{
			NaptrRecord naptrRecord = (NaptrRecord) record;
			if (naptrRecord.getOrder() == 100)
			{
				assertEquals(50, naptrRecord.getPreference());
				assertEquals("S", naptrRecord.getFlags());
				assertEquals("SIP+D2U", naptrRecord.getService());
				assertEquals("", naptrRecord.getRegexp());
				assertEquals("_sip._udp.cipango.org", naptrRecord.getReplacement().toString());
			}
			else if (naptrRecord.getOrder() == 90)
			{
				assertEquals(50, naptrRecord.getPreference());
				assertEquals("S", naptrRecord.getFlags());
				assertEquals("SIP+D2T", naptrRecord.getService());
				assertEquals("", naptrRecord.getRegexp());
				assertEquals("_sip._tcp.cipango.org", naptrRecord.getReplacement().toString());
			}
		}
	}
	
	@Test
	public void testPtr() throws Exception
	{
		List<Record> records = _dnsService.lookup(new PtrRecord(InetAddress.getByName(IPV4_ADDR)));
		assertEquals(1, records.size());
		PtrRecord ptr = (PtrRecord) records.get(0);
		assertEquals("46-105-46-188.ovh.net", ptr.getPrtdName().toString());
		//System.out.println(records);
	}
	

	public void testPtrIpv6() throws Exception
	{
		//new PtrRecord(InetAddress.getByName(IPV6_ADDR));
		List<Record> records = _dnsService.lookup(new PtrRecord(InetAddress.getByName(IPV6_ADDR)));
		assertEquals(1, records.size());
		PtrRecord ptr = (PtrRecord) records.get(0);
		assertEquals("46-105-46-188.ovh.net", ptr.getPrtdName().toString());
		//System.out.println(records);
	}
	
	@Test
	public void testBadResolver() throws Exception
	{
		Resolver badResolver = new Resolver();
		badResolver.setHost("127.0.0.1");
		badResolver.setPort(45877);
		badResolver.setTimeout(1500);
		badResolver.setMaxRetries(1);
		_dnsService.getResolverManager().addResolver(0, badResolver);
		List<InetAddress> addr = _dnsService.lookupIpv4HostAddr("www.cipango.org");
		assertNotNull(addr);
		assertEquals(1, addr.size());
		assertEquals(IPV4_ADDR, addr.get(0).getHostAddress());
	}
	
}
