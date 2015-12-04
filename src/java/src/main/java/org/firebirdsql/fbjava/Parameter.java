package org.firebirdsql.fbjava;


class Parameter
{
	Parameter(DataType dataType, Class<?> javaClass)
	{
		this.dataType = dataType;
		this.javaClass = javaClass;
	}

	DataType dataType;
	Class<?> javaClass;
	DataType.Conversion conversion;
	int nullOffset;
	int offset;
	int length;
}
