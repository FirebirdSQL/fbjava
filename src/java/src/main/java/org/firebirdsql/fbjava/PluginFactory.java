package org.firebirdsql.fbjava;

import org.firebirdsql.fbjava.FbClientLibrary.IExternalEngine;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginBase;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginConfig;
import org.firebirdsql.fbjava.FbClientLibrary.IPluginFactoryIntf;
import org.firebirdsql.fbjava.FbClientLibrary.IStatus;


class PluginFactory implements IPluginFactoryIntf
{
	@Override
	public IPluginBase createPlugin(IStatus status, IPluginConfig factoryParameter) throws FbException
	{
		return new IExternalEngine(new ExternalEngine());
	}
}
