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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.firebirdsql.logging.Logger;
import org.firebirdsql.logging.LoggerFactory;


/**
 * URLConnection for blobs in plugin tables.
 *
 * @author <a href="mailto:adrianosf@gmail.com">Adriano dos Santos Fernandes</a>
 */
public class BlobConnection extends URLConnection
{
	private static final Logger log = LoggerFactory.getLogger(BlobConnection.class);

	public BlobConnection(URL url)
	{
		super(url);
	}

	@Override
	public void connect() throws IOException
	{
	}

	@Override
	public InputStream getInputStream() throws IOException
	{
		try
		{
			final DbClassLoader classLoader = (DbClassLoader) Thread.currentThread().getContextClassLoader();
			final Connection conn = classLoader.getConnection();
			final PreparedStatement stmt = conn.prepareStatement("select content from sqlj.read_jar(?)");
			ResultSet rs = null;

			try
			{
				String urlStr = url.toString().substring(8);	// "fbjava:/"
				stmt.setString(1, urlStr);
				final ResultSet finalRs = rs = stmt.executeQuery();

				if (rs.next())
				{
					return new InputStream() {
						private InputStream inner = finalRs.getBinaryStream(1);

						@Override
						public int read() throws IOException
						{
							return inner.read();
						}

						@Override
						public int read(byte[] b, int off, int len) throws IOException
						{
							return inner.read(b, off, len);
						}

						@Override
						public long skip(long n) throws IOException
						{
							return inner.skip(n);
						}

						@Override
						public int available() throws IOException
						{
							return inner.available();
						}

						@Override
						public void close() throws IOException
						{
							if (inner == null)
								return;

							try
							{
								inner.close();
								try
								{
									finalRs.close();
									stmt.close();
								}
								catch (SQLException e)
								{
									throw new IOException(e);
								}
							}
							finally
							{
								inner = null;
							}
						}

						@Override
						public void mark(int readlimit)
						{
							inner.mark(readlimit);
						}

						@Override
						public void reset() throws IOException
						{
							inner.reset();
						}

						@Override
						public boolean markSupported()
						{
							return inner.markSupported();
						}
					};
				}
				else
				{
					rs.close();
					stmt.close();

					try (PreparedStatement stmt2 = conn.prepareStatement("select child from sqlj.list_dir(?)"))
					{
						stmt2.setString(1, urlStr);

						try (ResultSet rs2 = stmt2.executeQuery())
						{
							if (rs2.next())
							{
								ByteArrayOutputStream out = new ByteArrayOutputStream();
								PrintWriter writer = new PrintWriter(out);

								do
								{
									writer.println(rs2.getString(1));
								} while (rs2.next());

								writer.close();

								return new ByteArrayInputStream(out.toByteArray());
							}
						}
					}

					throw new IOException(String.format("Resource '%s' has not been found on the database.", url));
				}
			}
			catch (SQLException e)
			{
				if (rs != null)
					rs.close();
				stmt.close();
				throw e;
			}
		}
		catch (SQLException e)
		{
			log.error("Error retrieving resource or class from the database", e);
			throw new IOException(e);
		}
	}
}
