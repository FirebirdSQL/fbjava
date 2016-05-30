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
package org.firebirdsql.fbjava.impl;

import org.firebirdsql.fbjava.Values;


final class ValuesImpl implements Values
{
	private Object[] array;
	private int inCount;
	private int outCount;

	ValuesImpl(Object[] array, int inCount, int outCount)
	{
		this.array = array;
		this.inCount = inCount;
		this.outCount = outCount;
	}

	ValuesImpl(Object[] array, int inCount)
	{
		this(array, inCount, -1);
	}

	@Override
	public Object getObject(int index)
	{
		//// TODO: read only values

		if (outCount == -1)
		{
			checkIndex(index, inCount);
			return array[index - 1];
		}
		else
		{
			checkIndex(index, outCount);
			return ((Object[]) array[inCount + index - 1])[0];
		}
	}

	@Override
	public Object setObject(int index, Object value)
	{
		Object oldValue = getObject(index);

		if (outCount == -1)
		{
			checkIndex(index, inCount);
			array[index - 1] = value;
		}
		else
		{
			checkIndex(index, outCount);
			((Object[]) array[inCount + index - 1])[0] = value;
		}

		return oldValue;
	}

	private static void checkIndex(int index, int count)
	{
		if (index < 1 || index > count)
		{
			throw new IndexOutOfBoundsException(
				String.format("Values index out of bounds: Index: %d, Size: %d", index, count));
		}
	}
}
