package tech.mlsql.plugins.sql.profiler

import org.apache.spark.sql.catalyst.sqlgenerator.{BasicSQLDialect, LogicalPlanSQL}
import tech.mlsql.app.CustomController
import tech.mlsql.dsl.adaptor.{LoadStatement, SelectStatement}
import tech.mlsql.indexer.impl.{LinearTryIndexerSelector, NestedDataIndexer, RestIndexerMeta, TestIndexerMeta}
import tech.mlsql.sqlbooster.meta.ViewCatalyst
import tech.mlsql.tool.MLSQLAnalyzer

/**
 * 2/12/2020 WilliamZhu(allwefantasy@gmail.com)
 */
class IndexerRewriteController extends CustomController {
  override def run(params: Map[String, String]): String = {

    try {
      ViewCatalyst.createViewCatalyst()

      // 解析load语句
      val mlsqlAnalyzer = new MLSQLAnalyzer(params)

      val stats = mlsqlAnalyzer.analyze
      stats.get.foreach { singleSt => {
        singleSt match {
          case a: LoadStatement =>
            val LoadStatement(_, format, path, option, tableName) = a
            ViewCatalyst.meta.register(tableName, path, format)
          case _: SelectStatement =>
          case None => throw new RuntimeException("Only load/select are supported in gen sql interface")

        }
      }
      }

      var temp = ""
      mlsqlAnalyzer.executeAndGetLastTable() match {
        case Some(tableName) =>
          val lp = mlsqlAnalyzer.getSession.table(tableName).queryExecution.analyzed
          val metaClient = if (params.getOrElse("isTest", "false").toBoolean) new TestIndexerMeta() else new RestIndexerMeta()
          val indexer = new LinearTryIndexerSelector(Seq(new NestedDataIndexer(metaClient)), metaClient)
          val newPL = indexer.rewrite(lp, Map())
          temp = new LogicalPlanSQL(newPL, new BasicSQLDialect).toSQL
        case None =>
      }

      temp
    }
    finally {
      ViewCatalyst.unset
    }

  }
}
