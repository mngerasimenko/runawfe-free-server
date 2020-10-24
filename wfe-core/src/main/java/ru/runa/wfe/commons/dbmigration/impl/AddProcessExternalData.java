package ru.runa.wfe.commons.dbmigration.impl;

import java.util.ArrayList;
import java.util.List;
import ru.runa.wfe.commons.dbmigration.DbMigration;

public class AddProcessExternalData extends DbMigration {

    @Override
    protected List<String> getDDLQueriesBefore() {
        return new ArrayList<String>() {{
            add(getDDLCreateColumn("bpm_process", new BigintColumnDef("external_data", true)));
            add(getDDLCreateIndex("bpm_process", "ix_process_external_data", "external_data"));
        }};
    }
}
