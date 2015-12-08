package org.firebirdsql.fbjava;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


class JnaUtil
{
	private static final Set<Object> objects = Collections.newSetFromMap(new ConcurrentHashMap<Object, Boolean>());

	public static <T> T pin(T o)
	{
		boolean added = objects.add(o);
		assert added;
		return o;
	}

	public static void unpin(Object o)
	{
		boolean removed = objects.remove(o);
		assert removed;
	}
}
