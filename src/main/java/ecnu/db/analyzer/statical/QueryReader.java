package ecnu.db.analyzer.statical;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.dialect.postgresql.visitor.PGASTVisitorAdapter;
import com.alibaba.druid.sql.parser.ParserException;
import com.alibaba.druid.sql.visitor.SchemaStatVisitor;
import com.alibaba.druid.stat.TableStat;
import ecnu.db.utils.exception.TouchstoneException;
import ecnu.db.utils.exception.analyze.IllegalQueryTableNameException;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

import static ecnu.db.utils.CommonUtils.matchPattern;

/**
 * @author qingshuai.wang
 */
public class QueryReader {
    private static final Pattern CANONICAL_TBL_NAME = Pattern.compile("[a-zA-Z0-9_$]+\\.[a-zA-Z0-9_$]+");
    private final ExportTableAliasVisitor aliasVisitor;
    private final String queriesDir;
    private DbType dbType;

    public QueryReader(String defaultDatabaseName, String queriesDir) {
        if (defaultDatabaseName == null) {
            this.aliasVisitor = new ExportTableAliasVisitor();
        } else {
            this.aliasVisitor = new ExportTableAliasVisitor(defaultDatabaseName);
        }
        this.queriesDir = queriesDir;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    public List<File> loadQueryFiles() {
        return Optional.ofNullable(new File(queriesDir).listFiles())
                .map(Arrays::asList)
                .orElse(new ArrayList<>())
                .stream()
                .filter(file -> file.isFile() && file.getName().endsWith(".sql"))
                .toList();
    }

    public List<String> getQueriesFromFile(String file) throws IOException {
        StringBuilder fileContents = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.length() > 0 && !line.startsWith("--")) {
                    fileContents.append(line).append(System.lineSeparator());
                }
            }
        }
        List<SQLStatement> statementList = null;
        try {
            statementList = SQLUtils.parseStatements(fileContents.toString(), dbType, true);
        } catch (ParserException e) {
            LoggerFactory.getLogger(QueryReader.class).info("Parse SQL failed: {}", file, e);
            System.exit(-1);
        }

        List<String> sqls = new ArrayList<>();
        for (SQLStatement sqlStatement : statementList) {
            String sql = SQLUtils.format(sqlStatement.toString(), dbType);
            sql = sql.replace(System.lineSeparator(), " ");
            sql = sql.replace('\t', ' ');
            sql = sql.replaceAll(" +", " ");
            sqls.add(sql);
        }
        return sqls;
    }

    public Set<String> getTableName(String sql) throws IllegalQueryTableNameException {
        List<SQLStatement> stmtList = SQLUtils.parseStatements(sql, dbType);
        SQLStatement stmt = stmtList.get(0);
        SchemaStatVisitor statVisitor = SQLUtils.createSchemaStatVisitor(dbType);
        stmt.accept(statVisitor);
        HashSet<String> tableName = new HashSet<>();
        for (TableStat.Name name : statVisitor.getTables().keySet()) {
            tableName.add(aliasVisitor.addDatabaseNamePrefix(name.getName()));
        }
        return tableName;
    }

    public Map<String, String> getTableAlias(String sql) throws TouchstoneException {
        SQLStatement sqlStatement = SQLUtils.parseStatements(sql, dbType).get(0);
        if (!(sqlStatement instanceof SQLSelectStatement statement)) {
            throw new TouchstoneException("Only support select statement");
        }
        statement.accept(aliasVisitor);
        return aliasVisitor.getAliasMap();
    }

    /**
     * @param files SQL文件
     * @return 所有查询中涉及到的表名
     * @throws IOException 从SQL文件中获取Query失败
     */
    public List<String> fetchTableNames(List<File> files) throws IOException, IllegalQueryTableNameException {
        List<String> tableNames = new ArrayList<>();
        for (File sqlFile : files) {
            List<String> queries = getQueriesFromFile(sqlFile.getPath());
            for (String query : queries) {
                tableNames.addAll(getTableName(query));
            }
        }
        tableNames = tableNames.stream().distinct().toList();
        return tableNames;
    }


    private static class ExportTableAliasVisitor extends PGASTVisitorAdapter {
        private final Map<String, String> aliasMap = new HashMap<>();
        private final String defaultDatabaseName;

        public ExportTableAliasVisitor(String defaultDatabaseName) {
            this.defaultDatabaseName = defaultDatabaseName;
        }

        public ExportTableAliasVisitor() {
            this.defaultDatabaseName = null;
        }

        private static String convertTableName2CanonicalTableName(String canonicalTableName,
                                                                  String defaultDatabase) throws IllegalQueryTableNameException {
            List<List<String>> matches = matchPattern(QueryReader.CANONICAL_TBL_NAME, canonicalTableName);
            if (matches.size() == 1 && matches.get(0).get(0).length() == canonicalTableName.length()) {
                return canonicalTableName;
            } else {
                if (defaultDatabase == null) {
                    throw new IllegalQueryTableNameException();
                }
                return String.format("%s.%s", defaultDatabase, canonicalTableName);
            }
        }

        @Override
        public boolean visit(SQLExprTableSource x) {
            if (x.getAlias() != null) {
                try {
                    aliasMap.put(x.getAlias(), addDatabaseNamePrefix(x.getName().toString()));
                } catch (IllegalQueryTableNameException e) {
                    e.printStackTrace();
                }
            }
            return true;
        }

        public Map<String, String> getAliasMap() {
            return aliasMap;
        }

        /**
         * 单个数据库时把表转换为<database>.<table>的形式
         *
         * @param tableName 表名
         * @return 转换后的表名
         */
        public String addDatabaseNamePrefix(String tableName) throws IllegalQueryTableNameException {
            return convertTableName2CanonicalTableName(tableName, defaultDatabaseName);
        }
    }


}
