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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.AccessControlContext;
import java.security.Principal;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.security.auth.Subject;

import org.firebirdsql.encodings.Encoding;
import org.firebirdsql.encodings.EncodingFactory;
import org.firebirdsql.encodings.IEncodingFactory;
import org.firebirdsql.fbjava.ExternalResultSet;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalContext;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalEngine;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalEngineIntf;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalFunction;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalProcedure;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IExternalTrigger;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IMessageMetadata;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IMetadataBuilder;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IReferenceCounted;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IRoutineMetadata;
import org.firebirdsql.fbjava.impl.FbClientLibrary.ISC_DATE;
import org.firebirdsql.fbjava.impl.FbClientLibrary.ISC_TIME;
import org.firebirdsql.fbjava.impl.FbClientLibrary.IStatus;
import org.firebirdsql.gds.ISCConstants;
import org.firebirdsql.gds.impl.GDSHelper;
import org.firebirdsql.jdbc.FBBlob;
import org.firebirdsql.jdbc.FBConnection;

import com.sun.jna.Pointer;


final class ExternalEngine implements IExternalEngineIntf
{
	private static final IEncodingFactory encodingFactory = EncodingFactory.getPlatformDefault();
	private static Map<String, SharedData> sharedDataMap = new ConcurrentHashMap<>();
	private static Map<Integer, Pair<String, Integer>> fbTypeNames;
	private static Map<Class<?>, DataType> dataTypesByClass;
	private static Map<String, Class<?>> javaClassesByName;
	private static Map<Integer, DataType> defaultDataTypes;
	private IExternalEngine wrapper;
	private AtomicInteger refCounter = new AtomicInteger(1);
	private SharedData sharedData;

	private static class DataTypeReg
	{
		DataTypeReg(Class<?> javaClass, String... names)
		{
			this.javaClass = javaClass;
			this.names = names;
		}

		Class<?> javaClass;
		String[] names;
	}

	private static class SharedData
	{
		AtomicInteger attachmentCounter = new AtomicInteger(0);
		DbClassLoader classLoader;

		SharedData(String databaseName) throws SQLException, MalformedURLException
		{
			URL url = new URL(null, "fbjava:/", new URLStreamHandler() {
				@Override
				protected URLConnection openConnection(URL url) throws IOException
				{
					return new BlobConnection(url);
				}
			});

			classLoader = new DbClassLoader(databaseName, new URL[] {url}, getClass().getClassLoader());
		}

		void openAttachment(IStatus status, IExternalContext context)
		{
			attachmentCounter.incrementAndGet();
		}

		boolean closeAttachment(IStatus status, IExternalContext context) throws IOException
		{
			if (attachmentCounter.decrementAndGet() == 0)
			{
				classLoader.close();
				return true;
			}
			else
				return false;
		}
	}

	private ExternalEngine(String securityDatabase)
	{
	}

	static
	{
		fbTypeNames = new HashMap<>();
		fbTypeNames.put(ISCConstants.SQL_TEXT, Pair.of("CHAR", Types.CHAR));
		fbTypeNames.put(ISCConstants.SQL_VARYING, Pair.of("VARCHAR", Types.VARCHAR));
		fbTypeNames.put(ISCConstants.SQL_SHORT, Pair.of("SMALLINT", Types.SMALLINT));
		fbTypeNames.put(ISCConstants.SQL_LONG, Pair.of("INTEGER", Types.INTEGER));
		fbTypeNames.put(ISCConstants.SQL_FLOAT, Pair.of("FLOAT", Types.FLOAT));
		fbTypeNames.put(ISCConstants.SQL_DOUBLE, Pair.of("DOUBLE PRECISION", Types.DOUBLE));
		fbTypeNames.put(ISCConstants.SQL_D_FLOAT, Pair.of("FLOAT", Types.FLOAT));
		fbTypeNames.put(ISCConstants.SQL_TIMESTAMP, Pair.of("TIMESTAMP", Types.TIMESTAMP));
		fbTypeNames.put(ISCConstants.SQL_BLOB, Pair.of("BLOB", Types.BLOB));
		fbTypeNames.put(ISCConstants.SQL_ARRAY, Pair.of("ARRAY", Types.ARRAY));
		fbTypeNames.put(ISCConstants.SQL_QUAD, Pair.of("QUAD", -1));
		fbTypeNames.put(ISCConstants.SQL_TYPE_TIME, Pair.of("TIME", Types.TIME));
		fbTypeNames.put(ISCConstants.SQL_TYPE_DATE, Pair.of("DATE", Types.DATE));
		fbTypeNames.put(ISCConstants.SQL_INT64, Pair.of("BIGINT", Types.BIGINT));
		fbTypeNames.put(ISCConstants.SQL_BOOLEAN, Pair.of("BOOLEAN", Types.BOOLEAN));
		fbTypeNames.put(ISCConstants.SQL_NULL, Pair.of("NULL", Types.NULL));

		dataTypesByClass = new HashMap<>();
		javaClassesByName = new HashMap<>();
		defaultDataTypes = new HashMap<>();

		// String
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				int type = metadata.getType(status, index);
				int length = metadata.getLength(status, index);
				Encoding encoding = encodingFactory.getEncodingForCharacterSetId(metadata.getCharSet(status, index));

				switch (type)
				{
					case ISCConstants.SQL_TEXT:
						builder.setType(status, index, ISCConstants.SQL_VARYING);
						break;

					case ISCConstants.SQL_VARYING:
						break;

					case ISCConstants.SQL_BLOB:
					{
						Conversion byteConversion = dataTypesByClass.get(byte[].class).setupConversion(status,
							byte[].class, metadata, builder, index);

						return new Conversion() {
							@Override
							Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
								throws FbException
							{
								if (message.getShort(nullOffset) != NOT_NULL_FLAG)
									return null;

								byte[] bytes = (byte[]) byteConversion.getFromMessage(context, message,
									nullOffset, offset);

								return encoding.decodeFromCharset(bytes);
							}

							@Override
							void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset,
								Object o) throws FbException
							{
								if (o == null)
									message.setShort(nullOffset, NULL_FLAG);
								else
								{
									byte[] bytes = encoding.encodeToCharset((String) o);
									byteConversion.putInMessage(context, message, nullOffset, offset, bytes);
								}
							}
						};
					}

					case ISCConstants.SQL_SHORT:
					case ISCConstants.SQL_LONG:
					case ISCConstants.SQL_FLOAT:
					case ISCConstants.SQL_DOUBLE:
					case ISCConstants.SQL_D_FLOAT:
					case ISCConstants.SQL_TIMESTAMP:
					case ISCConstants.SQL_TYPE_TIME:
					case ISCConstants.SQL_TYPE_DATE:
					case ISCConstants.SQL_INT64:
					case ISCConstants.SQL_BOOLEAN:
						builder.setType(status, index, ISCConstants.SQL_VARYING);
						length = 25;	// max length (of timestamp)
						builder.setLength(status, index, length);
						break;

					default:
					{
						String typeName = Optional.of(fbTypeNames.get(type))
							.map(Pair::getFirst)
							.orElse(String.valueOf(type));

						throw new FbException(
							String.format("Cannot use Java String type for the Firebird type '%s'.", typeName));
					}
				}

				final int finalLength = length;

				return new Conversion() {
					@Override
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
					{
						if (message.getShort(nullOffset) != NOT_NULL_FLAG)
							return null;

						int length = Short.toUnsignedInt(message.getShort(offset));
						return encoding.decodeFromCharset(message.getByteArray(offset + 2, length));
					}

					@Override
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset,
						Object o) throws FbException
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							byte[] bytes = encoding.encodeToCharset((String) o);

							if (bytes.length > finalLength)
							{
								throw new FbException(String.format(
									"String with length (%d) bytes greater than max expected length (%d).",
									bytes.length, finalLength));
							}

							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setShort(offset, (short) bytes.length);
							message.write(offset + 2, bytes, 0, bytes.length);
						}
					}
				};
			}
		}, new DataTypeReg(String.class, "String"), new DataTypeReg(String.class, "java.lang.String"));

		// byte[]
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				int type = metadata.getType(status, index);
				int length = metadata.getLength(status, index);

				switch (type)
				{
					case ISCConstants.SQL_TEXT:
						builder.setType(status, index, ISCConstants.SQL_VARYING);
						break;

					case ISCConstants.SQL_VARYING:
					case ISCConstants.SQL_BLOB:
						break;

					default:
					{
						String typeName = Optional.of(fbTypeNames.get(type))
							.map(Pair::getFirst)
							.orElse(String.valueOf(type));

						throw new FbException(
							String.format("Cannot use Java byte[] type for the Firebird type '%s'.", typeName));
					}
				}

				if (type == ISCConstants.SQL_BLOB)
				{
					return new Conversion() {
						@Override
						Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
							throws FbException
						{
							if (message.getShort(nullOffset) != NOT_NULL_FLAG)
								return null;

							long blobId = message.getLong(offset);

							try
							{
								FBConnection connection = (FBConnection) InternalContext.get().getConnection();
								GDSHelper gdsHelper = connection.getGDSHelper();
								FBBlob blob = new FBBlob(gdsHelper, blobId);

								try (InputStream in = blob.getBinaryStream())
								{
									byte[] bytes = new byte[(int) blob.length()];
									in.read(bytes);
									return bytes;
								}
							}
							catch (Exception e)
							{
								FbException.rethrow(e);
								return null;
							}
						}

						@Override
						void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset,
							Object o) throws FbException
						{
							if (o == null)
								message.setShort(nullOffset, NULL_FLAG);
							else
							{
								byte[] bytes = (byte[]) o;

								try
								{
									FBConnection connection = (FBConnection) InternalContext.get().getConnection();
									FBBlob blob = (FBBlob) connection.createBlob();

									try (OutputStream out = blob.setBinaryStream(1))
									{
										out.write(bytes);
									}

									message.setShort(nullOffset, NOT_NULL_FLAG);
									message.setLong(offset, blob.getBlobId());
								}
								catch (Exception e)
								{
									FbException.rethrow(e);
								}
							}
						}
					};
				}
				else
				{
					return new Conversion() {
						@Override
						Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
						{
							if (message.getShort(nullOffset) != NOT_NULL_FLAG)
								return null;

							int length = Short.toUnsignedInt(message.getShort(offset));
							return message.getByteArray(offset + 2, length);
						}

						@Override
						void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset,
							Object o) throws FbException
						{
							if (o == null)
								message.setShort(nullOffset, NULL_FLAG);
							else
							{
								byte[] bytes = (byte[]) o;

								if (bytes.length > length)
								{
									throw new FbException(String.format(
										"Byte array with length (%d) greater than max expected length (%d).",
										bytes.length, length));
								}

								message.setShort(nullOffset, NOT_NULL_FLAG);
								message.setShort(offset, (short) bytes.length);
								message.write(offset + 2, bytes, 0, bytes.length);
							}
						}
					};
				}
			}
		}, new DataTypeReg(byte[].class, "byte[]"));

		// java.sql.Blob
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				int type = metadata.getType(status, index);

				if (type != ISCConstants.SQL_BLOB)
				{
					String typeName = Optional.of(fbTypeNames.get(type))
						.map(Pair::getFirst)
						.orElse(String.valueOf(type));

					throw new FbException(
						String.format("Cannot use Java java.sql.Blob type for the Firebird type '%s'.", typeName));
				}

				return new Conversion() {
					@Override
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
						throws FbException
					{
						if (message.getShort(nullOffset) != NOT_NULL_FLAG)
							return null;

						long blobId = message.getLong(offset);

						try
						{
							FBConnection connection = (FBConnection) InternalContext.get().getConnection();
							GDSHelper gdsHelper = connection.getGDSHelper();

							return new FBBlob(gdsHelper, blobId);
						}
						catch (Exception e)
						{
							FbException.rethrow(e);
							return null;
						}
					}

					@Override
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset,
						Object o) throws FbException
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							Blob blob = (Blob) o;

							try
							{
								FBConnection connection = (FBConnection) InternalContext.get().getConnection();
								long blobId;
								FBBlob fbBlob;

								if (blob instanceof FBBlob &&
									(fbBlob = (FBBlob) blob).getGdsHelper() == connection.getGDSHelper())
								{
									blobId = fbBlob.getBlobId();
								}
								else
								{
									FBBlob outBlob = (FBBlob) connection.createBlob();

									try (InputStream in = blob.getBinaryStream())
									{
										outBlob.copyStream(in);
									}

									blobId = outBlob.getBlobId();
								}

								message.setShort(nullOffset, NOT_NULL_FLAG);
								message.setLong(offset, blobId);
							}
							catch (Exception e)
							{
								FbException.rethrow(e);
							}
						}
					}
				};
			}
		}, new DataTypeReg(Blob.class, "java.sql.Blob"));

		// short, Short
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_SHORT);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == short.class ? (Short)(short) 0 : null) :
							(Short) message.getShort(offset);
					}

					@Override
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setShort(offset, (short) o);
						}
					}
				};
			}
		}, new DataTypeReg(short.class, "short"), new DataTypeReg(Short.class, "Short", "java.lang.Short"));

		// int, Integer
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_LONG);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == int.class ? (Integer) 0 : null) :
							(Integer) message.getInt(offset);
					}

					@Override
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset, Object o)
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
		}, new DataTypeReg(int.class, "int"), new DataTypeReg(Integer.class, "Integer", "java.lang.Integer"));

		// long, Long
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_INT64);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == long.class ? (Long) 0L : null) :
							(Long) message.getLong(offset);
					}

					@Override
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setLong(offset, (long) o);
						}
					}
				};
			}
		}, new DataTypeReg(long.class, "long"), new DataTypeReg(Long.class, "Long", "java.lang.Long"));

		// float, Float
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_FLOAT);

				return new Conversion() {
					@Override
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == float.class ? (Float) 0.0f : null) :
							(Float) message.getFloat(offset);
					}

					@Override
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setFloat(offset, (float) o);
						}
					}
				};
			}
		}, new DataTypeReg(float.class, "float"), new DataTypeReg(Float.class, "Float", "java.lang.Float"));

		// double, Double
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_DOUBLE);

				return new Conversion() {
					@Override
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == double.class ? (Double) 0.0 : null) :
							(Double) message.getDouble(offset);
					}

					@Override
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset, Object o)
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
		}, new DataTypeReg(double.class, "double"), new DataTypeReg(Double.class, "Double", "java.lang.Double"));

		// BigDecimal
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
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
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
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
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset, Object o)
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
		}, new DataTypeReg(BigDecimal.class, "java.math.BigDecimal"));

		// java.util.Date, java.sql.Date
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_TYPE_DATE);

				return new Conversion() {
					@Override
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
					{
						if (message.getShort(nullOffset) != NOT_NULL_FLAG)
							return null;

						long t = fbDateToJava(message.getInt(offset));

						if (javaClass == java.util.Date.class)
							return new java.util.Date(t);
						else
							return new java.sql.Date(t);
					}

					@Override
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setInt(offset, javaDateToFb(((java.util.Date) o).getTime()));
						}
					}
				};
			}
		}, new DataTypeReg(java.util.Date.class, "java.util.Date"), new DataTypeReg(java.sql.Date.class, "java.sql.Date"));

		// java.sql.Time
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_TYPE_TIME);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
					{
						if (message.getShort(nullOffset) != NOT_NULL_FLAG)
							return null;

						long t = fbTimeToJava(message.getInt(offset));
						return new java.sql.Time(t);
					}

					@Override
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setInt(offset, javaTimeToFb(((java.sql.Time) o).getTime()));
						}
					}
				};
			}
		}, new DataTypeReg(java.sql.Time.class, "java.sql.Time"));

		// java.sql.Timestamp
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_TIMESTAMP);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
					{
						if (message.getShort(nullOffset) != NOT_NULL_FLAG)
							return null;

						long date = fbDateToJava(message.getInt(offset));
						long time = fbTimeToJava(message.getInt(offset + 4));
						java.sql.Time baseTime = new Time(0);

						return new java.sql.Timestamp(date + time - baseTime.getTime());
					}

					@Override
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);

							long t = ((java.sql.Timestamp) o).getTime();
							message.setInt(offset, javaDateToFb(t));
							message.setInt(offset + 4, javaTimeToFb(t));
						}
					}
				};
			}
		}, new DataTypeReg(java.sql.Timestamp.class, "java.sql.Timestamp"));

		// boolean, Boolean
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_BOOLEAN);

				return new Conversion() {
					@Override
					Object getFromMessage(IExternalContext context, Pointer message, int nullOffset, int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == boolean.class ? (Boolean) false : null) :
							(Boolean) (message.getByte(offset) != 0);
					}

					@Override
					void putInMessage(IExternalContext context, Pointer message, int nullOffset, int offset, Object o)
					{
						if (o == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setByte(offset, (byte) ((boolean) o ? 1 : 0));
						}
					}
				};
			}
		}, new DataTypeReg(boolean.class, "boolean"), new DataTypeReg(Boolean.class, "Boolean", "java.lang.Boolean"));

		// Object
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(IStatus status, Class<?> javaClass, IMessageMetadata metadata,
				IMetadataBuilder builder, int index) throws FbException
			{
				int type = metadata.getType(status, index);
				DataType defaultType = defaultDataTypes.get(type);

				if (defaultType == null)
				{
					String typeName = Optional.of(fbTypeNames.get(type))
						.map(Pair::getFirst)
						.orElse(String.valueOf(type));

					throw new FbException(
						String.format("Cannot use Java Object type for the Firebird type '%s'.", typeName));
				}

				return defaultType.setupConversion(status, javaClass, metadata, builder, index);
			}
		}, new DataTypeReg(Object.class, "Object", "java.lang.Object"));

		defaultDataTypes.put(ISCConstants.SQL_TEXT, dataTypesByClass.get(String.class));
		defaultDataTypes.put(ISCConstants.SQL_VARYING, dataTypesByClass.get(String.class));
		defaultDataTypes.put(ISCConstants.SQL_BLOB, dataTypesByClass.get(Blob.class));
		defaultDataTypes.put(ISCConstants.SQL_SHORT, dataTypesByClass.get(BigDecimal.class));
		defaultDataTypes.put(ISCConstants.SQL_LONG, dataTypesByClass.get(BigDecimal.class));
		defaultDataTypes.put(ISCConstants.SQL_INT64, dataTypesByClass.get(BigDecimal.class));
		defaultDataTypes.put(ISCConstants.SQL_FLOAT, dataTypesByClass.get(Float.class));
		defaultDataTypes.put(ISCConstants.SQL_D_FLOAT, dataTypesByClass.get(Float.class));
		defaultDataTypes.put(ISCConstants.SQL_DOUBLE, dataTypesByClass.get(Double.class));
		defaultDataTypes.put(ISCConstants.SQL_TYPE_DATE, dataTypesByClass.get(java.sql.Date.class));
		defaultDataTypes.put(ISCConstants.SQL_TYPE_TIME, dataTypesByClass.get(java.sql.Time.class));
		defaultDataTypes.put(ISCConstants.SQL_TIMESTAMP, dataTypesByClass.get(java.sql.Timestamp.class));
		defaultDataTypes.put(ISCConstants.SQL_BOOLEAN, dataTypesByClass.get(Boolean.class));
	}

	private static void addDataType(DataType dataType, DataTypeReg... dataTypesReg)
	{
		Arrays.stream(dataTypesReg).forEach(reg -> {
			Arrays.stream(reg.names).forEach(name -> javaClassesByName.put(name, reg.javaClass));
			dataTypesByClass.put(reg.javaClass, dataType);
		});
	}

	private static long fbDateToJava(int n)
	{
		ISC_DATE iscDate = new ISC_DATE();
		iscDate.value = n;
		int[] year = new int[1];
		int[] month = new int[1];
		int[] day = new int[1];
		Main.util.decodeDate(iscDate, year, month, day);

		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(0);
		calendar.set(Calendar.YEAR, year[0]);
		calendar.set(Calendar.MONTH, month[0] - 1);
		calendar.set(Calendar.DAY_OF_MONTH, day[0]);

		return calendar.getTimeInMillis();
	}

	private static int javaDateToFb(long n)
	{
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(n);

		ISC_DATE iscDate = Main.util.encodeDate(
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH) + 1,
			calendar.get(Calendar.DAY_OF_MONTH));

		return iscDate.value;
	}

	private static long fbTimeToJava(int n)
	{
		ISC_TIME iscTime = new ISC_TIME();
		iscTime.value = n;
		int[] hours = new int[1];
		int[] minutes = new int[1];
		int[] seconds = new int[1];
		int[] fractions = new int[1];
		Main.util.decodeTime(iscTime, hours, minutes, seconds, fractions);

		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(0);
		calendar.set(Calendar.HOUR_OF_DAY, hours[0]);
		calendar.set(Calendar.MINUTE, minutes[0]);
		calendar.set(Calendar.SECOND, seconds[0]);
		calendar.set(Calendar.MILLISECOND, fractions[0] / 10);

		return calendar.getTimeInMillis();
	}

	private static int javaTimeToFb(long n)
	{
		Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(n);

		ISC_TIME iscTime = Main.util.encodeTime(
			calendar.get(Calendar.HOUR_OF_DAY),
			calendar.get(Calendar.MINUTE),
			calendar.get(Calendar.SECOND),
			calendar.get(Calendar.MILLISECOND) * 10);

		return iscTime.value;
	}

	public static IExternalEngine create(String securityDatabase)
	{
		ExternalEngine wrapped = new ExternalEngine(securityDatabase);
		wrapped.wrapper = JnaUtil.pin(new IExternalEngine(wrapped));
		return wrapped.wrapper;
	}

	@Override
	public void addRef()
	{
		refCounter.incrementAndGet();
	}

	@Override
	public int release()
	{
		if (refCounter.decrementAndGet() == 0)
		{
			JnaUtil.unpin(wrapper);
			return 0;
		}
		else
			return 1;
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
	public void open(IStatus status, IExternalContext context, Pointer charSet, int charSetSize) throws FbException
	{
		try
		{
			sharedData = sharedDataMap.computeIfAbsent(context.getDatabaseName(), key -> {
				try
				{
					return new SharedData(key);
				}
				catch (Exception e)
				{
					throw new RuntimeException(e);
				}
			});
		}
		catch (Throwable t)
		{
			FbException.rethrow(t);
		}
	}

	@Override
	public void openAttachment(IStatus status, IExternalContext context) throws FbException
	{
		sharedData.openAttachment(status, context);
	}

	@Override
	public void closeAttachment(IStatus status, IExternalContext context) throws FbException
	{
		try
		{
			if (sharedData.closeAttachment(status, context))
				sharedDataMap.remove(context.getDatabaseName());
		}
		catch (Throwable t)
		{
			FbException.rethrow(t);
		}
	}

	@Override
	public IExternalFunction makeFunction(IStatus status, IExternalContext context,
		IRoutineMetadata metadata, IMetadataBuilder inBuilder, IMetadataBuilder outBuilder) throws FbException
	{
		Routine routine = getRoutine(status, context, metadata, inBuilder, outBuilder, Routine.Type.FUNCTION);
		return ExternalFunction.create(routine);
	}

	@Override
	public IExternalProcedure makeProcedure(IStatus status, IExternalContext context,
		IRoutineMetadata metadata, IMetadataBuilder inBuilder, IMetadataBuilder outBuilder) throws FbException
	{
		Routine routine = getRoutine(status, context, metadata, inBuilder, outBuilder, Routine.Type.PROCEDURE);
		return ExternalProcedure.create(routine);
	}

	@Override
	public IExternalTrigger makeTrigger(IStatus status, IExternalContext context,
		IRoutineMetadata metadata, IMetadataBuilder fieldsBuilder) throws FbException
	{
		System.out.println("makeTrigger");
		return null;
	}

	private Routine getRoutine(IStatus status, IExternalContext context, IRoutineMetadata metadata,
		IMetadataBuilder inBuilder, IMetadataBuilder outBuilder, Routine.Type type) throws FbException
	{
		try
		{
			String entryPoint = metadata.getEntryPoint(status);
			String invalidMethodSignatureMsg = String.format("Invalid method signature: '%s'", entryPoint);
			int paramsStart = entryPoint.indexOf('(');
			int methodStart = entryPoint.lastIndexOf('.', paramsStart) + 1;
			String className = entryPoint.substring(0, methodStart - 1).trim();
			String methodName = entryPoint.substring(methodStart, paramsStart).trim();

			Class<?> clazz = runInClassLoader(status, context, className, methodName,
				() -> Class.forName(className, true, sharedData.classLoader));

			Routine routine = new Routine(status, metadata, this, type);
			ArrayList<Class<?>> paramTypes = new ArrayList<>();

			int[] pos = {paramsStart + 1};

			skipBlanks(entryPoint, pos);

			IMessageMetadata inMetadata = metadata.getInputMetadata(status);
			try
			{
				int inCount = inMetadata.getCount(status);

				IMessageMetadata outMetadata = metadata.getOutputMetadata(status);
				try
				{
					int outCount = outMetadata.getCount(status);

					if (peekChar(entryPoint, pos) == ')')
						++pos[0];
					else
					{
						int n = 0;

						do
						{
							++n;

							boolean isOutput = n > inCount && n <= inCount + outCount;
							Parameter parameter = getDataType(entryPoint, pos, isOutput);

							IMessageMetadata inOutMetadata;
							int index;

							if (isOutput)
							{
								routine.outputParameters.add(parameter);
								inOutMetadata = outMetadata;
								index = n - 1 - inCount;
							}
							else
							{
								routine.inputParameters.add(parameter);
								inOutMetadata = inMetadata;
								index = n - 1;
							}

							parameter.name = inOutMetadata.getField(status, index);
							parameter.type = fbTypeNames.get(inOutMetadata.getType(status, index));

							paramTypes.add(parameter.javaClass);

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

					if (pos[0] < entryPoint.length() && getChar(entryPoint, pos) == '!')
					{
						skipBlanks(entryPoint, pos);
						routine.nameInfo = entryPoint.substring(pos[0]);
					}
					else if (pos[0] != entryPoint.length())
						throw new FbException(invalidMethodSignatureMsg);

					switch (type)
					{
						case FUNCTION:
						{
							assert outCount == 1;

							if (paramTypes.size() != inCount)
							{
								throw new FbException(String.format("Number of parameters (%d) in the Java method " +
									"does not match the number of parameters (%d) in the function declaration",
									paramTypes.size(), inCount));
							}

							routine.method = clazz.getMethod(methodName, paramTypes.toArray(new Class<?> [0]));

							DataType returnType = dataTypesByClass.get(routine.method.getReturnType());

							if (returnType == null)
							{
								throw new FbException(String.format("Unrecognized data type: '%s'",
									routine.method.getReturnType().getName()));
							}

							Parameter parameter = new Parameter(returnType, routine.method.getReturnType());
							parameter.type = fbTypeNames.get(outMetadata.getType(status, 0));

							routine.outputParameters.add(parameter);
							break;
						}

						case PROCEDURE:
							if (paramTypes.size() != inCount + outCount)
							{
								throw new FbException(String.format("Number of parameters (%d) in the Java method " +
									"does not match the number of parameters (%d + %d) in the procedure declaration",
									paramTypes.size(), inCount, outCount));
							}

							ArrayList<Class<?>> paramTypes2 = new ArrayList<>();

							paramTypes
								.stream()
								.limit(inCount)
								.forEach(p -> paramTypes2.add(p));

							paramTypes
								.stream()
								.skip(inCount)
								.forEach(p -> paramTypes2.add(Array.newInstance(p, 0).getClass()));

							routine.method = clazz.getMethod(methodName, paramTypes2.toArray(new Class<?> [0]));

							if (routine.method.getReturnType() != void.class &&
								!ExternalResultSet.class.isAssignableFrom(routine.method.getReturnType()))
							{
								throw new FbException("Java method for a procedure must return void or an class " +
									"implementing ExternalResultSet interface");	//// TODO: package name
							}

							break;
					}

					routine.setupParameters(status, routine.inputParameters, routine.inputMetadata,
						inMetadata, inBuilder);

					routine.setupParameters(status, routine.outputParameters, routine.outputMetadata,
						outMetadata, outBuilder);
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

	private Parameter getDataType(String s, int[] pos, boolean arrayRef) throws FbException
	{
		String name = getName(s, pos);

		while (pos[0] < s.length() && peekChar(s, pos) == '.')
		{
			++pos[0];
			name += "." + getName(s, pos);
		}

		skipBlanks(s, pos);

		while (peekChar(s, pos) == '[')
		{
			++pos[0];
			skipBlanks(s, pos);

			if (getChar(s, pos) == ']')
			{
				skipBlanks(s, pos);
				name += "[]";
			}
			else
				throw new FbException("Expected ']'.");
		}

		if (arrayRef)
		{
			if (name.endsWith("[]"))
				name = name.substring(0, name.length() - 2);
			else
				throw new FbException(String.format("Expected an array type but found '%s'", name));
		}

		Class<?> javaClass = javaClassesByName.get(name);

		if (javaClass == null)
			throw new FbException(String.format("Unrecognized data type: '%s'", name));

		return new Parameter(dataTypesByClass.get(javaClass), javaClass);
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

	<T> T runInClassLoader(IStatus status, IExternalContext context, String className, String methodName,
		CallableThrowable<T> callable) throws Throwable
	{
		ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(sharedData.classLoader);

			Subject subj = DbPolicy.getUserSubject(status, context, sharedData.classLoader);

			ProtectionDomain[] protectionDomains = {new ProtectionDomain(sharedData.classLoader.codeSource,
				sharedData.classLoader.codeSourcePermission, sharedData.classLoader,
				subj.getPrincipals().toArray(new Principal[1]))};

			AccessControlContext acc = new AccessControlContext(protectionDomains);

			try
			{
				return Subject.doAsPrivileged(subj, new PrivilegedExceptionAction<T>() {
					@Override
					public T run() throws Exception
					{
						try
						{
							return callable.call();
						}
						catch (Throwable t)
						{
							// We cannot pass a Throwable with PrivilegedExceptionAction, so we enclose it with an
							// Exception. This is the reason we call getClause() two times below.
							throw new Exception(t);
						}
					}
				}, acc);
			}
			catch (PrivilegedActionException privilegedException)
			{
				// Lets filter the stack trace to remove garbage from user POV. We start removing the
				// privilegedException trace, then we remove up to the user class and method name.
				// From all frames.

				Throwable userException = privilegedException.getCause().getCause();	// explained above
				StackTraceElement[] privilegedTrace = privilegedException.getStackTrace();

				for (Throwable currentException = userException;
					 currentException != null;
					 currentException = currentException.getCause())
				{
					StackTraceElement[] currentTrace = currentException.getStackTrace();

					for (int i = currentTrace.length - 1, j = privilegedTrace.length - 1; i >= 0; --i)
					{
						if (!currentTrace[i].equals(privilegedTrace[j]))
							break;

						if (j-- == 0)
						{
							int k = i;

							while (--k >= 0 &&
								!(currentTrace[k].getClassName().equals(className) &&
								  (currentTrace[k].getMethodName().equals(methodName) ||
								   "<clinit>".equals(currentTrace[k].getMethodName()))))
							{
							}

							if (k < 0 &&
								!(currentException instanceof ExceptionInInitializerError ||
								  currentException instanceof NoClassDefFoundError))
							{
								k = i - 1;
							}

							currentException.setStackTrace(Arrays.copyOf(currentTrace, k + 1));
							break;
						}
					}
				}

				throw userException;
			}
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(oldClassLoader);
		}
	}
}
