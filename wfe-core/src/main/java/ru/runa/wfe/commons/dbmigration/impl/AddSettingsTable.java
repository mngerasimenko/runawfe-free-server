package ru.runa.wfe.commons.dbmigration.impl;

import java.sql.Types;
import java.util.LinkedList;
import java.util.List;

import ru.runa.wfe.commons.dbmigration.DbMigration;

public class AddSettingsTable extends DbMigration {

    @Override
    protected List<String> getDDLQueriesBefore() {
        List<String> sql = super.getDDLQueriesAfter();
        List<ColumnDef> columns = new LinkedList<DbMigration.ColumnDef>();
        ColumnDef id = new ColumnDef("ID", Types.BIGINT, false);
        id.setPrimaryKey();
        columns.add(id);
        columns.add(new ColumnDef("FILE_NAME", dialect.getTypeName(Types.VARCHAR, 1024, 1024, 1024), false));
        columns.add(new ColumnDef("NAME", dialect.getTypeName(Types.VARCHAR, 1024, 1024, 1024), false));
        columns.add(new ColumnDef("VALUE", dialect.getTypeName(Types.VARCHAR, 1024, 1024, 1024), true));
        sql.add(getDDLCreateTable("BPM_SETTING", columns, null));
        sql.add(getDDLCreateSequence("SEQ_BPM_SETTING"));
        return sql;
    }

}
