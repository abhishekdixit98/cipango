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

import org.cipango.dns.bio.UdpConnector;
import org.cipango.dns.record.ARecord;
import org.junit.Test;

public class LookupTest
{
	
	@Test
	public void testResolve() throws Exception
	{
		Resolver resolver = new Resolver();
		resolver.setConnector(new UdpConnector());
		resolver.setHost("192.168.2.207");
		
		Lookup lookup = new Lookup(resolver, new Cache(), new ARecord("jira.cipango.org"));
		System.out.println(lookup.resolve());
		lookup.resolve();
	}
	
}
