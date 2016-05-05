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

import java.util.List;

import org.firebirdsql.fbjava.ValuesMetadata;


final class ValuesMetadataImpl implements ValuesMetadata
{
	private List<Parameter> parameters;
	private int count;

	void setup(List<Parameter> parameters)
	{
		this.parameters = parameters;
		count = parameters.size();
	}

	@Override
	public int getCount()
	{
		return count;
	}

	@Override
	public Class<?> getJavaClass(int index)
	{
		checkIndex(index);
		return parameters.get(index).javaClass;
	}

	private void checkIndex(int index)
	{
		if (index < 0 || index >= count)
		{
			throw new IndexOutOfBoundsException(
				String.format("ValuesMetadata index out of bounds: Index: %d, Size: %d", index, count));
		}
	}
}
