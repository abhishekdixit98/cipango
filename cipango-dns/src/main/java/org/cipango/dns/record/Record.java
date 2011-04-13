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
package org.cipango.dns.record;


import java.io.IOException;

import org.cipango.dns.Compression;
import org.cipango.dns.DnsClass;
import org.cipango.dns.Name;
import org.cipango.dns.Type;
import org.eclipse.jetty.io.Buffer;


public abstract class Record
{	
	private int _ttl;
	private Name _name;
	private DnsClass _class;
	
	public abstract Type getType();
	
	
	public abstract void doEncode(Buffer b, Compression c) throws IOException;
		
	public abstract void doDecode(Buffer b, Compression c, int dataLength) throws IOException;
	
	protected byte[] decodeCharacterString(Buffer b)
	{
		int length = b.get();
		return b.get(length).asArray();
	}
	
	protected void encodeCharacterString(Buffer b, byte[] characterString)
	{
		b.put((byte) (characterString.length & 0xFF));
		b.put(characterString);
	}

	public int getTtl()
	{
		return _ttl;
	}

	public void setTtl(int ttl)
	{
		_ttl = ttl;
	}

	public Name getName()
	{
		return _name;
	}

	public void setName(Name name)
	{
		_name = name;
	}


	public DnsClass getDnsClass()
	{
		return _class;
	}


	public void setDnsClass(DnsClass clazz)
	{
		_class = clazz;
	}
	
	@Override
	public String toString()
	{
		return _name + ": type " + getType();
	}
	
}
