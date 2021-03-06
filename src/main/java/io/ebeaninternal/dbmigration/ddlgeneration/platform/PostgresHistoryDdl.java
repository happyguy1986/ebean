package io.ebeaninternal.dbmigration.ddlgeneration.platform;

import io.ebeaninternal.dbmigration.ddlgeneration.DdlBuffer;
import io.ebeaninternal.dbmigration.ddlgeneration.DdlWrite;
import io.ebeaninternal.dbmigration.model.MTable;

import java.io.IOException;
import java.util.List;

/**
 * Uses DB triggers to maintain a history table.
 */
public class PostgresHistoryDdl extends DbTriggerBasedHistoryDdl {


  public PostgresHistoryDdl() {
    this.currentTimestamp = "current_timestamp";
  }

  /**
   * Use Postgres create table like to create the history table.
   */
  @Override
  protected void createHistoryTable(DdlBuffer apply, MTable table) throws IOException {

    String baseTable = table.getName();
    apply
      .append("create table ").append(baseTable).append(historySuffix)
      .append("(like ").append(baseTable).append(")").endOfStatement();
  }

  /**
   * Use Postgres range type rather than start and end timestamps.
   */
  @Override
  protected void addSysPeriodColumns(DdlBuffer apply, String baseTableName, String whenCreatedColumn) throws IOException {
    apply
      .append("alter table ").append(baseTableName)
      .append(" add column ").append(sysPeriod).append(" tstzrange not null default tstzrange(").append(currentTimestamp).append(", null)")
      .endOfStatement();

    if (whenCreatedColumn != null) {
      apply.append("update ").append(baseTableName).append(" set ")
        .append(sysPeriod).append(" = tstzrange(").append(whenCreatedColumn).append(", null)").endOfStatement();
    }
  }

  @Override
  protected void appendSysPeriodColumns(DdlBuffer apply, String prefix) throws IOException {
    appendColumnName(apply, prefix, sysPeriod);
  }

  @Override
  protected void dropSysPeriodColumns(DdlBuffer buffer, String baseTableName) throws IOException {
    buffer.append("alter table ").append(baseTableName).append(" drop column ").append(sysPeriod).endOfStatement();
  }

  @Override
  protected void createTriggers(DdlWrite writer, MTable table) throws IOException {

    String baseTableName = table.getName();
    String procedureName = procedureName(baseTableName);
    String triggerName = triggerName(baseTableName);

    DdlBuffer apply = writer.applyHistory();
    apply
      .append("create trigger ").append(triggerName).newLine()
      .append("  before update or delete on ").append(baseTableName).newLine()
      .append("  for each row execute procedure ").append(procedureName).append("();").newLine().newLine();
  }

  @Override
  protected void dropTriggers(DdlBuffer buffer, String baseTable) throws IOException {
    // rollback trigger then function
    buffer.append("drop trigger if exists ").append(triggerName(baseTable)).append(" on ").append(baseTable).append(" cascade").endOfStatement();
    buffer.append("drop function if exists ").append(procedureName(baseTable)).append("()").endOfStatement();
    buffer.end();
  }

  protected void createOrReplaceFunction(DdlBuffer apply, String procedureName, String historyTable, List<String> includedColumns) throws IOException {
    apply
      .append("create or replace function ").append(procedureName).append("() returns trigger as $$").newLine()
      .append("begin").newLine();
    apply
      .append("  if (TG_OP = 'UPDATE') then").newLine();
    appendInsertIntoHistory(apply, historyTable, includedColumns);
    apply
      .append("    NEW.").append(sysPeriod).append(" = tstzrange(").append(currentTimestamp).append(",null);").newLine()
      .append("    return new;").newLine();
    apply
      .append("  elsif (TG_OP = 'DELETE') then").newLine();
    appendInsertIntoHistory(apply, historyTable, includedColumns);
    apply
      .append("    return old;").newLine();
    apply
      .append("  end if;").newLine()
      .append("end;").newLine()
      .append("$$ LANGUAGE plpgsql;").newLine();

    apply.end();
  }

  @Override
  protected void createStoredFunction(DdlWrite writer, MTable table) throws IOException {

    String procedureName = procedureName(table.getName());
    String historyTable = historyTableName(table.getName());

    List<String> columnNames = columnNamesForApply(table);
    createOrReplaceFunction(writer.applyHistory(), procedureName, historyTable, columnNames);
  }

  @Override
  protected void updateHistoryTriggers(DbTriggerUpdate update) throws IOException {

    String procedureName = procedureName(update.getBaseTable());

    recreateHistoryView(update);
    createOrReplaceFunction(update.historyBuffer(), procedureName, update.getHistoryTable(), update.getColumns());
  }

  /**
   * For postgres we need to drop and recreate the view. Well, we could add columns to the end of the view
   * but otherwise we need to drop and create it.
   */
  private void recreateHistoryView(DbTriggerUpdate update) throws IOException {

    DdlBuffer buffer = update.dropDependencyBuffer();
    // we need to drop the view early/first before any changes to the tables etc
    buffer.append("drop view if exists ").append(update.getBaseTable()).append(viewSuffix).endOfStatement();

    // recreate the view with specific columns specified (the columns generally are not dropped until later)
    createWithHistoryView(update);
  }

  @Override
  protected void appendInsertIntoHistory(DdlBuffer buffer, String historyTable, List<String> columns) throws IOException {

    buffer.append("    insert into ").append(historyTable).append(" (").append(sysPeriod).append(",");
    appendColumnNames(buffer, columns, "");
    buffer.append(") values (tstzrange(lower(OLD.").append(sysPeriod).append("), ").append(currentTimestamp).append("), ");
    appendColumnNames(buffer, columns, "OLD.");
    buffer.append(");").newLine();
  }

}
