// ========================================================================
// Copyright 2008-2009 NEXCOM Systems
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

package org.cipango.server.transaction;

import org.cipango.server.Server;
import org.cipango.server.SipConnection;
import org.cipango.server.SipRequest;
import org.cipango.server.session.CallSession;
import org.cipango.util.TimerTask;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 * SIP transaction base class
 */
public abstract class Transaction
{
	private static final Logger LOG = Log.getLogger(Transaction.class);
	
	public static final int STATE_UNDEFINED  = 0;
    public static final int STATE_CALLING    = 1;
    public static final int STATE_TRYING     = 2;
    public static final int STATE_PROCEEDING = 3;
    public static final int STATE_COMPLETED  = 4;
    public static final int STATE_CONFIRMED  = 5;
    public static final int STATE_ACCEPTED	 = 6;
    public static final int STATE_TERMINATED = 7;
    
    public static final String[] STATES = 
    {
        "Undefined", 
        "Calling", 
        "Trying", 
        "Proceeding", 
        "Completed", 
        "Confirmed", 
        "Accepted",
        "Terminated"
    };
   
    public static final int DEFAULT_T1 = 500;
    public static final int DEFAULT_T2 = 4000;
    public static final int DEFAULT_T4 = 5000;

    public static final int DEFAULT_TD = 32000;

    
    protected TimerTask[] _timers;
    protected TimersSettings _timersConfiguration;
    
    protected int _state;
    private String _branch;
    private String _key;
    protected SipRequest _request;
    protected CallSession _callSession;
    protected boolean _cancel;
    
    private SipConnection _connection;
    
    public Transaction(SipRequest request, String branch)
    {
        _request = request;
        _callSession = request.getCallSession();
        _branch = branch;
        _cancel = request.isCancel();
        _timersConfiguration = getServer().getTransactionManager().getTimersSettings();
            
        _key = _cancel ? "cancel-" + branch : branch;
        request.setTransaction(this);
    }
    
    public SipConnection getConnection()
    {
    	return _connection;
    }
    
    public void setConnection(SipConnection connection)
    {
    	_connection = connection;
    }
    
    public boolean isInvite() 
    {
        return _request.isInvite(); 
    }
    
    public boolean isAck() 
    {
        return _request.isAck();
    }
    
    public String getKey() 
    {
        return _key;
    }
    
    public SipRequest getRequest() 
    {
        return _request;
    }
    
    public int getState() 
    {
        return _state;
    }
    
    public boolean isCompleted()
    {
    	return (_state >= STATE_COMPLETED);
    }
    
    public abstract boolean isServer();
    
    public void setState(int state) 
    {
        if (LOG.isDebugEnabled()) 
            LOG.debug("{} -> {}", this, STATES[state]);
        
        _state = state;
    }
    
    public String getStateAsString()
    {
    	return STATES[_state];
    }
    
    public void startTimer(int timer, long delay)
    {
    	TimerTask timerTask = _timers[timer];
    	if (timerTask != null)
    		_callSession.cancel(timerTask);
    	_timers[timer] = _callSession.schedule(new Timer(timer), delay);
    }
    
    public void cancelTimer(int timer) 
    {
    	TimerTask timerTask = _timers[timer];
    	if (timerTask != null)
    		_callSession.cancel(timerTask);
    	_timers[timer] = null;
    }
    
    public abstract String asString(int timer);
    
    public boolean isTransportReliable()
	{
		return getConnection().getConnector().isReliable();
	}
    
    public Server getServer() 
    {
        return getCallSession().getServer();
    }
    
    public String getBranch() 
    {
        return _branch;
    }
    
    public CallSession getCallSession() 
    {
        return _callSession;
    }
    
    public abstract void timeout(int id);
    
    /**
     * SIP transaction timer
     */
    class Timer implements Runnable
    {
		private int _timer;
    	
    	public Timer(int timer)
    	{
    		_timer = timer;
    	}
    	
    	public void run()
    	{
    		try 
    		{		
    			if (LOG.isDebugEnabled())
    				LOG.debug("timeout {} for transaction {}", this, Transaction.this);
    			timeout(_timer);
    		}
    		catch (Throwable t)
    		{
    			LOG.warn(t);
    		}
    	}
    	
    	public String toString()
    	{
    		return asString(_timer);
    	}
    }
  
    public String toString() 
    {
        return _branch + "/" + _request.getMethod() + "/" + STATES[_state];
    }
    
    public static class TimersSettings
    {
		private int _t1 = DEFAULT_T1;
        private int _t2 = DEFAULT_T2;
        private int _t4 = DEFAULT_T4;
        private int _tD = DEFAULT_TD;
        
        public TimersSettings()
        {
        }

        public TimersSettings(TimersSettings src) 
        {
        	_t1 = src._t1;
        	_t2 = src._t2;
        	_t4 = src._t4;
        	_tD = src._tD;
        }

		public int getT1() { return _t1; }
		public int getT2() { return _t2; }
		public int getT4() { return _t4; }
		public int getTB() { return 64*_t1; }
		public int getTD() { return _tD; }
		public int getTF() { return 64*_t1; }
		public int getTH() { return 64*_t1; }
		public int getTI() { return _t4; }
		public int getTJ() { return 64*_t1; }
		public int getTK() { return _t4; }
		public int getTL() { return 64*_t1; }
		public int getTM() { return 64*_t1; }

		public void setT1(int t1) { _t1 = t1; }
		public void setT2(int t2) { _t2 = t2; }
		public void setT4(int t4) { _t4 = t4; }
		public void setTD(int tD) { _tD = tD; }
    }
}

