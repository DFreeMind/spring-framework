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

package org.springframework.transaction.interceptor;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.lang.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PatternMatchUtils;

/**
 * Simple {@link TransactionAttributeSource} implementation that
 * allows attributes to be matched by registered name.
 *
 * @author Juergen Hoeller
 * @since 21.08.2003
 * @see #isMatch
 * @see MethodMapTransactionAttributeSource
 */
@SuppressWarnings("serial")
public class NameMatchTransactionAttributeSource implements TransactionAttributeSource, Serializable {

	/**
	 * Logger available to subclasses.
	 * <p>Static for optimal serialization.
	 */
	protected static final Log logger = LogFactory.getLog(NameMatchTransactionAttributeSource.class);

	/** Keys are method names; values are TransactionAttributes */
	private Map<String, TransactionAttribute> nameMap = new HashMap<>();


	/**
	 * Set a name/attribute map, consisting of method names
	 * (e.g. "myMethod") and TransactionAttribute instances
	 * (or Strings to be converted to TransactionAttribute instances).
	 * @see TransactionAttribute
	 * @see TransactionAttributeEditor
	 */
	public void setNameMap(Map<String, TransactionAttribute> nameMap) {
		nameMap.forEach(this::addTransactionalMethod);
	}

	/**
	 * Parses the given properties into a name/attribute map.
	 * Expects method names as keys and String attributes definitions as values,
	 * parsable into TransactionAttribute instances via TransactionAttributeEditor.
	 * @see #setNameMap
	 * @see TransactionAttributeEditor
	 */
	// LUQIUDO
	// 设置配置的事务方法
	public void setProperties(Properties transactionAttributes) {
		TransactionAttributeEditor tae = new TransactionAttributeEditor();
		Enumeration<?> propNames = transactionAttributes.propertyNames();
		while (propNames.hasMoreElements()) {
			String methodName = (String) propNames.nextElement();
			String value = transactionAttributes.getProperty(methodName);
			tae.setAsText(value);
			TransactionAttribute attr = (TransactionAttribute) tae.getValue();
			addTransactionalMethod(methodName, attr);
		}
	}

	/**
	 * Add an attribute for a transactional method.
	 * <p>Method names can be exact matches, or of the pattern "xxx*",
	 * "*xxx" or "*xxx*" for matching multiple methods.
	 * @param methodName the name of the method
	 * @param attr attribute associated with the method
	 */
	public void addTransactionalMethod(String methodName, TransactionAttribute attr) {
		if (logger.isDebugEnabled()) {
			logger.debug("Adding transactional method [" + methodName + "] with attribute [" + attr + "]");
		}
		this.nameMap.put(methodName, attr);
	}


	@Override
	@Nullable
	/**
	 * LUQIUDO
	 *
	 * 对调用的方法进行判断，判断它是否是事务方法，如果是事务方法，那么取出相应的事务配置属性.
	 *
	 * 首先，以调用方法名为索引在nameMap中查找相应的事务处理属性值，
	 * 如果能够找到，那么就说明该调用方法和事务方法是直接对应的；
	 * 如果找不到，那么就会遍历整个nameMap，对保存在nameMap中的每一个方法名，
	 * 使用PatternMatchUtils的SimpleMatch方法进行命名模式上的匹配。
	 * 这里使用PatternMatchUtils进行匹配的原因是，在设置事务方法的时候，
	 * 可以不需要为事务方法设置一个完整的方法名，而可以通过设置方法名的命名模式来完成，
	 * 比如可以通过对通配符*的使用等。所以，如果直接通过方法名没能够匹配上，
	 * 而通过方法名的命名模式能够匹配上，这个方法也是需要进行事务处理的方法，
	 * 相对应地，它所配置的事务处理属性也会从nameMap中取出来，从而触发事务处理拦截器的拦截。
	 */
	public TransactionAttribute getTransactionAttribute(Method method, @Nullable Class<?> targetClass) {
		if (!ClassUtils.isUserLevelMethod(method)) {
			return null;
		}

		// Look for direct name match.
		// 判断当前目标调用的方法与配置的事务方法是否直接匹配
		String methodName = method.getName();
		TransactionAttribute attr = this.nameMap.get(methodName);

		// 如果不能直接匹配，就通过调用PatternMatchUtils的simpleMatch方法来进行匹配判断
		if (attr == null) {
			// Look for most specific name match.
			String bestNameMatch = null;
			for (String mappedName : this.nameMap.keySet()) {
				if (isMatch(methodName, mappedName) &&
						(bestNameMatch == null || bestNameMatch.length() <= mappedName.length())) {
					attr = this.nameMap.get(mappedName);
					bestNameMatch = mappedName;
				}
			}
		}

		return attr;
	}

	/**
	 * Return if the given method name matches the mapped name.
	 * <p>The default implementation checks for "xxx*", "*xxx" and "*xxx*" matches,
	 * as well as direct equality. Can be overridden in subclasses.
	 * @param methodName the method name of the class
	 * @param mappedName the name in the descriptor
	 * @return if the names match
	 * @see org.springframework.util.PatternMatchUtils#simpleMatch(String, String)
	 */
	protected boolean isMatch(String methodName, String mappedName) {
		return PatternMatchUtils.simpleMatch(mappedName, methodName);
	}


	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof NameMatchTransactionAttributeSource)) {
			return false;
		}
		NameMatchTransactionAttributeSource otherTas = (NameMatchTransactionAttributeSource) other;
		return ObjectUtils.nullSafeEquals(this.nameMap, otherTas.nameMap);
	}

	@Override
	public int hashCode() {
		return NameMatchTransactionAttributeSource.class.hashCode();
	}

	@Override
	public String toString() {
		return getClass().getName() + ": " + this.nameMap;
	}

}
