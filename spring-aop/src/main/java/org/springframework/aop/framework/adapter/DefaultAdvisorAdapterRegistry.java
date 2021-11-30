/*
 * Copyright 2002-2018 the original author or authors.
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

package org.springframework.aop.framework.adapter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;

/**
 * Default implementation of the {@link AdvisorAdapterRegistry} interface.
 * Supports {@link org.aopalliance.intercept.MethodInterceptor},
 * {@link org.springframework.aop.MethodBeforeAdvice},
 * {@link org.springframework.aop.AfterReturningAdvice},
 * {@link org.springframework.aop.ThrowsAdvice}.
 *
 * @author Rod Johnson
 * @author Rob Harrop
 * @author Juergen Hoeller
 */

/**
 * LUQIUDO
 * 了一系列在AOP应用中与用到的Spring AOP的advice通知相对应的adapter适配实现，
 * 并看到了对这些adapter的具体使用。具体说来，对它们的使用主要体现在以下两个方面：
 * 一是调用adapter的support方法，通过这个方法来判断取得的advice属于什么类型的advice通知，
 * 从而根据不同的advice类型来注册不同的AdviceInterceptor，也就是前面看到的那些拦截器；
 * 另一方面，这些AdviceInterceptor都是Spring AOP框架设计好了的，是为实现不同的advice功能提供服务的。
 * 有了这些AdviceInterceptor，可以方便地使用由Spring提供的各种不同的advice来设计AOP应用。
 * 也就是说，正是这些AdviceInterceptor最终实现了advice通知在AopProxy代理对象中的织入功能。
 */
@SuppressWarnings("serial")
public class DefaultAdvisorAdapterRegistry implements AdvisorAdapterRegistry, Serializable {

	// 持有一个 AdvisorAdapter的 List,这个 List中的 Adapter是与实现 Spring AOP的 advice增强功能相对应的
	private final List<AdvisorAdapter> adapters = new ArrayList<>(3);


	/**
	 * Create a new DefaultAdvisorAdapterRegistry, registering well-known adapters.
	 */
	// 这里把已有的 advice实现的 Adapter加入进来，
	// 有非常熟悉的 MethodBeforeAdvice、 AfterReturningAdvice、
	// ThrowsAdvice 这些 AOP的 advice封装实现
	public DefaultAdvisorAdapterRegistry() {
		registerAdvisorAdapter(new MethodBeforeAdviceAdapter());
		registerAdvisorAdapter(new AfterReturningAdviceAdapter());
		registerAdvisorAdapter(new ThrowsAdviceAdapter());
	}


	@Override
	public Advisor wrap(Object adviceObject) throws UnknownAdviceTypeException {
		if (adviceObject instanceof Advisor) {
			return (Advisor) adviceObject;
		}
		if (!(adviceObject instanceof Advice)) {
			throw new UnknownAdviceTypeException(adviceObject);
		}
		Advice advice = (Advice) adviceObject;
		if (advice instanceof MethodInterceptor) {
			// So well-known it doesn't even need an adapter.
			return new DefaultPointcutAdvisor(advice);
		}
		for (AdvisorAdapter adapter : this.adapters) {
			// Check that it is supported.
			if (adapter.supportsAdvice(advice)) {
				return new DefaultPointcutAdvisor(advice);
			}
		}
		throw new UnknownAdviceTypeException(advice);
	}


	@Override
	// 在 DefaultAdvisorChainFactory中启动的 getInterceptors方法
	public MethodInterceptor[] getInterceptors(Advisor advisor) throws UnknownAdviceTypeException {
		List<MethodInterceptor> interceptors = new ArrayList<>(3);
		// 从 Advisor通知器配置中取得 advice通知
		Advice advice = advisor.getAdvice();
		// 如果通知是 MethodInterceptor类型的通知，直接加入 interceptors的 List中，不需要适配
		if (advice instanceof MethodInterceptor) {
			interceptors.add((MethodInterceptor) advice);
		}
		// 对通知进行适配，使用已经配置好的 Adapter： MethodBeforeAdviceAdapter、
		// AfterReturningAdviceAdapter以及 ThrowsAdviceAdapter，然后从对应的
		// adapter 中取出封装好 AOP 编织功能的拦截器
		for (AdvisorAdapter adapter : this.adapters) {
			if (adapter.supportsAdvice(advice)) {
				interceptors.add(adapter.getInterceptor(advisor));
			}
		}
		if (interceptors.isEmpty()) {
			throw new UnknownAdviceTypeException(advisor.getAdvice());
		}
		return interceptors.toArray(new MethodInterceptor[0]);
	}

	@Override
	public void registerAdvisorAdapter(AdvisorAdapter adapter) {
		this.adapters.add(adapter);
	}

}
