/*
 * FB/Java plugin
 *
 * Distributable under LGPL license.
 * You may obtain a copy of the License at http://www.gnu.org/copyleft/lgpl.html
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * LGPL License for more details.
 *
 * This file was created by members of the Firebird development team.
 * All individual contributions remain the Copyright (C) of those
 * individuals.  Contributors to this file are either listed here or
 * can be obtained from a git log command.
 *
 * All rights reserved.
 */
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
