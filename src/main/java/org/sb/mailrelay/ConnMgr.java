/**
 * 
 */
package org.sb.mailrelay;

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author sam
 *
 */
public class ConnMgr<T> implements Supplier<T> 
{
	private static final Logger log = Logger.getLogger(ConnMgr.class.getPackage().getName());
	
	private final Supplier<T> maker;
	private final Predicate<T> checker;
	private final Consumer<T> killer;
	private T inst;
	
	private ConnMgr(Supplier<T> maker2, Predicate<T> checker2, Consumer<T> killer2) 
	{
		maker = maker2;
		checker = checker2;
		killer = killer2;
	}

	public static <T> ConnMgr<T> wrap(Supplier<T> maker, Predicate<T> checker, Consumer<T> killer)
	{
		return new ConnMgr<T>(maker, checker, killer);
	}
	
	@Override
	public T get() 
	{
		T tmp = inst;
        if(tmp == null || !checker.test(tmp))
        synchronized (this)
        {
            tmp = inst;
            if(tmp == null || !checker.test(tmp))
            try
            {
            	if(tmp != null)
            	try 
    			{
            		log.info("Connection check failed, will attempt to reconnect");
    				killer.accept(tmp);
    			} 
    			catch (RuntimeException e) 
    			{
    				log.log(Level.SEVERE, "Connection cleanup failed", e);
    			}
                tmp = inst = maker.get();
            }
            catch(RuntimeException re)
            {
            	inst = null;
            	throw re;
            }
        }
        return tmp;
	}
}
