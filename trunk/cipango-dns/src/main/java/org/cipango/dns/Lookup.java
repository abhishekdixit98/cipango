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
import java.net.UnknownHostException;
import java.util.List;

import org.cipango.dns.record.CnameRecord;
import org.cipango.dns.record.Record;
import org.cipango.dns.section.HeaderSection.ResponseCode;

public class Lookup
{
	private Record _record;
	private Name _toSearch;
	private ResolverManager _resolverManager;
	private Cache _cache;
	private int _iterations = 0;
	
	public Lookup(ResolverManager resolverManager, Cache cache, Record record)
	{
		_resolverManager = resolverManager;
		_record = record;
		_cache = cache;
	}
	
	public List<Record> resolve() throws IOException, UnknownHostException
	{
		List<Record> records = getFromCache();
		
		while (records.isEmpty())
		{			
			Record record;
			if (_toSearch == null)
				record = _record;
			else
			{
				record = _record.getType().newRecord();
				record.setName(_toSearch);
			}
		
			DnsMessage query = new DnsMessage(record);
			DnsMessage answer = _resolverManager.resolve(query);
			incrementIteration();
			
			ResponseCode responseCode = answer.getHeaderSection().getResponseCode();
			if (responseCode == ResponseCode.NAME_ERROR)
			{
				_cache.addNegativeRecord(query, answer);
				throw new UnknownHostException(_record.getName().toString());
			} 
			else if (responseCode != ResponseCode.NO_ERROR)
				throw new IOException("Got negative answer: " + answer.getHeaderSection().getResponseCode());
			
			if (answer.getAnswerSection().isEmpty())
				throw new UnknownHostException(_record.getName().toString());
			
			_cache.addRecordSet(query, answer);
			
			records = getFromCache();
		}
		return records;
	}
	
	private List<Record> getFromCache() throws IOException
	{
		List<Record> records = _cache.getRecords(_record.getName(), _record.getType());
		while (records.size() == 1 && records.get(0).getType() == Type.CNAME && _record.getType() != Type.CNAME)
		{
			incrementIteration();
			_toSearch = ((CnameRecord) records.get(0)).getCname();
			records = _cache.getRecords(_toSearch, _record.getType());
		}
		return records;
	}
	
	private void incrementIteration() throws IOException
	{
		_iterations++;
		if (_iterations > 12)
			throw new IOException("Name " + _record.getName() + " Looped");
	}
}
