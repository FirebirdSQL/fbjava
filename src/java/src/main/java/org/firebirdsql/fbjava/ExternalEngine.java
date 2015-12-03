package org.firebirdsql.fbjava;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.firebirdsql.fbjava.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.FbClientLibrary.IExternalEngineIntf;
import org.firebirdsql.fbjava.FbClientLibrary.IExternalFunction;
import org.firebirdsql.fbjava.FbClientLibrary.IExternalProcedure;
import org.firebirdsql.fbjava.FbClientLibrary.IExternalTrigger;
import org.firebirdsql.fbjava.FbClientLibrary.IMessageMetadata;
import org.firebirdsql.fbjava.FbClientLibrary.IMetadataBuilder;
import org.firebirdsql.fbjava.FbClientLibrary.IReferenceCounted;
import org.firebirdsql.fbjava.FbClientLibrary.IRoutineMetadata;
import org.firebirdsql.fbjava.FbClientLibrary.IStatus;
import org.firebirdsql.gds.ISCConstants;

import com.sun.jna.Pointer;


class ExternalEngine implements IExternalEngineIntf
{
	private static Map<Integer, String> fbTypeNames;
	private static Map<Class<?>, DataType> dataTypesByClass;
	private static Map<String, DataType> dataTypesByName;
	private static Map<Integer, DataType> defaultDataTypes;

	static
	{
		fbTypeNames = new HashMap<>();
		fbTypeNames.put(ISCConstants.SQL_TEXT, "CHAR");
		fbTypeNames.put(ISCConstants.SQL_VARYING, "VARCHAR");
		fbTypeNames.put(ISCConstants.SQL_SHORT, "SMALLINT");
		fbTypeNames.put(ISCConstants.SQL_LONG, "INTEGER");
		fbTypeNames.put(ISCConstants.SQL_FLOAT, "FLOAT");
		fbTypeNames.put(ISCConstants.SQL_DOUBLE, "DOUBLE PRECISION");
		fbTypeNames.put(ISCConstants.SQL_D_FLOAT, "FLOAT");
		fbTypeNames.put(ISCConstants.SQL_TIMESTAMP, "TIMESTAMP");
		fbTypeNames.put(ISCConstants.SQL_BLOB, "BLOB");
		fbTypeNames.put(ISCConstants.SQL_ARRAY, "ARRAY");
		fbTypeNames.put(ISCConstants.SQL_QUAD, "QUAD");
		fbTypeNames.put(ISCConstants.SQL_TYPE_TIME, "TIME");
		fbTypeNames.put(ISCConstants.SQL_TYPE_DATE, "DATE");
		fbTypeNames.put(ISCConstants.SQL_INT64, "BIGINT");
		fbTypeNames.put(ISCConstants.SQL_BOOLEAN, "BOOLEAN");
		fbTypeNames.put(ISCConstants.SQL_NULL, "NULL");

		dataTypesByClass = new HashMap<>();
		dataTypesByName = new HashMap<>();
		defaultDataTypes = new HashMap<>();

		addDataType(new DataType() {
			@Override
			String[] getNames()
			{
				return new String[] {"int"};
			}

			@Override
			Class<?> getJavaClass()
			{
				return int.class;
			}

			@Override
			Conversion buildMetadata(IStatus status, IMessageMetadata metadata, IMetadataBuilder builder, int index)
				throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_LONG);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessage(Pointer message, int nullOffset, int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ? 0 : message.getInt(offset);
					}

					@Override
					void putInMessage(Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setInt(offset, (int) o);
						}
					}
				};
			}
		});

		addDataType(new DataType() {
			@Override
			String[] getNames()
			{
				return new String[] {"Integer", "java.lang.Integer"};
			}

			@Override
			Class<?> getJavaClass()
			{
				return Integer.class;
			}

			@Override
			Conversion buildMetadata(IStatus status, IMessageMetadata metadata, IMetadataBuilder builder, int index)
				throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_LONG);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessage(Pointer message, int nullOffset, int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ? null : message.getInt(offset);
					}

					@Override
					void putInMessage(Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setInt(offset, (int) o);
						}
					}
				};
			}
		});

		addDataType(new DataType() {
			@Override
			String[] getNames()
			{
				return new String[] {"double"};
			}

			@Override
			Class<?> getJavaClass()
			{
				return double.class;
			}

			@Override
			Conversion buildMetadata(IStatus status, IMessageMetadata metadata, IMetadataBuilder builder, int index)
				throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_DOUBLE);

				return new Conversion() {
					@Override
					Object getFromMessage(Pointer message, int nullOffset, int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ? 0 : message.getDouble(offset);
					}

					@Override
					void putInMessage(Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setDouble(offset, (double) o);
						}
					}
				};
			}
		});

		addDataType(new DataType() {
			@Override
			String[] getNames()
			{
				return new String[] {"Double", "java.lang.Double"};
			}

			@Override
			Class<?> getJavaClass()
			{
				return Double.class;
			}

			@Override
			Conversion buildMetadata(IStatus status, IMessageMetadata metadata, IMetadataBuilder builder, int index)
				throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_DOUBLE);

				return new Conversion() {
					@Override
					Object getFromMessage(Pointer message, int nullOffset, int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ? null : message.getDouble(offset);
					}

					@Override
					void putInMessage(Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setDouble(offset, (double) o);
						}
					}
				};
			}
		});

		addDataType(new DataType() {
			@Override
			String[] getNames()
			{
				return new String[] {"java.math.BigDecimal"};
			}

			@Override
			Class<?> getJavaClass()
			{
				return BigDecimal.class;
			}

			@Override
			Conversion buildMetadata(IStatus status, IMessageMetadata metadata, IMetadataBuilder builder, int index)
				throws FbException
			{
				int initialType = metadata.getType(status, index);
				int initialScale = metadata.getScale(status, index);
				final int type;
				final int scale;

				switch (initialType)
				{
					case ISCConstants.SQL_SHORT:
					case ISCConstants.SQL_LONG:
					case ISCConstants.SQL_INT64:
					case ISCConstants.SQL_FLOAT:
					case ISCConstants.SQL_DOUBLE:
						type = initialType;
						scale = initialScale;
						break;

					default:
						type = ISCConstants.SQL_DOUBLE;
						scale = 0;
						builder.setType(status, index, type);
						builder.setScale(status, index, scale);
						break;
				}

				return new Conversion() {
					@Override
					Object getFromMessage(Pointer message, int nullOffset, int offset)
					{
						if (message.getShort(nullOffset) != NOT_NULL_FLAG)
							return null;

						Long longVal = null;

						switch (type)
						{
							case ISCConstants.SQL_SHORT:
								longVal = (long) message.getShort(offset);
								break;

							case ISCConstants.SQL_LONG:
								longVal = (long) message.getInt(offset);
								break;

							case ISCConstants.SQL_INT64:
								longVal = message.getLong(offset);
								break;
						}

						if (longVal != null)
							return BigDecimal.valueOf(longVal).scaleByPowerOfTen(scale);

						switch (type)
						{
							case ISCConstants.SQL_FLOAT:
								return BigDecimal.valueOf(message.getFloat(offset));

							case ISCConstants.SQL_DOUBLE:
								return BigDecimal.valueOf(message.getDouble(offset));

							default:
								assert false;
								return null;
						}
					}

					@Override
					void putInMessage(Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
						{
							message.setShort(nullOffset, NULL_FLAG);
							return;
						}

						message.setShort(nullOffset, NOT_NULL_FLAG);

						BigDecimal bigVal = (BigDecimal) o;

						switch (type)
						{
							case ISCConstants.SQL_FLOAT:
								message.setFloat(offset, bigVal.floatValue());
								return;

							case ISCConstants.SQL_DOUBLE:
								message.setDouble(offset, bigVal.doubleValue());
								return;
						}

						BigInteger bigInt = bigVal.setScale(-scale, BigDecimal.ROUND_HALF_UP).unscaledValue();

						//// FIXME: overflow

						switch (type)
						{
							case ISCConstants.SQL_SHORT:
								message.setShort(offset, bigInt.shortValue());
								break;

							case ISCConstants.SQL_LONG:
								message.setInt(offset, bigInt.intValue());
								break;

							case ISCConstants.SQL_INT64:
								message.setLong(offset, bigInt.longValue());
								break;

							default:
								assert false;
						}
					}
				};
			}
		});

		addDataType(new DataType() {
			@Override
			String[] getNames()
			{
				return new String[] {"Object", "java.lang.Object"};
			}

			@Override
			Class<?> getJavaClass()
			{
				return Object.class;
			}

			@Override
			Conversion buildMetadata(IStatus status, IMessageMetadata metadata, IMetadataBuilder builder, int index)
				throws FbException
			{
				int type = metadata.getType(status, index);
				DataType defaultType = defaultDataTypes.get(type);

				if (defaultType == null)
				{
					String typeName = fbTypeNames.get(type);
					if (typeName == null)
						typeName = String.valueOf(type);

					throw new FbException(
						String.format("Cannot use Java Object type for the Firebird type '%s'.", typeName));
				}

				return defaultType.buildMetadata(status, metadata, builder, index);
			}
		});

		defaultDataTypes.put(ISCConstants.SQL_SHORT, dataTypesByClass.get(BigDecimal.class));
		defaultDataTypes.put(ISCConstants.SQL_LONG, dataTypesByClass.get(BigDecimal.class));
		defaultDataTypes.put(ISCConstants.SQL_DOUBLE, dataTypesByClass.get(Double.class));
	}

	private static void addDataType(DataType dataType)
	{
		dataTypesByClass.put(dataType.getJavaClass(), dataType);
		Arrays.stream(dataType.getNames()).forEach(name -> dataTypesByName.put(name, dataType));
	}

	@Override
	public void setOwner(IReferenceCounted r)
	{
	}

	@Override
	public IReferenceCounted getOwner()
	{
		return null;
	}

	@Override
	public void addRef()
	{
		//// TODO:
	}

	@Override
	public int release()
	{
		//// TODO:
		return 0;
	}

	@Override
	public void open(IStatus status, IExternalContext context, Pointer charSet, int charSetSize) throws FbException
	{
	}

	@Override
	public void openAttachment(IStatus status, IExternalContext context) throws FbException
	{
	}

	@Override
	public void closeAttachment(IStatus status, IExternalContext context) throws FbException
	{
	}

	@Override
	public IExternalFunction makeFunction(IStatus status, IExternalContext context,
		IRoutineMetadata metadata, IMetadataBuilder inBuilder, IMetadataBuilder outBuilder) throws FbException
	{
		Routine routine = getRoutine(status, metadata, inBuilder, outBuilder);
		return new IExternalFunction(new ExternalFunction(routine));
	}

	@Override
	public IExternalProcedure makeProcedure(IStatus status, IExternalContext context,
		IRoutineMetadata metadata, IMetadataBuilder inBuilder, IMetadataBuilder outBuilder) throws FbException
	{
		System.out.println("makeProcedure");
		return null;
	}

	@Override
	public IExternalTrigger makeTrigger(IStatus status, IExternalContext context,
		IRoutineMetadata metadata, IMetadataBuilder fieldsBuilder) throws FbException
	{
		System.out.println("makeTrigger");
		return null;
	}

	private Routine getRoutine(IStatus status, IRoutineMetadata metadata,
		IMetadataBuilder inBuilder, IMetadataBuilder outBuilder) throws FbException
	{
		try
		{
			String entryPoint = metadata.getEntryPoint(status);
			String invalidMethodSignatureMsg = String.format("Invalid method signature: '%s'", entryPoint);
			int paramsStart = entryPoint.indexOf('(');
			int methodStart = entryPoint.lastIndexOf('.', paramsStart) + 1;
			String className = entryPoint.substring(0, methodStart - 1).trim();
			String methodName = entryPoint.substring(methodStart, paramsStart).trim();

			Class<?> clazz = Class.forName(className, true, getClassLoader());
			Routine routine = new Routine();
			ArrayList<Class<?>> paramTypes = new ArrayList<>();

			int[] pos = {paramsStart + 1};

			skipBlanks(entryPoint, pos);

			if (peekChar(entryPoint, pos) == ')')
				++pos[0];
			else
			{
				do
				{
					DataType dataType = getDataType(entryPoint, pos);
					routine.inputParameters.add(new Parameter(dataType));
					paramTypes.add(dataType.getJavaClass());

					skipBlanks(entryPoint, pos);

					char c = getChar(entryPoint, pos);

					if (c == ')')
					{
						//// TODO: return[s] <type> ?
						break;
					}
					else if (c == ',')
						skipBlanks(entryPoint, pos);
					else
					{
						--pos[0];
						throw new FbException(invalidMethodSignatureMsg);
					}
				} while (true);
			}

			skipBlanks(entryPoint, pos);

			if (pos[0] != entryPoint.length())
				throw new FbException(invalidMethodSignatureMsg);

			routine.method = clazz.getMethod(methodName, paramTypes.toArray(new Class<?> [0]));

			DataType returnType = dataTypesByClass.get(routine.method.getReturnType());

			if (returnType == null)
			{
				throw new FbException(String.format("Unrecognized data type: '%s'",
					routine.method.getReturnType().getName()));
			}

			routine.outputParameters.add(new Parameter(returnType));

			IMessageMetadata inMetadata = metadata.getInputMetadata(status);
			try
			{
				IMessageMetadata outMetadata = metadata.getOutputMetadata(status);
				try
				{
					routine.buildParameters(status, routine.inputParameters, inMetadata, inBuilder);
					routine.buildParameters(status, routine.outputParameters, outMetadata, outBuilder);
				}
				finally
				{
					outMetadata.release();
				}
			}
			finally
			{
				inMetadata.release();
			}

			return routine;
		}
		catch (Throwable t)
		{
			FbException.rethrow(t);
			return null;
		}
	}

	private DataType getDataType(String s, int[] pos) throws FbException
	{
		String name = getName(s, pos);

		while (pos[0] < s.length() && peekChar(s, pos) == '.')
		{
			++pos[0];
			name += "." + getName(s, pos);
		}

		DataType dataType = dataTypesByName.get(name);

		if (dataType == null)
			throw new FbException(String.format("Unrecognized data type: '%s'",  name));

		return dataType;
	}

	private String getName(String s, int[] pos) throws FbException
	{
		if (pos[0] >= s.length())
			throw new FbException("Expected name but entry point end found.");

		int start = pos[0];

		if (!Character.isJavaIdentifierStart((peekChar(s, pos))))
			throw new FbException(String.format("Expected name at entry point character position %d.", pos[0]));

		while (Character.isJavaIdentifierPart(getChar(s, pos)))
			;

		--pos[0];

		return s.substring(start, pos[0]);
	}

	private void skipBlanks(String s, int[] pos)
	{
		int len = s.length();
		char c;

		while (pos[0] < len && ((c = s.charAt(pos[0])) == ' ' || c == '\t'))
			++pos[0];
	}

	private char getChar(String s, int[] pos) throws FbException
	{
		char c = peekChar(s, pos);
		++pos[0];
		return c;
	}

	private char peekChar(String s, int[] pos) throws FbException
	{
		if (pos[0] >= s.length())
			throw new FbException("Expected a character but entry point end found.");

		return s.charAt(pos[0]);
	}

	private ClassLoader getClassLoader()
	{
		return getClass().getClassLoader();
	}
}
