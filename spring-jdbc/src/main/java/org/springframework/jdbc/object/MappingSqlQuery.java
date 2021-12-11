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

package org.springframework.jdbc.object;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;

import org.springframework.lang.Nullable;

/**
 * Reusable query in which concrete subclasses must implement the abstract
 * mapRow(ResultSet, int) method to convert each row of the JDBC ResultSet
 * into an object.
 *
 * <p>Simplifies MappingSqlQueryWithParameters API by dropping parameters and
 * context. Most subclasses won't care about parameters. If you don't use
 * contextual information, subclass this instead of MappingSqlQueryWithParameters.
 *
 * @author Rod Johnson
 * @author Thomas Risberg
 * @author Jean-Pierre Pawlak
 * @see MappingSqlQueryWithParameters
 */

/**
 * LUQIUDO
 * 使用MappingSqlQuery将数据库表的数据记录直接映射到一个对象集合，
 * 这是一个很有用的特性，类似于一个简单的O/R映射实现
 *
 * 使用过程会使用到 RdbmsOpertion 中的 declareParameter 和 compile
 * 进去分析两者的实现原理, 在完成了compile之后，对MappingSqlQuery的准备工作就基本完成了。
 *
 * 在执行查询时，实际上执行的是 SqlQuery 的executeByNamedParam方法，
 * 这个方法需要完成的工作包括配置SQL语句，配置数据记录到数据对象的转换的RowMapper，
 * 然后使用JdbcTemplate来完成数据的查询，并启动数据记录到Java数据对象的转换
 */
public abstract class MappingSqlQuery<T> extends MappingSqlQueryWithParameters<T> {

	/**
	 * Constructor that allows use as a JavaBean.
	 */
	public MappingSqlQuery() {
	}

	/**
	 * Convenient constructor with DataSource and SQL string.
	 * @param ds DataSource to use to obtain connections
	 * @param sql SQL to run
	 */
	public MappingSqlQuery(DataSource ds, String sql) {
		super(ds, sql);
	}


	/**
	 * This method is implemented to invoke the simpler mapRow
	 * template method, ignoring parameters.
	 * @see #mapRow(ResultSet, int)
	 */
	@Override
	@Nullable
	protected final T mapRow(ResultSet rs, int rowNum, @Nullable Object[] parameters, @Nullable Map<?, ?> context)
			throws SQLException {

		return mapRow(rs, rowNum);
	}

	/**
	 * Subclasses must implement this method to convert each row of the
	 * ResultSet into an object of the result type.
	 * <p>Subclasses of this class, as opposed to direct subclasses of
	 * MappingSqlQueryWithParameters, don't need to concern themselves
	 * with the parameters to the execute method of the query object.
	 * @param rs ResultSet we're working through
	 * @param rowNum row number (from 0) we're up to
	 * @return an object of the result type
	 * @throws SQLException if there's an error extracting data.
	 * Subclasses can simply not catch SQLExceptions, relying on the
	 * framework to clean up.
	 */
	@Nullable
	protected abstract T mapRow(ResultSet rs, int rowNum) throws SQLException;

}
