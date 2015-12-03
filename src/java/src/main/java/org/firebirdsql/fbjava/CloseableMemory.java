package org.firebirdsql.fbjava;

import com.sun.jna.Memory;


/***
 * JNA Memory with AutoCloseable.
 *
 * @author asfernandes
 */
class CloseableMemory extends Memory implements AutoCloseable
{
	public CloseableMemory(long size)
	{
		super(size);
	}

	@Override
	public void close()
	{
		dispose();
	}
}
