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
import java.math.RoundingMode;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.security.AccessControlContext;
import java.security.AccessController;
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
import org.firebirdsql.jdbc.FirebirdBlob;

import com.sun.jna.Pointer;


final class ExternalEngine implements IExternalEngineIntf
{
	private static final IEncodingFactory encodingFactory = EncodingFactory.getPlatformDefault();
	private static final Map<String, SharedData> sharedDataMap = new ConcurrentHashMap<>();
	static final Map<Integer, Pair<String, Integer>> fbTypeNames;
	static final Map<Class<?>, DataType> dataTypesByClass;
	private static final Map<String, Class<?>> javaClassesByName;
	private static final Map<Integer, DataType> defaultDataTypes;
	private IExternalEngine wrapper;
	private final AtomicInteger refCounter = new AtomicInteger(1);
	private SharedData sharedData;

	private static final class DataTypeReg
	{
		DataTypeReg(final Class<?> javaClass, final String... names)
		{
			this.javaClass = javaClass;
			this.names = names;
		}

		final Class<?> javaClass;
		final String[] names;
	}

	private static final class SharedData
	{
		final AtomicInteger attachmentCounter = new AtomicInteger(0);
		final DbClassLoader classLoader;

		SharedData(final String databaseName) throws SQLException, MalformedURLException
		{
			final URL contextUrl = new URL(null, "fbjava:/", new URLStreamHandler() {
				@Override
				protected URLConnection openConnection(URL url) throws IOException
				{
					return new BlobConnection(url);
				}
			});

			classLoader = new DbClassLoader(databaseName, contextUrl, getClass().getClassLoader());
		}

		void openAttachment(final IStatus status, final IExternalContext context)
		{
			attachmentCounter.incrementAndGet();
		}

		boolean closeAttachment(final IStatus status, final IExternalContext context) throws IOException
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

	private ExternalEngine(final String securityDatabase)
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
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				final int type = metadata.getType(status, index);
				int length = metadata.getLength(status, index);
				final Encoding encoding =
					encodingFactory.getEncodingForCharacterSetId(metadata.getCharSet(status, index));

				switch (type)
				{
					case ISCConstants.SQL_TEXT:
						builder.setType(status, index, ISCConstants.SQL_VARYING);
						break;

					case ISCConstants.SQL_VARYING:
						break;

					case ISCConstants.SQL_BLOB:
					{
						final Conversion byteConversion = dataTypesByClass.get(byte[].class).setupConversion(
							status, byte[].class, metadata, builder, index);

						return new Conversion() {
							@Override
							Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
								final int nullOffset, final int offset) throws FbException
							{
								if (message.getShort(nullOffset) != NOT_NULL_FLAG)
									return null;

								return byteConversion.getFromMessagePrivileged(context, message,
									nullOffset, offset);
							}

							@Override
							Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
								throws FbException
							{
								return encoding.decodeFromCharset(
									(byte[]) byteConversion.getFromMessageUnprivileged(context, result));
							}

							@Override
							Object putInMessageUnprivileged(final IExternalContext context, final Object o)
								throws FbException
							{
								if (o == null)
									return null;

								final byte[] bytes = encoding.encodeToCharset((String) o);
								return byteConversion.putInMessageUnprivileged(context, bytes);
							}

							@Override
							void putInMessagePrivileged(final IExternalContext context, final Pointer message,
								final int nullOffset, final int offset, final Object o, final Object result)
									throws FbException
							{
								byteConversion.putInMessagePrivileged(context, message, nullOffset, offset, o, result);
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
						final String typeName = Optional.ofNullable(fbTypeNames.get(type))
							.map(Pair::getFirst)
							.orElse(String.valueOf(type));

						throw new FbException(
							String.format("Cannot use Java String type for the Firebird type '%s'.", typeName));
					}
				}

				final int finalLength = length;

				return new Conversion() {
					@Override
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
					{
						if (message.getShort(nullOffset) != NOT_NULL_FLAG)
							return null;

						final int length = Short.toUnsignedInt(message.getShort(offset));
						return message.getByteArray(offset + 2, length);
					}

					@Override
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
					{
						return encoding.decodeFromCharset((byte[]) result);
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o) throws FbException
					{
						if (o == null)
							return null;

						final byte[] bytes = encoding.encodeToCharset((String) o);

						if (bytes.length > finalLength)
						{
							throw new FbException(String.format(
								"String with length (%d) bytes greater than max expected length (%d).",
								bytes.length, finalLength));
						}

						return bytes;
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset, final Object o, final Object result)
					{
						if (result == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							final byte[] bytes = (byte[]) result;

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
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				final int type = metadata.getType(status, index);
				final int length = metadata.getLength(status, index);

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
						final String typeName = Optional.ofNullable(fbTypeNames.get(type))
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
						Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
							final int nullOffset, final int offset)
						{
							if (message.getShort(nullOffset) != NOT_NULL_FLAG)
								return null;

							return message.getLong(offset);
						}

						@Override
						Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
							throws FbException
						{
							final long blobId = (long) result;

							try
							{
								final FBConnection connection = (FBConnection) InternalContext.get().getConnection();
								final GDSHelper gdsHelper = connection.getGDSHelper();
								final FBBlob blob = new FBBlob(gdsHelper, blobId);

								try (final InputStream in = blob.getBinaryStream())
								{
									final byte[] bytes = new byte[(int) blob.length()];
									in.read(bytes);
									return bytes;
								}
							}
							catch (final Exception e)
							{
								FbException.rethrow(e);
								return null;
							}
						}

						@Override
						Object putInMessageUnprivileged(final IExternalContext context, final Object o) throws FbException
						{
							if (o == null)
								return null;

							final byte[] bytes = (byte[]) o;

							try
							{
								final FBConnection connection = (FBConnection) InternalContext.get().getConnection();
								final FBBlob blob = (FBBlob) connection.createBlob();

								try (final OutputStream out = blob.setBinaryStream(1))
								{
									out.write(bytes);
								}

								return blob.getBlobId();
							}
							catch (final Exception e)
							{
								FbException.rethrow(e);
							}

							return null;
						}

						@Override
						void putInMessagePrivileged(final IExternalContext context, final Pointer message,
							final int nullOffset, final int offset, final Object o, final Object result)
						{
							if (result == null)
								message.setShort(nullOffset, NULL_FLAG);
							else
							{
								final long blobId = (long) result;

								message.setShort(nullOffset, NOT_NULL_FLAG);
								message.setShort(nullOffset, NOT_NULL_FLAG);
								message.setLong(offset, blobId);
							}
						}
					};
				}
				else
				{
					return new Conversion() {
						@Override
						Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
							final int nullOffset, final int offset)
						{
							if (message.getShort(nullOffset) != NOT_NULL_FLAG)
								return null;

							final int length = Short.toUnsignedInt(message.getShort(offset));
							return message.getByteArray(offset + 2, length);
						}

						@Override
						Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
						{
							return result;
						}

						@Override
						Object putInMessageUnprivileged(final IExternalContext context, final Object o)
						{
							return o;
						}

						@Override
						void putInMessagePrivileged(final IExternalContext context, final Pointer message,
							final int nullOffset, final int offset, final Object o, final Object result)
								throws FbException
						{
							if (result == null)
								message.setShort(nullOffset, NULL_FLAG);
							else
							{
								final byte[] bytes = (byte[]) result;

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

		// java.sql.Blob and org.firebirdsql.jdbc.FirebirdBlob
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				final int type = metadata.getType(status, index);

				if (type != ISCConstants.SQL_BLOB)
				{
					String typeName = Optional.ofNullable(fbTypeNames.get(type))
						.map(Pair::getFirst)
						.orElse(String.valueOf(type));

					throw new FbException(
						String.format("Cannot use Java java.sql.Blob type for the Firebird type '%s'.", typeName));
				}

				return new Conversion() {
					@Override
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
					{
						if (message.getShort(nullOffset) != NOT_NULL_FLAG)
							return null;

						return message.getLong(offset);
					}

					@Override
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
						throws FbException
					{
						try
						{
							final long blobId = (long) result;

							final FBConnection connection = (FBConnection) InternalContext.get().getConnection();
							final GDSHelper gdsHelper = connection.getGDSHelper();

							return new FBBlob(gdsHelper, blobId);
						}
						catch (final Exception e)
						{
							FbException.rethrow(e);
							return null;
						}
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o) throws FbException
					{
						if (o == null)
							return o;

						final Blob blob = (Blob) o;

						try
						{
							final FBConnection connection = (FBConnection) InternalContext.get().getConnection();
							final FBBlob fbBlob;

							if (blob instanceof FBBlob &&
								(fbBlob = (FBBlob) blob).getGdsHelper() == connection.getGDSHelper())
							{
								return fbBlob.getBlobId();
							}
							else
							{
								final FBBlob outBlob = (FBBlob) connection.createBlob();

								try (InputStream in = blob.getBinaryStream())
								{
									outBlob.copyStream(in);
								}

								return outBlob.getBlobId();
							}
						}
						catch (final Exception e)
						{
							FbException.rethrow(e);
							return null;
						}
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset, final Object o, final Object result) throws FbException
					{
						if (result == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							final long blobId = (long) result;

							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setLong(offset, blobId);
						}
					}
				};
			}
		}, new DataTypeReg(Blob.class, "java.sql.Blob"), new DataTypeReg(FirebirdBlob.class, "org.firebirdsql.jdbc.FirebirdBlob"));

		// short, Short
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_SHORT);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == short.class ? (Short)(short) 0 : null) :
							(Short) message.getShort(offset);
					}

					@Override
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
					{
						return result;
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o)
					{
						return o;
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message, final int nullOffset, final int offset,
						final Object o, final Object result)
					{
						if (result == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setShort(offset, (short) result);
						}
					}
				};
			}
		}, new DataTypeReg(short.class, "short"), new DataTypeReg(Short.class, "Short", "java.lang.Short"));

		// int, Integer
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_LONG);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == int.class ? (Integer) 0 : null) :
							(Integer) message.getInt(offset);
					}

					@Override
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
					{
						return result;
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o)
					{
						return o;
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message, final int nullOffset, final int offset,
						final Object o, final Object result)
					{
						if (result == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setInt(offset, (int) result);
						}
					}
				};
			}
		}, new DataTypeReg(int.class, "int"), new DataTypeReg(Integer.class, "Integer", "java.lang.Integer"));

		// long, Long
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_INT64);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == long.class ? (Long) 0L : null) :
							(Long) message.getLong(offset);
					}

					@Override
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
					{
						return result;
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o)
					{
						return o;
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset, final Object o, final Object result)
					{
						if (result == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setLong(offset, (long) result);
						}
					}
				};
			}
		}, new DataTypeReg(long.class, "long"), new DataTypeReg(Long.class, "Long", "java.lang.Long"));

		// float, Float
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_FLOAT);

				return new Conversion() {
					@Override
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == float.class ? (Float) 0.0f : null) :
							(Float) message.getFloat(offset);
					}

					@Override
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
					{
						return result;
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o)
					{
						return o;
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset, final Object o, final Object result)
					{
						if (result == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setFloat(offset, (float) result);
						}
					}
				};
			}
		}, new DataTypeReg(float.class, "float"), new DataTypeReg(Float.class, "Float", "java.lang.Float"));

		// double, Double
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_DOUBLE);

				return new Conversion() {
					@Override
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == double.class ? (Double) 0.0 : null) :
							(Double) message.getDouble(offset);
					}

					@Override
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
					{
						return result;
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o)
					{
						return o;
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset, final Object o, final Object result)
					{
						if (result == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setDouble(offset, (double) result);
						}
					}
				};
			}
		}, new DataTypeReg(double.class, "double"), new DataTypeReg(Double.class, "Double", "java.lang.Double"));

		// BigDecimal
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				final int initialType = metadata.getType(status, index);
				final int initialScale = metadata.getScale(status, index);
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
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
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
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
					{
						return result;
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o)
					{
						if (o == null)
							return null;

						final BigDecimal bigVal = (BigDecimal) o;

						switch (type)
						{
							case ISCConstants.SQL_FLOAT:
								return bigVal.floatValue();

							case ISCConstants.SQL_DOUBLE:
								return bigVal.doubleValue();
						}

						BigInteger bigInt = bigVal.setScale(-scale, RoundingMode.HALF_UP).unscaledValue();

						//// FIXME: overflow

						switch (type)
						{
							case ISCConstants.SQL_SHORT:
								return bigInt.shortValue();

							case ISCConstants.SQL_LONG:
								return bigInt.intValue();

							case ISCConstants.SQL_INT64:
								return bigInt.longValue();

							default:
								assert false;
								return null;
						}
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset, final Object o, final Object result)
					{
						if (result == null)
						{
							message.setShort(nullOffset, NULL_FLAG);
							return;
						}

						message.setShort(nullOffset, NOT_NULL_FLAG);

						if (result instanceof Float)
							message.setFloat(offset, (float) result);
						else if (result instanceof Double)
							message.setDouble(offset, (double) result);
						else if (result instanceof Short)
							message.setShort(offset, (short) result);
						else if (result instanceof Integer)
							message.setInt(offset, (int) result);
						else if (result instanceof Long)
							message.setLong(offset, (long) result);
						else
							assert false;
					}
				};
			}
		}, new DataTypeReg(BigDecimal.class, "java.math.BigDecimal"));

		// java.util.Date, java.sql.Date
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_TYPE_DATE);

				return new Conversion() {
					@Override
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
					{
						if (message.getShort(nullOffset) != NOT_NULL_FLAG)
							return null;

						final long t = fbDateToJava(message.getInt(offset));

						if (javaClass == java.util.Date.class)
							return new java.util.Date(t);
						else
							return new java.sql.Date(t);
					}

					@Override
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
					{
						return result;
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o)
					{
						if (o == null)
							return null;

						return (long) ((java.util.Date) o).getTime();
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset, final Object o, final Object result) throws FbException
					{
						if (result == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							final long t = (long) result;

							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setInt(offset, javaDateToFb(t));
						}
					}
				};
			}
		}, new DataTypeReg(java.util.Date.class, "java.util.Date"), new DataTypeReg(java.sql.Date.class, "java.sql.Date"));

		// java.sql.Time
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_TYPE_TIME);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
					{
						if (message.getShort(nullOffset) != NOT_NULL_FLAG)
							return null;

						final long t = fbTimeToJava(message.getInt(offset));
						return new java.sql.Time(t);
					}

					@Override
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
					{
						return result;
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o)
					{
						if (o == null)
							return null;

						return (long) ((java.util.Date) o).getTime();
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset, final Object o, final Object result) throws FbException
					{
						if (result == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							final long t = (long) result;

							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setInt(offset, javaTimeToFb(t));
						}
					}
				};
			}
		}, new DataTypeReg(java.sql.Time.class, "java.sql.Time"));

		// java.sql.Timestamp
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_TIMESTAMP);
				builder.setScale(status, index, 0);

				return new Conversion() {
					@Override
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
					{
						if (message.getShort(nullOffset) != NOT_NULL_FLAG)
							return null;

						final long date = fbDateToJava(message.getInt(offset));
						final long time = fbTimeToJava(message.getInt(offset + 4));
						final java.sql.Time baseTime = new Time(0);

						return new java.sql.Timestamp(date + time - baseTime.getTime());
					}

					@Override
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
					{
						return result;
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o)
					{
						if (o == null)
							return null;

						return (long) ((java.sql.Time) o).getTime();
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset, final Object o, final Object result) throws FbException
					{
						if (result == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							final long t = (long) result;

							message.setShort(nullOffset, NOT_NULL_FLAG);
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
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				builder.setType(status, index, ISCConstants.SQL_BOOLEAN);

				return new Conversion() {
					@Override
					Object getFromMessagePrivileged(final IExternalContext context, final Pointer message,
						final int nullOffset, final int offset)
					{
						return message.getShort(nullOffset) != NOT_NULL_FLAG ?
							(javaClass == boolean.class ? (Boolean) false : null) :
							(Boolean) (message.getByte(offset) != 0);
					}

					@Override
					Object getFromMessageUnprivileged(final IExternalContext context, final Object result)
					{
						return result;
					}

					@Override
					Object putInMessageUnprivileged(final IExternalContext context, final Object o)
					{
						return o;
					}

					@Override
					void putInMessagePrivileged(final IExternalContext context, final Pointer message, final int nullOffset, final int offset,
						final Object o, final Object result)
					{
						if (result == null)
							message.setShort(nullOffset, NULL_FLAG);
						else
						{
							message.setShort(nullOffset, NOT_NULL_FLAG);
							message.setByte(offset, (byte) ((boolean) result ? 1 : 0));
						}
					}
				};
			}
		}, new DataTypeReg(boolean.class, "boolean"), new DataTypeReg(Boolean.class, "Boolean", "java.lang.Boolean"));

		// Object
		addDataType(new DataType() {
			@Override
			Conversion setupConversion(final IStatus status, final Class<?> javaClass, final IMessageMetadata metadata,
				final IMetadataBuilder builder, final int index) throws FbException
			{
				int type = metadata.getType(status, index);
				DataType defaultType = defaultDataTypes.get(type);

				if (defaultType == null)
				{
					String typeName = Optional.ofNullable(fbTypeNames.get(type))
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

	private static long fbDateToJava(final int n)
	{
		final ISC_DATE iscDate = new ISC_DATE();
		iscDate.value = n;
		final int[] year = new int[1];
		final int[] month = new int[1];
		final int[] day = new int[1];
		Main.util.decodeDate(iscDate, year, month, day);

		final Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(0);
		calendar.set(Calendar.YEAR, year[0]);
		calendar.set(Calendar.MONTH, month[0] - 1);
		calendar.set(Calendar.DAY_OF_MONTH, day[0]);

		return calendar.getTimeInMillis();
	}

	private static int javaDateToFb(final long n)
	{
		final Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(n);

		final ISC_DATE iscDate = Main.util.encodeDate(
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH) + 1,
			calendar.get(Calendar.DAY_OF_MONTH));

		return iscDate.value;
	}

	private static long fbTimeToJava(final int n)
	{
		final ISC_TIME iscTime = new ISC_TIME();
		iscTime.value = n;
		final int[] hours = new int[1];
		final int[] minutes = new int[1];
		final int[] seconds = new int[1];
		final int[] fractions = new int[1];
		Main.util.decodeTime(iscTime, hours, minutes, seconds, fractions);

		final Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(0);
		calendar.set(Calendar.HOUR_OF_DAY, hours[0]);
		calendar.set(Calendar.MINUTE, minutes[0]);
		calendar.set(Calendar.SECOND, seconds[0]);
		calendar.set(Calendar.MILLISECOND, fractions[0] / 10);

		return calendar.getTimeInMillis();
	}

	private static int javaTimeToFb(final long n)
	{
		final Calendar calendar = Calendar.getInstance();
		calendar.setTimeInMillis(n);

		final ISC_TIME iscTime = Main.util.encodeTime(
			calendar.get(Calendar.HOUR_OF_DAY),
			calendar.get(Calendar.MINUTE),
			calendar.get(Calendar.SECOND),
			calendar.get(Calendar.MILLISECOND) * 10);

		return iscTime.value;
	}

	public static IExternalEngine create(final String securityDatabase)
	{
		final ExternalEngine wrapped = new ExternalEngine(securityDatabase);
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
				catch (final Exception e)
				{
					throw new RuntimeException(e);
				}
			});
		}
		catch (final Throwable t)
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
		catch (final Throwable t)
		{
			FbException.rethrow(t);
		}
	}

	@Override
	public IExternalFunction makeFunction(IStatus status, IExternalContext context,
		IRoutineMetadata metadata, IMetadataBuilder inBuilder, IMetadataBuilder outBuilder) throws FbException
	{
		try
		{
			return doPrivileged(() -> {
				Routine routine = getRoutine(status, context, metadata, inBuilder, outBuilder, Routine.Type.FUNCTION);
				return ExternalFunction.create(routine);
			});
		}
		catch (final Throwable t)
		{
			FbException.rethrow(t);
			return null;
		}
	}

	@Override
	public IExternalProcedure makeProcedure(IStatus status, IExternalContext context,
		IRoutineMetadata metadata, IMetadataBuilder inBuilder, IMetadataBuilder outBuilder) throws FbException
	{
		try
		{
			return doPrivileged(() -> {
				final Routine routine = getRoutine(status, context, metadata,
					inBuilder, outBuilder, Routine.Type.PROCEDURE);
				return ExternalProcedure.create(routine);
			});
		}
		catch (final Throwable t)
		{
			FbException.rethrow(t);
			return null;
		}
	}

	@Override
	public IExternalTrigger makeTrigger(IStatus status, IExternalContext context,
		IRoutineMetadata metadata, IMetadataBuilder fieldsBuilder) throws FbException
	{
		try
		{
			return doPrivileged(() -> {
				final Routine routine = getRoutine(status, context, metadata, fieldsBuilder,
					null, Routine.Type.TRIGGER);
				return ExternalTrigger.create(routine);
			});
		}
		catch (final Throwable t)
		{
			FbException.rethrow(t);
			return null;
		}
	}

	private Routine getRoutine(IStatus status, IExternalContext context, IRoutineMetadata metadata,
		IMetadataBuilder inBuilder, IMetadataBuilder outBuilder, Routine.Type type) throws Throwable
	{
		final String entryPoint = metadata.getEntryPoint(status);
		final String invalidMethodSignatureMsg = String.format("Invalid method signature: '%s'", entryPoint);
		final int paramsStart = entryPoint.indexOf('(');

		if (paramsStart == -1)
			throw new FbException(invalidMethodSignatureMsg);

		final int methodStart = entryPoint.lastIndexOf('.', paramsStart) + 1;

		if (methodStart == 0)
			throw new FbException(invalidMethodSignatureMsg);

		final String className = entryPoint.substring(0, methodStart - 1).trim();
		final String methodName = entryPoint.substring(methodStart, paramsStart).trim();

		final Class<?> clazz = runInClassLoader(status, context, className, methodName,
			() -> Class.forName(className, true, sharedData.classLoader));

		final Routine routine = new Routine(status, metadata, this, type);
		final ArrayList<Class<?>> paramTypes = new ArrayList<>();

		final int[] pos = {paramsStart + 1};

		skipBlanks(entryPoint, pos);

		final IMessageMetadata inMetadata = type == Routine.Type.TRIGGER ? null : metadata.getInputMetadata(status);
		try
		{
			final int inCount = inMetadata == null ? 0 : inMetadata.getCount(status);

			final IMessageMetadata outMetadata = type == Routine.Type.TRIGGER ?
				null : metadata.getOutputMetadata(status);
			try
			{
				final int outCount = outMetadata == null ? 0 : outMetadata.getCount(status);

				if (peekChar(entryPoint, pos) == ')')
					++pos[0];
				else
				{
					if (type == Routine.Type.TRIGGER)
						throw new FbException(invalidMethodSignatureMsg);

					int n = 0;

					do
					{
						++n;

						final boolean isOutput = n > inCount && n <= inCount + outCount;
						final Parameter parameter = getDataType(entryPoint, pos, isOutput);

						final IMessageMetadata inOutMetadata;
						final int index;

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

						final char c = getChar(entryPoint, pos);

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

				//// TODO: Parameters prefixed with a Context parameter.
				//// TODO: Zero parameters.

				switch (type)
				{
					case FUNCTION:
					{
						assert outCount == 1;

						if (paramTypes.size() != inCount && paramTypes.size() != 0)
						{
							throw new FbException(String.format("Number of parameters (%d) in the Java method " +
								"does not match the number of parameters (%d) in the function declaration",
								paramTypes.size(), inCount));
						}

						routine.method = clazz.getMethod(methodName, paramTypes.toArray(new Class<?> [0]));

						final DataType returnType = dataTypesByClass.get(routine.method.getReturnType());

						if (returnType == null)
						{
							throw new FbException(String.format("Unrecognized data type: '%s'",
								routine.method.getReturnType().getName()));
						}

						final Parameter parameter = new Parameter(returnType, routine.method.getReturnType());
						parameter.type = fbTypeNames.get(outMetadata.getType(status, 0));

						routine.outputParameters.add(parameter);
						break;
					}

					case PROCEDURE:
					{
						if (paramTypes.size() != inCount + outCount && paramTypes.size() != 0)
						{
							throw new FbException(String.format("Number of parameters (%d) in the Java method " +
								"does not match the number of parameters (%d + %d) in the procedure declaration",
								paramTypes.size(), inCount, outCount));
						}

						final ArrayList<Class<?>> paramTypes2 = new ArrayList<>();

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
							throw new FbException(String.format(
								"Java method for a procedure must return void or an class implementing %s interface",
								ExternalResultSet.class.getName()));
						}

						break;
					}

					case TRIGGER:
					{
						routine.method = clazz.getMethod(methodName);

						if (routine.method.getReturnType() != void.class)
							throw new FbException("Java method for a trigger must return void");

						if (inBuilder != null)
						{
							final IMessageMetadata triggerMetadata = metadata.getTriggerMetadata(status);
							try
							{
								routine.setupParameters(status, routine.inputParameters, routine.inputMetadata,
									triggerMetadata, inBuilder);
							}
							finally
							{
								triggerMetadata.release();
							}
						}

						break;
					}
				}

				if (type != Routine.Type.TRIGGER)
				{
					routine.setupParameters(status, routine.inputParameters, routine.inputMetadata,
						inMetadata, inBuilder);

					routine.setupParameters(status, routine.outputParameters, routine.outputMetadata,
						outMetadata, outBuilder);
				}
			}
			finally
			{
				if (outMetadata != null)
					outMetadata.release();
			}
		}
		finally
		{
			if (inMetadata != null)
				inMetadata.release();
		}

		return routine;
	}

	private Parameter getDataType(final String s, final int[] pos, final boolean arrayRef) throws FbException
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

		final Class<?> javaClass = javaClassesByName.get(name);

		if (javaClass == null)
			throw new FbException(String.format("Unrecognized data type: '%s'", name));

		return new Parameter(dataTypesByClass.get(javaClass), javaClass);
	}

	private String getName(final String s, final int[] pos) throws FbException
	{
		if (pos[0] >= s.length())
			throw new FbException("Expected name but entry point end found.");

		final int start = pos[0];

		if (!Character.isJavaIdentifierStart((peekChar(s, pos))))
			throw new FbException(String.format("Expected name at entry point character position %d.", pos[0]));

		while (Character.isJavaIdentifierPart(getChar(s, pos)))
			;

		--pos[0];

		return s.substring(start, pos[0]);
	}

	private void skipBlanks(final String s, final int[] pos)
	{
		final int len = s.length();
		char c;

		while (pos[0] < len && ((c = s.charAt(pos[0])) == ' ' || c == '\t' || c == '\r' || c == '\n'))
			++pos[0];
	}

	private char getChar(final String s, final int[] pos) throws FbException
	{
		final char c = peekChar(s, pos);
		++pos[0];
		return c;
	}

	private char peekChar(final String s, final int[] pos) throws FbException
	{
		if (pos[0] >= s.length())
			throw new FbException("Expected a character but entry point end found.");

		return s.charAt(pos[0]);
	}

	<T> T runInClassLoader(final IStatus status, final IExternalContext context, final String className,
		final String methodName, final CallableThrowable<T> callable) throws Throwable
	{
		return doPrivileged(() -> runInClassLoader0(status, context, className, methodName, callable));
	}

	private <T> T runInClassLoader0(final IStatus status, final IExternalContext context,
		final String className, final String methodName, final CallableThrowable<T> callable) throws Throwable
	{
		final ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();
		try
		{
			Thread.currentThread().setContextClassLoader(sharedData.classLoader);

			final Subject subj = DbPolicy.getUserSubject(status, context, sharedData.classLoader);

			final ProtectionDomain[] protectionDomains = {new ProtectionDomain(sharedData.classLoader.codeSource,
				sharedData.classLoader.codeSourcePermission, sharedData.classLoader,
				subj.getPrincipals().toArray(new Principal[1]))};

			final AccessControlContext acc = new AccessControlContext(protectionDomains);

			return doAsPrivileged(subj, acc, () -> {
				try
				{
					return callable.call();
				}
				catch (final Throwable userException)
				{
					// Lets filter the stack trace to remove garbage from user POV. We remove up to the user class
					// and method name. From all frames.

					for (Throwable currentException = userException;
						 currentException != null;
						 currentException = currentException.getCause())
					{
						final StackTraceElement[] currentTrace = currentException.getStackTrace();

						for (int i = currentTrace.length - 1; i >= 0; --i)
						{
							if (currentTrace[i].getClassName().equals(className) &&
								currentTrace[i].getMethodName().equals(methodName))
							{
								currentException.setStackTrace(Arrays.copyOf(currentTrace, i + 1));
								break;
							}
						}
					}

					throw userException;
				}
			});
		}
		finally
		{
			Thread.currentThread().setContextClassLoader(oldClassLoader);
		}
	}

	interface PrivilegedThrowableAction<T>
	{
		T run() throws Throwable;
	}

	static <T> T doPrivileged(final PrivilegedThrowableAction<T> action) throws Throwable
	{
		try
		{
			return AccessController.doPrivileged(new PrivilegedExceptionAction<T>() {
				@Override
				public T run() throws Exception
				{
					try
					{
						return action.run();
					}
					catch (final Throwable t)
					{
						// We cannot pass a Throwable with PrivilegedExceptionAction, so we enclose it with an
						// Exception. This is the reason we call getClause() two times below.
						throw new Exception(t);
					}
				}
			});
		}
		catch (final PrivilegedActionException privilegedException)
		{
			throw privilegedException.getCause().getCause();	// user exception
		}
	}

	static <T> T doAsPrivileged(final Subject subject, final AccessControlContext acc,
		final PrivilegedThrowableAction<T> action) throws Throwable
	{
		try
		{
			return Subject.doAsPrivileged(subject, new PrivilegedExceptionAction<T>() {
				@Override
				public T run() throws Exception
				{
					try
					{
						return action.run();
					}
					catch (final Throwable t)
					{
						// We cannot pass a Throwable with PrivilegedExceptionAction, so we enclose it with an
						// Exception. This is the reason we call getClause() two times below.
						throw new Exception(t);
					}
				}
			}, acc);
		}
		catch (final PrivilegedActionException privilegedException)
		{
			throw privilegedException.getCause().getCause();	// user exception
		}
	}
}
