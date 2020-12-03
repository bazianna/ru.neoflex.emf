package ru.neoflex.emf.sparksql;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.spark.sql.catalyst.plans.logical.LogicalPlan;
import org.apache.spark.sql.catalyst.plans.logical.Project;
import org.apache.spark.sql.catalyst.trees.TreeNode;
import org.apache.spark.sql.execution.SparkSqlParser;
import org.apache.spark.sql.internal.SQLConf;
import org.eclipse.emf.ecore.resource.Resource;
import org.springframework.web.bind.annotation.*;
import ru.neoflex.emf.restserver.DBServerSvc;
import ru.neoflex.emf.restserver.JsonHelper;
import ru.neoflex.emf.sparksql.Node;
import ru.neoflex.emf.sparksql.ProjectNode;
import ru.neoflex.emf.sparksql.QueryLogicalPlan;
import ru.neoflex.emf.sparksql.SparksqlFactory;
import scala.collection.Iterator;

@RestController()
@RequestMapping("/sparksql")
public class SparksqlController {
    final DBServerSvc dbServerSvc;

    public SparksqlController(DBServerSvc dbServerSvc) {
        this.dbServerSvc = dbServerSvc;
    }

    Node createNode(TreeNode treeNode) {
        Node result;
        if (treeNode instanceof Project) {
            result = createProjectNode((Project) treeNode);
        }
        else {
            result = SparksqlFactory.eINSTANCE.createNode();
        }
        result.setNodeName(treeNode.getClass().getSimpleName());
        result.setDescription(treeNode.toString());
        Iterator it = treeNode.children().iterator();
        while (it.hasNext()) {
            TreeNode child = (TreeNode) it.next();
            result.getChildren().add(createNode(child));
        }
        return result;
    }

    private Node createProjectNode(Project treeNode) {
        Node result;
        ProjectNode projectNode = SparksqlFactory.eINSTANCE.createProjectNode();
        Project project = treeNode;
        for(Iterator it = project.projectList().iterator(); it.hasNext();) {
            TreeNode t = (TreeNode) it.next();
            projectNode.getProjectList().add(createNode(t));
        }
        result = projectNode;
        return result;
    }

    @PostMapping(value = "/parse", consumes = {"text/plain"})
    JsonNode parse(@RequestBody String sql) throws Exception {
        SQLConf sqlConf = new SQLConf();
        SparkSqlParser parser = new SparkSqlParser(sqlConf);
        LogicalPlan plan = parser.parsePlan(sql);
        QueryLogicalPlan queryLogicalPlan = SparksqlFactory.eINSTANCE.createQueryLogicalPlan();
        queryLogicalPlan.setSql(sql);
        Node planNode = createNode(plan);
        queryLogicalPlan.setLogicalPlan(planNode);
        return dbServerSvc.getDbServer().inTransaction(false, tx -> {
            Resource resource = tx.createResource();
            resource.getContents().add(queryLogicalPlan);
            resource.save(null);
            return JsonHelper.resourceToJson(resource);
        });
    }

}
