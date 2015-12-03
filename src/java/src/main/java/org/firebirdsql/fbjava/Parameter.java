package org.firebirdsql.fbjava;


class Parameter
{
	Parameter(DataType dataType)
	{
		this.dataType = dataType;
	}

	DataType dataType;
	DataType.Conversion conversion;
	int nullOffset;
	int offset;
	int length;
}
