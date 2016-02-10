/*
 * ModeShape (http://www.modeshape.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.modeshape.jdbc.delegate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.ValueFormatException;
import javax.jcr.query.Row;
import javax.jcr.query.RowIterator;
import org.modeshape.jcr.api.query.QueryResult;
import org.modeshape.jdbc.JcrType;
import org.modeshape.jdbc.JdbcJcrValueFactory;
import org.modeshape.jdbc.rest.JSONRestClient;
import org.modeshape.jdbc.rest.ModeShapeRestClient;

/**
 * A simple implementation of the {@link QueryResult} interface.
 *
 * @author Horia Chiorean
 */
public final class HttpQueryResult implements QueryResult {

    protected final List<HttpRow> rows = new ArrayList<>();
    protected final Map<String, String> columnTypesByName = new LinkedHashMap<>();
    private final ModeShapeRestClient restClient;

    protected HttpQueryResult(org.modeshape.jdbc.rest.QueryResult queryResult, ModeShapeRestClient restClient) {
        assert queryResult != null;
        this.restClient = restClient;

        if (!queryResult.isEmpty()) {
            this.columnTypesByName.putAll(queryResult.getColumns());

            for (org.modeshape.jdbc.rest.QueryResult.Row queryRow : queryResult) {
                rows.add(new HttpRow(queryRow, this.restClient));
            }
        }
    }

    @Override
    public String getPlan() {
        throw new UnsupportedOperationException("Method getPlan() not supported");
    }

    @Override
    public Collection<String> getWarnings() {
        return Collections.emptyList();
    }

    @Override
    public String[] getColumnNames() {
        return columnTypesByName.keySet().toArray(new String[columnTypesByName.size()]);
    }

    @Override
    public boolean isEmpty() {
        return rows.isEmpty();
    }

    @Override
    public RowIterator getRows() {
        return new HttpRowIterator();
    }

    @Override
    public NodeIterator getNodes() {
        throw new UnsupportedOperationException("Method getNodes() not supported");
    }

    @Override
    public String[] getSelectorNames() {
        throw new UnsupportedOperationException("Method getSelectorNames() not supported");
    }

    @Override
    public void close() {
        // do nothing
    }

    @Override
    public String[] getColumnTypes() {
        return columnTypesByName.values().toArray(new String[columnTypesByName.size()]);
    }

    private class HttpRowIterator implements RowIterator {

        private static final int EMPTY_CURSOR = -1;
        private int cursor = rows.isEmpty() ? EMPTY_CURSOR : 0;

        protected HttpRowIterator() {
        }

        @Override
        public Row nextRow() {
            if (cursor == -1 || cursor >= rows.size()) {
                throw new NoSuchElementException("No more rows to iterate over");
            }
            return rows.get(cursor++);
        }

        @Override
        public void skip(long skipNum) {
            if (skipNum < 0) {
                throw new IllegalArgumentException("skipNum must be a positive value");
            }
            int availableRowsCount = rows.size() - cursor;
            if (skipNum > availableRowsCount) {
                throw new NoSuchElementException("Skip would go past collection end");
            }
            cursor += skipNum;
        }

        @Override
        public long getSize() {
            return rows.size();
        }

        @Override
        public long getPosition() {
            return cursor;
        }

        @Override
        public boolean hasNext() {
            return cursor != -1 && cursor < rows.size();
        }

        @Override
        public Object next() {
            return nextRow();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("Method remove() not supported by this iterator");
        }
    }

    private class HttpRow implements Row {

        private final Map<String, Value> valuesMap = new LinkedHashMap<>();
        private final ModeShapeRestClient restClient;

        protected HttpRow(org.modeshape.jdbc.rest.QueryResult.Row row, ModeShapeRestClient restClient) {
            assert row != null;
            this.restClient = restClient;
            for (Map.Entry<String, String> column : columnTypesByName.entrySet()) {
                Object queryRowValue = row.getValue(column.getKey());
                if (column.getValue().equalsIgnoreCase(JcrType.DefaultDataTypes.BINARY)) {
                    class Binary implements javax.jcr.Value, javax.jcr.Binary {

                        private final ModeShapeRestClient restClient;
                        private byte[] data;
                        private final String uri;

                        public Binary(ModeShapeRestClient restClient, String uri) {
                            this.restClient = restClient;
                            this.uri = uri;
                        }

                        private byte[] getData() {
                            if (data == null) {
                                JSONRestClient.Response doGet = restClient.getJsonRestClient().doGet(uri);
                                if (doGet.isOK()) {
                                    data = doGet.getContent();
                                }
                            }
                            return data;
                        }

                        @Override
                        public String getString() throws ValueFormatException, IllegalStateException, RepositoryException {
                            return new String(getData());
                        }

                        @Override
                        public InputStream getStream() throws RepositoryException {
                            return new ByteArrayInputStream(getData());
                        }

                        @Override
                        public javax.jcr.Binary getBinary() throws RepositoryException {
                            return this;
                        }

                        @Override
                        public long getLong() throws ValueFormatException, RepositoryException {
                            return getData().length;
                        }

                        @Override
                        public double getDouble() throws ValueFormatException, RepositoryException {
                            return getLong();
                        }

                        @Override
                        public BigDecimal getDecimal() throws ValueFormatException, RepositoryException {
                            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                        }

                        @Override
                        public Calendar getDate() throws ValueFormatException, RepositoryException {
                            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                        }

                        @Override
                        public boolean getBoolean() throws ValueFormatException, RepositoryException {
                            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                        }

                        @Override
                        public int getType() {
                            return PropertyType.BINARY;
                        }

                        @Override
                        public int read(byte[] b, long position) throws IOException, RepositoryException {
                            return getStream().read(b, (int) position, b.length);
                        }

                        @Override
                        public long getSize() throws RepositoryException {
                            return getLong();
                        }

                        @Override
                        public void dispose() {
                            data = null;
                        }
                    }
                    valuesMap.put(column.getKey(), new Binary(this.restClient, queryRowValue.toString()));
                } else {
                    valuesMap.put(column.getKey(), JdbcJcrValueFactory.createValue(queryRowValue));
                }
            }
        }

        @Override
        public Node getNode() {
            throw new UnsupportedOperationException("Method getNode() not supported");
        }

        @Override
        public Value[] getValues() {
            return valuesMap.values().toArray(new Value[valuesMap.size()]);
        }

        @Override
        public Value getValue(String columnName) {
            return valuesMap.get(columnName);
        }

        @Override
        public Node getNode(String selectorName) {
            throw new UnsupportedOperationException("Method getNode(selectorName) not supported");
        }

        @Override
        public String getPath() {
            throw new UnsupportedOperationException("Method getPath() not supported");
        }

        @Override
        public String getPath(String selectorName) {
            throw new UnsupportedOperationException("Method getPath(selectorName) not supported");
        }

        @Override
        public double getScore() {
            throw new UnsupportedOperationException("Method getScore() not supported");
        }

        @Override
        public double getScore(String selectorName) {
            throw new UnsupportedOperationException("Method getScore( String selectorName ) not supported");
        }
    }
}
