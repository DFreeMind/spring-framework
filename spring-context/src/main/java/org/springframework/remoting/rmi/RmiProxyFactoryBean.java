/*
 * Copyright 2002-2017 the original author or authors.
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

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.Assert;

/**
 * {@link FactoryBean} for RMI proxies, supporting both conventional RMI services
 * and RMI invokers. Exposes the proxied service for use as a bean reference,
 * using the specified service interface. Proxies will throw Spring's unchecked
 * RemoteAccessException on remote invocation failure instead of RMI's RemoteException.
 *
 * <p>The service URL must be a valid RMI URL like "rmi://localhost:1099/myservice".
 * RMI invokers work at the RmiInvocationHandler level, using the same invoker stub
 * for any service. Service interfaces do not have to extend {@code java.rmi.Remote}
 * or throw {@code java.rmi.RemoteException}. Of course, in and out parameters
 * have to be serializable.
 *
 * <p>With conventional RMI services, this proxy factory is typically used with the
 * RMI service interface. Alternatively, this factory can also proxy a remote RMI
 * service with a matching non-RMI business interface, i.e. an interface that mirrors
 * the RMI service methods but does not declare RemoteExceptions. In the latter case,
 * RemoteExceptions thrown by the RMI stub will automatically get converted to
 * Spring's unchecked RemoteAccessException.
 *
 * <p>The major advantage of RMI, compared to Hessian, is serialization.
 * Effectively, any serializable Java object can be transported without hassle.
 * Hessian has its own (de-)serialization mechanisms, but is HTTP-based and thus
 * much easier to setup than RMI. Alternatively, consider Spring's HTTP invoker
 * to combine Java serialization with HTTP-based transport.
 *
 * @author Juergen Hoeller
 * @since 13.05.2003
 * @see #setServiceInterface
 * @see #setServiceUrl
 * @see RmiClientInterceptor
 * @see RmiServiceExporter
 * @see java.rmi.Remote
 * @see java.rmi.RemoteException
 * @see org.springframework.remoting.RemoteAccessException
 * @see org.springframework.remoting.caucho.HessianProxyFactoryBean
 * @see org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean
 */

/**
 * 当获取该bean时，首先通过afterPropertiesSet创建代理类，并使用当前类作为增强方法，
 * 而在调用该bean时其实返回的是代理类，既然调用的是代理类，那么又会使用当前bean作为增强器进行增强，
 * 也就是说会调用RMIProxy FactoryBean的父类RMIClientInterceptor的invoke方法。
 */
// LUQIUDO ☀️
// RMI客户端基础设施的封装是由拦截器RmiClientInterceptor来完成的，
// 这个拦截器的设置是在RmiProxyFactoryBean生成的代理对象中完成
public class RmiProxyFactoryBean extends RmiClientInterceptor implements FactoryBean<Object>, BeanClassLoaderAware {

	// 通过ProxyFactory生成的代理对象，代理对象的代理方法和拦截器都会在其生成时设置好
	private Object serviceProxy;


	@Override
	// 在依赖注入完成以后，容器回调afterPropertiesSet，
	// 通过ProxyFactory生成代理对象，这个代理对象的拦截器是RmiClientInterceptor
	// 实现了InitializingBean，🏷
	// 则Spring会确保在此初始化bean时调用afterPropertiesSet进行逻辑的初始化
	public void afterPropertiesSet() {
		// STEPINTO ✨
		super.afterPropertiesSet();
		Class<?> ifc = getServiceInterface();
		Assert.notNull(ifc, "Property 'serviceInterface' is required");
		// 根据设置的接口创建代理，并使用当前类this作为增强器
		this.serviceProxy = new ProxyFactory(ifc, this).getProxy(getBeanClassLoader());
	}


	@Override
	// FactoryBean的接口方法，返回生成的代理对象serviceProxy
	// 实现了FactoryBean接口，🏷
	// 那么当获取bean时并不是直接获取bean，而是获取该bean的getObject方法。
	public Object getObject() {
		return this.serviceProxy;
	}

	/**
	 * 在初始化时，创建了代理并将本身作为增强器加入了代理中（RMIProxyFactoryBean间接实现了MethodInterceptor）。
	 * 那么这样一来，当在客户端调用代理的接口中的某个方法时，
	 * 就会首先执行RMIProxyFactoryBean中的invoke方法进行增强。实际在 RmiClientInterceptor 中
	 */

	@Override
	public Class<?> getObjectType() {
		return getServiceInterface();
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

}
