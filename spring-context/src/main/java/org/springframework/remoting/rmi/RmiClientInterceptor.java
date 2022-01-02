/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.remoting.rmi;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

import org.springframework.aop.support.AopUtils;
import org.springframework.lang.Nullable;
import org.springframework.remoting.RemoteConnectFailureException;
import org.springframework.remoting.RemoteInvocationFailureException;
import org.springframework.remoting.RemoteLookupFailureException;
import org.springframework.remoting.support.RemoteInvocationBasedAccessor;
import org.springframework.remoting.support.RemoteInvocationUtils;

/**
 * {@link org.aopalliance.intercept.MethodInterceptor} for accessing conventional
 * RMI services or RMI invokers. The service URL must be a valid RMI URL
 * (e.g. "rmi://localhost:1099/myservice").
 *
 * <p>RMI invokers work at the RmiInvocationHandler level, needing only one stub for
 * any service. Service interfaces do not have to extend {@code java.rmi.Remote}
 * or throw {@code java.rmi.RemoteException}. Spring's unchecked
 * RemoteAccessException will be thrown on remote invocation failure.
 * Of course, in and out parameters have to be serializable.
 *
 * <p>With conventional RMI services, this invoker is typically used with the RMI
 * service interface. Alternatively, this invoker can also proxy a remote RMI service
 * with a matching non-RMI business interface, i.e. an interface that mirrors the RMI
 * service methods but does not declare RemoteExceptions. In the latter case,
 * RemoteExceptions thrown by the RMI stub will automatically get converted to
 * Spring's unchecked RemoteAccessException.
 *
 * @author Juergen Hoeller
 * @since 29.09.2003
 * @see RmiServiceExporter
 * @see RmiProxyFactoryBean
 * @see RmiInvocationHandler
 * @see org.springframework.remoting.RemoteAccessException
 * @see java.rmi.RemoteException
 * @see java.rmi.Remote
 */
public class RmiClientInterceptor extends RemoteInvocationBasedAccessor
		implements MethodInterceptor {

	private boolean lookupStubOnStartup = true;

	private boolean cacheStub = true;

	private boolean refreshStubOnConnectFailure = false;

	private RMIClientSocketFactory registryClientSocketFactory;

	private Remote cachedStub;

	private final Object stubMonitor = new Object();


	/**
	 * Set whether to look up the RMI stub on startup. Default is "true".
	 * <p>Can be turned off to allow for late start of the RMI server.
	 * In this case, the RMI stub will be fetched on first access.
	 * @see #setCacheStub
	 */
	public void setLookupStubOnStartup(boolean lookupStubOnStartup) {
		this.lookupStubOnStartup = lookupStubOnStartup;
	}

	/**
	 * Set whether to cache the RMI stub once it has been located.
	 * Default is "true".
	 * <p>Can be turned off to allow for hot restart of the RMI server.
	 * In this case, the RMI stub will be fetched for each invocation.
	 * @see #setLookupStubOnStartup
	 */
	public void setCacheStub(boolean cacheStub) {
		this.cacheStub = cacheStub;
	}

	/**
	 * Set whether to refresh the RMI stub on connect failure.
	 * Default is "false".
	 * <p>Can be turned on to allow for hot restart of the RMI server.
	 * If a cached RMI stub throws an RMI exception that indicates a
	 * remote connect failure, a fresh proxy will be fetched and the
	 * invocation will be retried.
	 * @see java.rmi.ConnectException
	 * @see java.rmi.ConnectIOException
	 * @see java.rmi.NoSuchObjectException
	 */
	public void setRefreshStubOnConnectFailure(boolean refreshStubOnConnectFailure) {
		this.refreshStubOnConnectFailure = refreshStubOnConnectFailure;
	}

	/**
	 * Set a custom RMI client socket factory to use for accessing the RMI registry.
	 * @see java.rmi.server.RMIClientSocketFactory
	 * @see java.rmi.registry.LocateRegistry#getRegistry(String, int, RMIClientSocketFactory)
	 */
	public void setRegistryClientSocketFactory(RMIClientSocketFactory registryClientSocketFactory) {
		this.registryClientSocketFactory = registryClientSocketFactory;
	}


	@Override
	// LUQIUDO
	// 建立RMI基础设施的调用，仍然是在afterPropertiesSet()方法中实现。
	// 因为这个RmiClientInterceptor实现了InitializingBean接口，所以它会被IoC容器回调
	public void afterPropertiesSet() {
		// 继续追踪代码，发现父类的父类，
		// 也就是UrlBasedRemoteAccessor中的
		// afterPropertiesSet方法只完成了对serviceUrl属性的验证。
		// STEPINTO 🍉
		super.afterPropertiesSet();
		// STEPINTO ✨✨
		prepare();
	}

	/**
	 * Fetches RMI stub on startup, if necessary.
	 * @throws RemoteLookupFailureException if RMI stub creation failed
	 * @see #setLookupStubOnStartup
	 * @see #lookupStub
	 */
	// LUQIUDO
	// 通过代理拦截并获取stub 🍇
	// 这里为RMI客户端准备stub，这个stub通过lookupStub()方法获得，
	// 并且会在第一次生成之后，放到缓存中去
	public void prepare() throws RemoteLookupFailureException {
		// Cache RMI stub on initialization?
		// 是否在初始化时缓存RMI Stub
		// 如果配置了lookupStubOnStartup属性便会在启动时寻找stub
		// 如果将此属性设置为true，那么获取stub的工作就会在系统启动时被执行并缓存，从而提高使用时候的响应时间。
		if (this.lookupStubOnStartup) {
			// STEPINTO ✨ 获取 stub
			Remote remoteObj = lookupStub();
			if (logger.isDebugEnabled()) {
				if (remoteObj instanceof RmiInvocationHandler) {
					logger.debug("RMI stub [" + getServiceUrl() + "] is an RMI invoker");
				}
				else if (getServiceInterface() != null) {
					boolean isImpl = getServiceInterface().isInstance(remoteObj);
					logger.debug("Using service interface [" + getServiceInterface().getName() +
						"] for RMI stub [" + getServiceUrl() + "] - " +
						(!isImpl ? "not " : "") + "directly implemented");
				}
			}
			if (this.cacheStub) {
				// 将获取到的 stub 缓存
				this.cachedStub = remoteObj;
			}
		}
	}

	/**
	 * Create the RMI stub, typically by looking it up.
	 * <p>Called on interceptor initialization if "cacheStub" is "true";
	 * else called for each invocation by {@link #getStub()}.
	 * <p>The default implementation looks up the service URL via
	 * {@code java.rmi.Naming}. This can be overridden in subclasses.
	 * @return the RMI stub to store in this interceptor
	 * @throws RemoteLookupFailureException if RMI stub creation failed
	 * @see #setCacheStub
	 * @see java.rmi.Naming#lookup
	 */
	/**
	 * 获取 stub 的两种方式
	 * - 使用自定义的套接字工厂。如果使用这种方式，你需要在构造Registry实例时将自定义套接字工厂传入，
	 * 	并使用Registry中提供的lookup方法来获取对应的stub。
	 * - 直接使用RMI提供的标准方法：Naming.lookup(getServiceUrl())。
	 */
	// 获得RMI stub对象的地方
	protected Remote lookupStub() throws RemoteLookupFailureException {
		try {
			Remote stub = null;
			if (this.registryClientSocketFactory != null) {
				// RMIClientSocketFactory specified for registry access.
				// Unfortunately, due to RMI API limitations, this means
				// that we need to parse the RMI URL ourselves and perform
				// straight LocateRegistry.getRegistry/Registry.lookup calls.
				URL url = new URL(null, getServiceUrl(), new DummyURLStreamHandler());
				String protocol = url.getProtocol();
				// 验证传输协议
				if (protocol != null && !"rmi".equals(protocol)) {
					throw new MalformedURLException("Invalid URL scheme '" + protocol + "'");
				}
				// 主机
				String host = url.getHost();
				// 端口
				int port = url.getPort();
				// 服务名
				String name = url.getPath();
				if (name != null && name.startsWith("/")) {
					name = name.substring(1);
				}
				Registry registry = LocateRegistry.getRegistry(host, port, this.registryClientSocketFactory);
				// 第一种方式
				stub = registry.lookup(name);
			}
			else {
				// Can proceed with standard RMI lookup API...
				// 第二种方式
				stub = Naming.lookup(getServiceUrl());
			}
			if (logger.isDebugEnabled()) {
				logger.debug("Located RMI stub with URL [" + getServiceUrl() + "]");
			}
			return stub;
		}
		catch (MalformedURLException ex) {
			throw new RemoteLookupFailureException("Service URL [" + getServiceUrl() + "] is invalid", ex);
		}
		catch (NotBoundException ex) {
			throw new RemoteLookupFailureException(
					"Could not find RMI service [" + getServiceUrl() + "] in RMI registry", ex);
		}
		catch (RemoteException ex) {
			throw new RemoteLookupFailureException("Lookup of RMI stub failed", ex);
		}
	}

	/**
	 * Return the RMI stub to use. Called for each invocation.
	 * <p>The default implementation returns the stub created on initialization,
	 * if any. Else, it invokes {@link #lookupStub} to get a new stub for
	 * each invocation. This can be overridden in subclasses, for example in
	 * order to cache a stub for a given amount of time before recreating it,
	 * or to test the stub whether it is still alive.
	 * @return the RMI stub to use for an invocation
	 * @throws RemoteLookupFailureException if RMI stub creation failed
	 * @see #lookupStub
	 */
	protected Remote getStub() throws RemoteLookupFailureException {
		if (!this.cacheStub || (this.lookupStubOnStartup && !this.refreshStubOnConnectFailure)) {
			// 如果有缓存直接使用缓存
			return (this.cachedStub != null ? this.cachedStub : lookupStub());
		}
		else {
			synchronized (this.stubMonitor) {
				if (this.cachedStub == null) {
					// 获取stub
					this.cachedStub = lookupStub();
				}
				return this.cachedStub;
			}
		}
	}


	/**
	 * Fetches an RMI stub and delegates to {@code doInvoke}.
	 * If configured to refresh on connect failure, it will call
	 * {@link #refreshAndRetry} on corresponding RMI exceptions.
	 * @see #getStub
	 * @see #doInvoke(MethodInvocation, Remote)
	 * @see #refreshAndRetry
	 * @see java.rmi.ConnectException
	 * @see java.rmi.ConnectIOException
	 * @see java.rmi.NoSuchObjectException
	 */
	@Override
	// LUQIUDO
	// 获取了stub之后，当调用RMI客户端的代理方法时，
	// 会触发拦截器RmiClientInterceptor的invoke回调方法
	// 拦截器对代理对象方法调用的回调，在实现中，取得RMI stub对象，然后调用doInvoke完成RMI调用
	public Object invoke(MethodInvocation invocation) throws Throwable {
		// 获取的服务器中对应的注册的remote对象，通过序列化传输
		// STEPINTO 🍉
		Remote stub = getStub();
		try {
			// STEPINTO ✨✨
			return doInvoke(invocation, stub);
		}
		catch (RemoteConnectFailureException ex) {
			return handleRemoteConnectFailure(invocation, ex);
		}
		catch (RemoteException ex) {
			if (isConnectFailure(ex)) {
				return handleRemoteConnectFailure(invocation, ex);
			}
			else {
				throw ex;
			}
		}
	}

	/**
	 * Determine whether the given RMI exception indicates a connect failure.
	 * <p>The default implementation delegates to
	 * {@link RmiClientInterceptorUtils#isConnectFailure}.
	 * @param ex the RMI exception to check
	 * @return whether the exception should be treated as connect failure
	 */
	protected boolean isConnectFailure(RemoteException ex) {
		return RmiClientInterceptorUtils.isConnectFailure(ex);
	}

	/**
	 * Refresh the stub and retry the remote invocation if necessary.
	 * <p>If not configured to refresh on connect failure, this method
	 * simply rethrows the original exception.
	 * @param invocation the invocation that failed
	 * @param ex the exception raised on remote invocation
	 * @return the result value of the new invocation, if succeeded
	 * @throws Throwable an exception raised by the new invocation,
	 * if it failed as well
	 * @see #setRefreshStubOnConnectFailure
	 * @see #doInvoke
	 */
	@Nullable
	private Object handleRemoteConnectFailure(MethodInvocation invocation, Exception ex) throws Throwable {
		if (this.refreshStubOnConnectFailure) {
			String msg = "Could not connect to RMI service [" + getServiceUrl() + "] - retrying";
			if (logger.isDebugEnabled()) {
				logger.warn(msg, ex);
			}
			else if (logger.isWarnEnabled()) {
				logger.warn(msg);
			}
			return refreshAndRetry(invocation);
		}
		else {
			throw ex;
		}
	}

	/**
	 * Refresh the RMI stub and retry the given invocation.
	 * Called by invoke on connect failure.
	 * @param invocation the AOP method invocation
	 * @return the invocation result, if any
	 * @throws Throwable in case of invocation failure
	 * @see #invoke
	 */
	@Nullable
	protected Object refreshAndRetry(MethodInvocation invocation) throws Throwable {
		Remote freshStub = null;
		synchronized (this.stubMonitor) {
			this.cachedStub = null;
			freshStub = lookupStub();
			if (this.cacheStub) {
				this.cachedStub = freshStub;
			}
		}
		return doInvoke(invocation, freshStub);
	}

	/**
	 * Perform the given invocation on the given RMI stub.
	 * @param invocation the AOP method invocation
	 * @param stub the RMI stub to invoke
	 * @return the invocation result, if any
	 * @throws Throwable in case of invocation failure
	 */
	@Nullable
	/**
	 * Spring中对于远程方法的调用其实是分两种情况考虑:
	 * 	- 获取的stub是RMIInvocationHandler类型的，从服务端获取的 stub 是RMIInvocationHandler，
	 * 		就意味着服务端也同样使用了Spring去构建，那么自然会使用Spring中作的约定，进行客户端调用处理。
	 * 		Spring中的处理方式被委托给了doInvoke方法。
	 * 	- 当获取的stub不是RMIInvocationHandler类型，
	 * 		那么服务端构建RMI服务可能是通过普通的方法或者借助于Spring外的第三方插件，
	 * 		那么处理方式自然会按照RMI中普通的处理方式进行，而这种普通的处理方式无非是反射。
	 * 		因为在invocation中包含了所需要调用的方法的各种信息，包括方法名称以及参数等，
	 * 		而调用的实体正是stub，那么通过反射方法完全可以激活stub中的远程调用。
	 */
	// LUQIUDO
	// 具体的RMI调用发生的地方，如果stub是RmiInvocationHandler实例，
	// 那么使用RMI调用器来完成这次远端调用；否则，使用传统的RMI调用方式
	protected Object doInvoke(MethodInvocation invocation, Remote stub) throws Throwable {
		// stub从服务器传回且经过Spring的封装
		if (stub instanceof RmiInvocationHandler) {
			// RMI invoker
			try {
				// STEPINTO 🍉
				return doInvoke(invocation, (RmiInvocationHandler) stub);
			}
			catch (RemoteException ex) {
				throw RmiClientInterceptorUtils.convertRmiAccessException(
					invocation.getMethod(), ex, isConnectFailure(ex), getServiceUrl());
			}
			catch (InvocationTargetException ex) {
				Throwable exToThrow = ex.getTargetException();
				RemoteInvocationUtils.fillInClientStackTraceIfPossible(exToThrow);
				throw exToThrow;
			}
			catch (Throwable ex) {
				throw new RemoteInvocationFailureException("Invocation of method [" + invocation.getMethod() +
						"] failed in RMI service [" + getServiceUrl() + "]", ex);
			}
		}
		else {
			// traditional RMI stub
			try {
				// 直接使用反射方法继续激活
				return RmiClientInterceptorUtils.invokeRemoteMethod(invocation, stub);
			}
			catch (InvocationTargetException ex) {
				Throwable targetEx = ex.getTargetException();
				if (targetEx instanceof RemoteException) {
					RemoteException rex = (RemoteException) targetEx;
					throw RmiClientInterceptorUtils.convertRmiAccessException(
							invocation.getMethod(), rex, isConnectFailure(rex), getServiceUrl());
				}
				else {
					throw targetEx;
				}
			}
		}
	}

	/**
	 * Apply the given AOP method invocation to the given {@link RmiInvocationHandler}.
	 * <p>The default implementation delegates to {@link #createRemoteInvocation}.
	 * @param methodInvocation the current AOP method invocation
	 * @param invocationHandler the RmiInvocationHandler to apply the invocation to
	 * @return the invocation result
	 * @throws RemoteException in case of communication errors
	 * @throws NoSuchMethodException if the method name could not be resolved
	 * @throws IllegalAccessException if the method could not be accessed
	 * @throws InvocationTargetException if the method invocation resulted in an exception
	 * @see org.springframework.remoting.support.RemoteInvocation
	 */
	@Nullable
	protected Object doInvoke(MethodInvocation methodInvocation, RmiInvocationHandler invocationHandler)
		throws RemoteException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {

		if (AopUtils.isToStringMethod(methodInvocation.getMethod())) {
			return "RMI invoker proxy for service URL [" + getServiceUrl() + "]";
		}
		// 将methodInvocation中的方法名及参数等信息重新封装到RemoteInvocation，并通过远程代理方法直接调用
		return invocationHandler.invoke(createRemoteInvocation(methodInvocation));
	}


	/**
	 * Dummy URLStreamHandler that's just specified to suppress the standard
	 * {@code java.net.URL} URLStreamHandler lookup, to be able to
	 * use the standard URL class for parsing "rmi:..." URLs.
	 */
	private static class DummyURLStreamHandler extends URLStreamHandler {

		@Override
		protected URLConnection openConnection(URL url) throws IOException {
			throw new UnsupportedOperationException();
		}
	}

}
