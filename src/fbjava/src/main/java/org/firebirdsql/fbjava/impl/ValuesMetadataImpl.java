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

import java.sql.ParameterMetaData;
import java.sql.SQLException;
import java.util.List;

import org.firebirdsql.fbjava.ValuesMetadata;


final class ValuesMetadataImpl implements ValuesMetadata
{
	private List<Parameter> parameters;

	void setup(List<Parameter> parameters)
	{
		this.parameters = parameters;
	}

	@Override
	public int getIndex(String name)
	{
		for (int i = 0; i < parameters.size(); ++i)
		{
			if (name.equals(parameters.get(i).name))
				return i + 1;
		}

		return -1;
	}

	@Override
	public String getName(int index)
	{
		checkIndex(index);
		return parameters.get(index - 1).name;
	}

	@Override
	public Class<?> getJavaClass(int index)
	{
		checkIndex(index);
		return parameters.get(index - 1).javaClass;
	}

	@Override
	public int getParameterCount()
	{
		return parameters.size();
	}

	@Override
	public int getParameterType(int index)
	{
		checkIndex(index);
		return parameters.get(index - 1).type.getSecond();
	}

	@Override
	public int getPrecision(int index)
	{
		checkIndex(index);
		//// FIXME: Incorrect for multi-byte strings, for numerics etc.
		return parameters.get(index - 1).length;
	}

	@Override
	public int getScale(int index)
	{
		checkIndex(index);
		return parameters.get(index - 1).scale;
	}

	@Override
	public int isNullable(int index)
	{
		checkIndex(index);
		return parameters.get(index - 1).isNullable ?
			ParameterMetaData.parameterNullable : ParameterMetaData.parameterNoNulls;
	}

	@Override
	public boolean isSigned(int index) throws SQLException
	{
		checkIndex(index);
		return true;
	}

	@Override
	public String getParameterTypeName(int index) throws SQLException
	{
		checkIndex(index);
		return null;	//// FIXME:
	}

	@Override
	public String getParameterClassName(int index) throws SQLException
	{
		checkIndex(index);
		return parameters.get(index - 1).javaClass.getName();
	}

	@Override
	public int getParameterMode(int index) throws SQLException
	{
		checkIndex(index);
		return ParameterMetaData.parameterModeUnknown;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException
	{
		return null;
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException
	{
		return false;
	}

	private void checkIndex(int index)
	{
		if (index < 1 || index > parameters.size())
		{
			throw new IndexOutOfBoundsException(
				String.format("ValuesMetadata index out of bounds: Index: %d, Size: %d", index, parameters.size()));
		}
	}
}
