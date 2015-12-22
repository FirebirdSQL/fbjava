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

import org.firebirdsql.fbjava.FbClientLibrary.IPluginBase;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginConfig;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginFactory;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginFactoryIntf;
import org.firebirdsql.fbjava.FbClientLibrary.IStatus;


class PluginFactory implements IPluginFactoryIntf
{
	private PluginFactory()
	{
	}

	public static IPluginFactory create()
	{
		return JnaUtil.pin(new IPluginFactory(new PluginFactory()));
	}

	@Override
	public IPluginBase createPlugin(IStatus status, IPluginConfig factoryParameter) throws FbException
	{
		return ExternalEngine.create();
	}
}
