package ru.runa.wfe.commons.dbmigration.impl;

import java.sql.Types;
import java.util.List;

import ru.runa.wfe.commons.dbmigration.DbMigration;

public class ExpandDescriptionsPatch extends DbMigration {

    @Override
    protected List<String> getDDLQueriesBefore() {
        List<String> sql = super.getDDLQueriesBefore();
        sql.add(getDDLModifyColumn("BPM_TASK", "DESCRIPTION", dialect.getTypeName(Types.VARCHAR, 1024, 1024, 1024)));
        sql.add(getDDLModifyColumn("BPM_PROCESS_DEFINITION", "DESCRIPTION", dialect.getTypeName(Types.VARCHAR, 1024, 1024, 1024)));
        sql.add(getDDLModifyColumn("EXECUTOR_RELATION", "DESCRIPTION", dialect.getTypeName(Types.VARCHAR, 1024, 1024, 1024)));
        sql.add(getDDLModifyColumn("EXECUTOR", "DESCRIPTION", dialect.getTypeName(Types.VARCHAR, 1024, 1024, 1024)));
        return sql;
    }

}
