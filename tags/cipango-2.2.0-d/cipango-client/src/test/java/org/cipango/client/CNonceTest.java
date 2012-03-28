package org.cipango.client;

import static org.junit.Assert.*;
import org.junit.Test;

public class CNonceTest 
{
	@Test
	public void testHex8() throws Exception
	{
		int[] cnoncesOrdinal = { 1, 10, 100, 1000, 10000, 100000, 1000000, 10000000 };
		
		for (int i = 0; i < cnoncesOrdinal.length; i++)
		{
			String s = Authentication.toHex8(cnoncesOrdinal[i]);
			assertEquals(8, s.length());
			int cnonce = Integer.parseInt(s, 16);
			assertEquals(cnoncesOrdinal[i], cnonce);
		}
	}
}
